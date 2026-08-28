# Benchmark harness -- k6 scenarios + dataset generator specified in
# docs-v00/Benchmark/benchmark-strategy/. Split out of the former root Makefile so each toolchain
# (management/, bench/, docs-v00/) owns its own targets. Invoke from the repo root as
# `make -C bench <target>`, or `cd bench && make <target>`.
#
# `make bench` deliberately does NOT depend on `management`'s `up` target
# (02-benchmark-plan.md §1.3) -- the database and app must already be at a known,
# deliberately-prepared state before a run starts; a target that quietly started a container with
# whatever data was last in it is how two incomparable runs get compared.
#
# .env stays at the repo root; every path below is relative to this Makefile's directory, one
# level under the repo root.

-include ../.env
export

CONTAINER := management-mysql

.PHONY: help bench-node-modules bench-seed bench bench-all bench-report bench-jmh bench-ci-smoke \
	bench-auth-ramp bench-cascade-delete bench-xc-003 bench-scale-sweep bench-mixed-soak

help:
	@echo "make bench-seed SCALE=S1|S2|S3 [SEED=..] - reseed the dataset at that scale, write out/<scale>-manifest.json, reset MySQL diagnostics"
	@echo "make bench SCENARIO=<name> SCALE=S1|S2|S3 - run one k6 scenario (scenarios/<name>.js), raw output to out/"
	@echo "make bench-all SCALE=S1|S2|S3 [BM_ONLY=..] - run every PM-033 scenario file in sequence"
	@echo "make bench-report SCALE=S1|S2|S3 [REPS=3] [BASELINE=../docs-v00/Benchmark/result/<file>.md] - render the median of the last REPS runs into the run-record Results table, with a Δp95-vs-baseline regression verdict per scenario if BASELINE is given"
	@echo "make bench-jmh [JMH_REGEX=..] - run the JMH microbenchmark suite (BM-JMH-001..004), result to ../management/target/jmh-result.json"
	@echo "make bench-ci-smoke SCALE=S1 - BM-STU-002+BM-ENR-002 with loose thresholds; what .github/workflows/benchmark-smoke.yml runs"
	@echo "make bench-auth-ramp SCALE=S1|S2|S3 - BM-IDN-001's login ramp, isolated (BM_ONLY-pinned) per its 'runs alone' requirement"
	@echo "make bench-cascade-delete SCALE=S2|S3 - BM-XC-001 bulk-delete burst + event_publication drain wait; destructive, restore after"
	@echo "make bench-xc-003 SCALE=S1|S2|S3 - BM-XC-003 pool-saturation sweep (BM-ENR-002 at VUS=5,10,20,40)"
	@echo "make bench-scale-sweep - BM-XC-004: classify the 6 P0 scenarios' S1->S2->S3 growth from existing out/ exports"
	@echo "make bench-mixed-soak SCALE=S2 [SOAK_DURATION=30m] - BM-XC-002 mixed-role soak + BM-IDN-004, with monitor-soak.js backgrounded"
	@echo "(Prometheus/Grafana/mysqld-exporter are behind the 'benchmark' docker-compose profile -- 'make -C management up-bench' starts them all alongside mysql; see bench/observability/README.md)"

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
BASELINE ?=
JMH_REGEX ?=
# k6 -> Prometheus, additive to --summary-export (docs-v00/Benchmark/benchmark-strategy/
# 06-dashboard-building.md, HTTP & Load Testing dashboard). PROMETHEUS_RW=0 opts out for anyone
# without Prometheus running -- see bench/observability/README.md for the receiver-side setup.
PROMETHEUS_RW ?= 1
K6_OUT := $(if $(filter 1,$(PROMETHEUS_RW)),--out experimental-prometheus-rw,)
export K6_PROMETHEUS_RW_SERVER_URL ?= http://localhost:9090/api/v1/write
# Default trend-stat is p99 only; the HTTP & Load Testing dashboard wants p50/p95/p99 together
# (06-dashboard-building.md: "clearly exposes tail latency as concurrency increases").
export K6_PROMETHEUS_RW_TREND_STATS ?= p(50),p(95),p(99)
# Read-only PM-033 files only, deliberately -- PM-035/036's writes.js, enrollment-batch.js,
# auth-login.js, cascade-delete.js and mixed-soak.js all mutate or destroy data, or (auth-login's
# ramp) must run in isolation, so a blind "run everything" loop is wrong for any of them. Each has
# its own target below, or is run directly via `make bench SCENARIO=<name>`.
BENCH_SCENARIO_FILES := student-search book-search course-list enrollment-list me-reads

bench-node-modules:
	@test -d node_modules || npm install --silent

