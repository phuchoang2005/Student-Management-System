-include .env
export

DC := docker compose
MYSQL_USER ?= management
MYSQL_DATABASE ?= management
CONTAINER := management-mysql

.PHONY: help env up down restart logs ps mysql clean reset docs docs-watch docs-clean \
	bench-node-modules bench-seed bench bench-all bench-report bench-jmh

help:
	@echo "make up       - start the mysql container (creates .env from .env.example if missing)"
	@echo "make down     - stop the mysql container"
	@echo "make restart  - restart the mysql container"
	@echo "make logs     - tail mysql logs"
	@echo "make ps       - show container status"
	@echo "make mysql    - open a mysql shell inside the container"
	@echo "make clean    - stop the container and delete its data volume"
	@echo "make reset    - clean + up (fresh database)"
	@echo "make docs     - compile docs/**/*.md to HTML (util/md-to-html.js)"
	@echo "make docs-watch - same, rebuilding on every save"
	@echo "make docs-clean - delete the generated HTML"
	@echo "make bench-seed SCALE=S1|S2|S3 [SEED=..] - reseed the dataset at that scale, write bench/out/<scale>-manifest.json, reset MySQL diagnostics"
	@echo "make bench SCENARIO=<name> SCALE=S1|S2|S3 - run one k6 scenario (bench/scenarios/<name>.js), raw output to bench/out/"
	@echo "make bench-all SCALE=S1|S2|S3 [BM_ONLY=..] - run every PM-033 scenario file in sequence"
	@echo "make bench-report SCALE=S1|S2|S3 [REPS=3] - render the median of the last REPS runs into the run-record Results table"
	@echo "make bench-jmh - JMH suite (not wired up yet, see PM-037)"

env:
	@test -f .env || cp .env.example .env

up: env
	colima start
	$(DC) up -d
	@echo "MySQL is starting on port $${MYSQL_PORT:-3306}..."

down_temp:
	$(DC) down

down:
	$(DC) down
	colima stop

restart: down up

logs:
	$(DC) logs -f mysql

ps:
	$(DC) ps

mysql: env
	docker exec -it $(CONTAINER) mysql -u$(MYSQL_USER) -p$(MYSQL_PASSWORD) $(MYSQL_DATABASE)

clean:
	$(DC) down -v

reset: clean up

# --- Documentation -----------------------------------------------------------------
# docs/**/*.html is generated, not committed (docs/.gitignore). Sources are the .md
# files and the PlantUML .svg assets they inline.

docs:
	@test -d util/node_modules || (cd util && npm install --silent)
	node util/md-to-html.js

docs-watch:
	@test -d util/node_modules || (cd util && npm install --silent)
	node util/md-to-html.js --watch

docs-clean:
	node util/md-to-html.js --clean

# --- Benchmarking -------------------------------------------------------------------------------
# bench/ is a k6 harness + dataset generator specified in docs/benchmark-strategy/. `make bench`
# deliberately does NOT depend on `make up` (02-benchmark-plan.md §1.3) -- the database and app
# must already be at a known, deliberately-prepared state before a run starts; a target that
# quietly started a container with whatever data was last in it is how two incomparable runs get
# compared.

SCALE ?= S1
ifndef SEED
SEED := $(shell date +%Y%m%d%H%M%S)
endif
BENCH_BASE_URL ?= http://localhost:8080
VUS ?= 20
# Cut down from an earlier 60s/300s/30s (3-repetition) draft that took ~13h to run once across
# S1/S2/S3 -- a protocol nobody runs measures nothing. See 02-benchmark-plan.md §2 for the tradeoff.
DURATION ?= 30s
WARMUP_DURATION ?= 15s
COOLDOWN_DURATION ?= 5s
REPS ?= 1
BM_ONLY ?=
BENCH_SCENARIO_FILES := student-search book-search course-list enrollment-list me-reads

bench-node-modules:
	@test -d bench/node_modules || (cd bench && npm install --silent)

bench-seed: bench-node-modules
	cd bench && npm run seed -- --scale=$(SCALE) --seed=$(SEED) --force
	node bench/seed/manifest.js --scale=$(SCALE)
	@docker exec $(CONTAINER) mysql -uroot -p$(MYSQL_ROOT_PASSWORD) -e \
		"SET GLOBAL slow_query_log='ON'; SET GLOBAL long_query_time=0.1; TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;" \
		|| echo "warning: could not set slow-log/digest diagnostics (needs SUPER/SYSTEM_VARIABLES_ADMIN) -- continuing without them"
	@echo "Seeded $(SCALE) with seed $(SEED) -- record this seed in the run record (05-baseline-and-reporting.md §3)."

bench: bench-node-modules
	@test -n "$(SCENARIO)" || (echo "Usage: make bench SCENARIO=<name> SCALE=S1|S2|S3 [VUS=.. DURATION=.. BM_ONLY=..]"; exit 1)
	@mkdir -p bench/out
	k6 run \
		--env BASE_URL=$(BENCH_BASE_URL) \
		--env SCALE=$(SCALE) \
		--env VUS=$(VUS) \
		--env DURATION=$(DURATION) \
		--env WARMUP_DURATION=$(WARMUP_DURATION) \
		--env COOLDOWN_DURATION=$(COOLDOWN_DURATION) \
		--env BM_ONLY=$(BM_ONLY) \
		--summary-trend-stats="min,med,avg,p(90),p(95),p(99),max" \
		--summary-export=bench/out/$(SCENARIO)-$(SCALE)-$(shell date +%Y%m%dT%H%M%S).json \
		bench/scenarios/$(SCENARIO).js

bench-all:
	@for s in $(BENCH_SCENARIO_FILES); do \
		$(MAKE) bench SCENARIO=$$s SCALE=$(SCALE) VUS=$(VUS) DURATION=$(DURATION) \
			WARMUP_DURATION=$(WARMUP_DURATION) COOLDOWN_DURATION=$(COOLDOWN_DURATION) BM_ONLY=$(BM_ONLY) \
			|| echo ">>> $$s: k6 exited non-zero (SLO threshold breach or error) -- data still written, continuing"; \
	done

bench-report:
	@test -n "$(SCALE)" || (echo "Usage: make bench-report SCALE=S1|S2|S3 [REPS=3]"; exit 1)
	node bench/report.js --scale=$(SCALE) --reps=$(REPS)

bench-jmh:
	@echo "bench-jmh: no JMH suite exists yet -- jmh-core/jmh-generator-annprocess and the"
	@echo "annotationProcessorPaths entry are PM-037 (Sprint 8). Nothing to run."
	@exit 1
