-include .env
export

DC := docker compose
MYSQL_USER ?= management
MYSQL_DATABASE ?= management
CONTAINER := management-mysql

.PHONY: help env up down restart logs ps mysql clean reset docs docs-watch docs-clean \
	bench-node-modules bench-seed bench bench-all bench-report bench-jmh \
	bench-auth-ramp bench-cascade-delete bench-xc-003 bench-scale-sweep bench-mixed-soak

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
	@echo "make bench-auth-ramp SCALE=S1|S2|S3 - BM-IDN-001's login ramp, isolated (BM_ONLY-pinned) per its 'runs alone' requirement"
	@echo "make bench-cascade-delete SCALE=S2|S3 - BM-XC-001 bulk-delete burst + event_publication drain wait; destructive, restore after"
	@echo "make bench-xc-003 SCALE=S1|S2|S3 - BM-XC-003 pool-saturation sweep (BM-ENR-002 at VUS=5,10,20,40)"
	@echo "make bench-scale-sweep - BM-XC-004: classify the 6 P0 scenarios' S1->S2->S3 growth from existing bench/out/ exports"
	@echo "make bench-mixed-soak SCALE=S2 [SOAK_DURATION=30m] - BM-XC-002 mixed-role soak + BM-IDN-004, with monitor-soak.js backgrounded"

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
SOAK_DURATION ?= 30m
# Read-only PM-033 files only, deliberately -- PM-035/036's writes.js, enrollment-batch.js,
# auth-login.js, cascade-delete.js and mixed-soak.js all mutate or destroy data, or (auth-login's
# ramp) must run in isolation, so a blind "run everything" loop is wrong for any of them. Each has
# its own target below, or is run directly via `make bench SCENARIO=<name>`.
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

# --- Sprint 8 (PM-035/036) targets --------------------------------------------------------------

# BM-IDN-001's ramp must run alone -- nothing else in this k6 invocation, nothing else on the host
# (03-benchmark-scenarios.md §6) -- so this pins BM_ONLY to just its 5 stages rather than trusting
# that convention to be remembered. `make bench SCENARIO=auth-login` still runs the whole file
# (ramp + BM-IDN-002 + BM-IDN-003) for a quick dev smoke test, just not for a number to trust.
bench-auth-ramp: bench-node-modules
	@mkdir -p bench/out
	@echo "Running BM-IDN-001 alone -- make sure nothing else is hitting this host right now."
	k6 run \
		--env BASE_URL=$(BENCH_BASE_URL) \
		--env SCALE=$(SCALE) \
		--env DURATION=$(DURATION) \
		--env WARMUP_DURATION=$(WARMUP_DURATION) \
		--env COOLDOWN_DURATION=$(COOLDOWN_DURATION) \
		--env BM_ONLY=BM_IDN_001_VU01,BM_IDN_001_VU10,BM_IDN_001_VU25,BM_IDN_001_VU50,BM_IDN_001_VU100 \
		--summary-trend-stats="min,med,avg,p(90),p(95),p(99),max" \
		--summary-export=bench/out/auth-login-$(SCALE)-$(shell date +%Y%m%dT%H%M%S).json \
		bench/scenarios/auth-login.js

# BM-XC-001: destructive burst delete + wait for the event_publication cascade to drain
# (bench/seed/cascade-drain.js -- k6 itself cannot query MySQL). Restore the dataset afterward
# (04-workload-data-preparation.md §5) before any other bench-* run at this scale.
bench-cascade-delete: bench-node-modules
	@mkdir -p bench/out
	@since=$$(date -u +%Y-%m-%dT%H:%M:%S.000Z); \
	k6 run \
		--env BASE_URL=$(BENCH_BASE_URL) \
		--env SCALE=$(SCALE) \
		--summary-trend-stats="min,med,avg,p(90),p(95),p(99),max" \
		--summary-export=bench/out/cascade-delete-$(SCALE)-$$(date +%Y%m%dT%H%M%S).json \
		bench/scenarios/cascade-delete.js; \
	node bench/seed/cascade-drain.js --since=$$since --scale=$(SCALE) --timeout=120; \
	echo ">>> cascade-delete is destructive -- restore the dataset (04-workload-data-preparation.md §5) before any other bench-* run at $(SCALE)."

# BM-XC-003: BM-ENR-002 (enrollment-list.js) at VU counts spanning the default, untuned Hikari
# pool size of 10 -- 5, 10, 20, 40. Distinct export filenames per VUS so the 4 runs don't conflate
# under report.js's plain <file>-<scale>-<timestamp> convention (see bench/xc003-report.js).
bench-xc-003: bench-node-modules
	@mkdir -p bench/out
	@for v in 5 10 20 40; do \
		k6 run \
			--env BASE_URL=$(BENCH_BASE_URL) \
			--env SCALE=$(SCALE) \
			--env VUS=$$v \
			--env DURATION=$(DURATION) \
			--env WARMUP_DURATION=$(WARMUP_DURATION) \
			--env COOLDOWN_DURATION=$(COOLDOWN_DURATION) \
			--env BM_ONLY=BM_ENR_002 \
			--summary-trend-stats="min,med,avg,p(90),p(95),p(99),max" \
			--summary-export=bench/out/enrollment-list-$(SCALE)-vu$$v-$$(date +%Y%m%dT%H%M%S).json \
			bench/scenarios/enrollment-list.js \
			|| echo ">>> VUS=$$v: k6 exited non-zero (threshold breach or error) -- data still written, continuing"; \
	done
	node bench/xc003-report.js --scale=$(SCALE)

# BM-XC-004: classifies the 6 P0 scenarios' S1->S2->S3 growth from bench/out/ exports already on
# disk (PM-034's baseline runs) -- no k6 execution, see bench/scale-sweep.js's own caveats.
bench-scale-sweep: bench-node-modules
	node bench/scale-sweep.js

# BM-XC-002 + BM-IDN-004: 30-minute mixed-role soak, with bench/monitor-soak.js backgrounded
# around it to sample heap-bytes-per-session (H7) -- the actual BM-XC-002 deliverable, which no k6
# --summary-export captures on its own.
bench-mixed-soak: bench-node-modules
	@mkdir -p bench/out
	@node bench/monitor-soak.js --scale=$(SCALE) --base-url=$(BENCH_BASE_URL) --interval=30 & \
	monitor_pid=$$!; \
	k6 run \
		--env BASE_URL=$(BENCH_BASE_URL) \
		--env SCALE=$(SCALE) \
		--env SOAK_DURATION=$(SOAK_DURATION) \
		--summary-trend-stats="min,med,avg,p(90),p(95),p(99),max" \
		--summary-export=bench/out/mixed-soak-$(SCALE)-$$(date +%Y%m%dT%H%M%S).json \
		bench/scenarios/mixed-soak.js; \
	kill $$monitor_pid 2>/dev/null || true