bench-seed: bench-node-modules
	npm run seed -- --scale=$(SCALE) --seed=$(SEED) --force
	node seed/manifest.js --scale=$(SCALE)
	@docker exec $(CONTAINER) mysql -uroot -p$(MYSQL_ROOT_PASSWORD) -e \
		"SET GLOBAL slow_query_log='ON'; SET GLOBAL long_query_time=0.1; TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;" \
		|| echo "warning: could not set slow-log/digest diagnostics (needs SUPER/SYSTEM_VARIABLES_ADMIN) -- continuing without them"
	@echo "Seeded $(SCALE) with seed $(SEED) -- record this seed in the run record (05-baseline-and-reporting.md §3)."

bench: bench-node-modules
	@test -n "$(SCENARIO)" || (echo "Usage: make bench SCENARIO=<name> SCALE=S1|S2|S3 [VUS=.. DURATION=.. BM_ONLY=..]"; exit 1)
	@mkdir -p out
	k6 run \
		--env BASE_URL=$(BENCH_BASE_URL) \
		--env SCALE=$(SCALE) \
		--env VUS=$(VUS) \
		--env DURATION=$(DURATION) \
		--env WARMUP_DURATION=$(WARMUP_DURATION) \
		--env COOLDOWN_DURATION=$(COOLDOWN_DURATION) \
		--env BM_ONLY=$(BM_ONLY) \
		$(K6_OUT) \
		--summary-trend-stats="min,med,avg,p(90),p(95),p(99),max" \
		--summary-export=out/$(SCENARIO)-$(SCALE)-$(shell date +%Y%m%dT%H%M%S).json \
		scenarios/$(SCENARIO).js

bench-all:
	@for s in $(BENCH_SCENARIO_FILES); do \
		$(MAKE) bench SCENARIO=$$s SCALE=$(SCALE) VUS=$(VUS) DURATION=$(DURATION) \
			WARMUP_DURATION=$(WARMUP_DURATION) COOLDOWN_DURATION=$(COOLDOWN_DURATION) BM_ONLY=$(BM_ONLY) \
			|| echo ">>> $$s: k6 exited non-zero (SLO threshold breach or error) -- data still written, continuing"; \
	done

bench-report:
	@test -n "$(SCALE)" || (echo "Usage: make bench-report SCALE=S1|S2|S3 [REPS=3] [BASELINE=../docs-v00/Benchmark/result/<file>.md]"; exit 1)
	node report.js --scale=$(SCALE) --reps=$(REPS) $(if $(BASELINE),--baseline=$(BASELINE))

# PM-038: exactly what .github/workflows/benchmark-smoke.yml runs, for local dry-run parity.
# ci-smoke.js carries its own hardcoded 15s-warmup/30s-steady durations and loose thresholds, so
# unlike `bench`, VUS/DURATION/BM_ONLY are irrelevant here -- only SCALE and BASE_URL matter.
bench-ci-smoke: bench-node-modules
	@mkdir -p out
	k6 run \
		--env BASE_URL=$(BENCH_BASE_URL) \
		--env SCALE=$(SCALE) \
		$(K6_OUT) \
		--summary-export=out/ci-smoke-$(SCALE)-$(shell date +%Y%m%dT%H%M%S).json \
		scenarios/ci-smoke.js

# PM-037: the four BM-JMH-* classes under management/src/test/java/.../benchmark/ (plus
# AesPasswordCipherBenchmark, which has to live in identity/internal -- see that class's own
# comment) run via JMH's own CLI entry point rather than a jmh-maven-plugin/exec-maven-plugin
# addition. dependency:build-classpath resolves via Maven's default plugin-prefix mapping, so no
# new plugin declaration is needed. JMH_REGEX filters by class/method name (e.g.
# JMH_REGEX=BcryptBenchmark); empty runs every discovered @Benchmark. A JMH result may never
# justify a code change on its own (01-benchmark-strategy.md §5.1) -- it can only support a change
# whose effect is also visible in a k6 BM-* scenario.
bench-jmh:
	cd ../management && ./mvnw --batch-mode --no-transfer-progress test-compile
	cd ../management && ./mvnw --batch-mode --no-transfer-progress dependency:build-classpath \
		-Dmdep.outputFile=target/jmh-classpath.txt -DincludeScope=test
	cd ../management && java -cp "target/classes:target/test-classes:$$(cat target/jmh-classpath.txt)" \
		org.openjdk.jmh.Main -rf json -rff target/jmh-result.json $(JMH_REGEX)
	@echo "Result: management/target/jmh-result.json."

# --- Sprint 8 (PM-035/036) targets --------------------------------------------------------------

# BM-IDN-001's ramp must run alone -- nothing else in this k6 invocation, nothing else on the host
# (03-benchmark-scenarios.md §6) -- so this pins BM_ONLY to just its 5 stages rather than trusting
# that convention to be remembered. `make bench SCENARIO=auth-login` still runs the whole file
# (ramp + BM-IDN-002 + BM-IDN-003) for a quick dev smoke test, just not for a number to trust.
bench-auth-ramp: bench-node-modules
	@mkdir -p out
	@echo "Running BM-IDN-001 alone -- make sure nothing else is hitting this host right now."
	k6 run \
		--env BASE_URL=$(BENCH_BASE_URL) \
		--env SCALE=$(SCALE) \
		--env DURATION=$(DURATION) \
		--env WARMUP_DURATION=$(WARMUP_DURATION) \
		--env COOLDOWN_DURATION=$(COOLDOWN_DURATION) \
		--env BM_ONLY=BM_IDN_001_VU01,BM_IDN_001_VU10,BM_IDN_001_VU25,BM_IDN_001_VU50,BM_IDN_001_VU100 \
		$(K6_OUT) \
		--summary-trend-stats="min,med,avg,p(90),p(95),p(99),max" \
		--summary-export=out/auth-login-$(SCALE)-$(shell date +%Y%m%dT%H%M%S).json \
		scenarios/auth-login.js

# BM-XC-001: destructive burst delete + wait for the event_publication cascade to drain
# (seed/cascade-drain.js -- k6 itself cannot query MySQL). Restore the dataset afterward
# (04-workload-data-preparation.md §5) before any other bench-* run at this scale.
bench-cascade-delete: bench-node-modules
	@mkdir -p out
	@since=$$(date -u +%Y-%m-%dT%H:%M:%S.000Z); \
	k6 run \
		--env BASE_URL=$(BENCH_BASE_URL) \
		--env SCALE=$(SCALE) \
		$(K6_OUT) \
		--summary-trend-stats="min,med,avg,p(90),p(95),p(99),max" \
		--summary-export=out/cascade-delete-$(SCALE)-$$(date +%Y%m%dT%H%M%S).json \
		scenarios/cascade-delete.js; \
	node seed/cascade-drain.js --since=$$since --scale=$(SCALE) --timeout=120; \
	echo ">>> cascade-delete is destructive -- restore the dataset (04-workload-data-preparation.md §5) before any other bench-* run at $(SCALE)."

# BM-XC-003: BM-ENR-002 (enrollment-list.js) at VU counts spanning the default, untuned Hikari
# pool size of 10 -- 5, 10, 20, 40. Distinct export filenames per VUS so the 4 runs don't conflate
# under report.js's plain <file>-<scale>-<timestamp> convention (see xc003-report.js).
bench-xc-003: bench-node-modules
	@mkdir -p out
	@for v in 5 10 20 40; do \
		k6 run \
			--env BASE_URL=$(BENCH_BASE_URL) \
			--env SCALE=$(SCALE) \
			--env VUS=$$v \
			--env DURATION=$(DURATION) \
			--env WARMUP_DURATION=$(WARMUP_DURATION) \
			--env COOLDOWN_DURATION=$(COOLDOWN_DURATION) \
			--env BM_ONLY=BM_ENR_002 \
			$(K6_OUT) \
			--summary-trend-stats="min,med,avg,p(90),p(95),p(99),max" \
			--summary-export=out/enrollment-list-$(SCALE)-vu$$v-$$(date +%Y%m%dT%H%M%S).json \
			scenarios/enrollment-list.js \
			|| echo ">>> VUS=$$v: k6 exited non-zero (threshold breach or error) -- data still written, continuing"; \
	done
	node xc003-report.js --scale=$(SCALE)

# BM-XC-004: classifies the 6 P0 scenarios' S1->S2->S3 growth from out/ exports already on disk
# (PM-034's baseline runs) -- no k6 execution, see scale-sweep.js's own caveats.
bench-scale-sweep: bench-node-modules
	node scale-sweep.js

# BM-XC-002 + BM-IDN-004: 30-minute mixed-role soak, with monitor-soak.js backgrounded around it
# to sample heap-bytes-per-session (H7) -- the actual BM-XC-002 deliverable, which no k6
# --summary-export captures on its own.
bench-mixed-soak: bench-node-modules
	@mkdir -p out
	@node monitor-soak.js --scale=$(SCALE) --base-url=$(BENCH_BASE_URL) --interval=30 & \
	monitor_pid=$$!; \
	k6 run \
		--env BASE_URL=$(BENCH_BASE_URL) \
		--env SCALE=$(SCALE) \
		--env SOAK_DURATION=$(SOAK_DURATION) \
		$(K6_OUT) \
		--summary-trend-stats="min,med,avg,p(90),p(95),p(99),max" \
		--summary-export=out/mixed-soak-$(SCALE)-$$(date +%Y%m%dT%H%M%S).json \
		scenarios/mixed-soak.js; \
	kill -INT $$monitor_pid 2>/dev/null || true; \
	sleep 1
