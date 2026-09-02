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
	bench-auth-ramp bench-cascade-delete bench-xc-003 bench-scale-sweep bench-mixed-soak \
	bench-full-catalog bench-auth-blast-radius bench-ip-suite

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
	@echo ""
	@echo "docs-v01/Benchmark/07-improvement-roadmap.md IP-01..IP-11 verification (08-hazard-fix-specs.md has the per-item BM-* targets):"
	@echo "make bench-full-catalog [REPS=1] [BASELINE=..] - reseed+run the P0 read catalog at S1/S2/S3 in sequence, bench-report each, then bench-scale-sweep -- IP-01/02/03/04/05/07's shared S1/S2/S3 exit criterion"
	@echo "make bench-auth-blast-radius SCALE=S2|S3 - IP-08's blast-radius check: BM-IDN-001's ramp run concurrently against a BM-STU-005 control (bench-auth-ramp alone only gives the ramp's own knee, not this)"
	@echo "make bench-ip-suite [IP_SUITE_SCALE=S2] - the whole IP-01..IP-11 sequence phase-by-phase (xc-003, full-catalog, writes, jmh, auth-ramp+blast-radius, cascade-delete last since it's destructive); IP-09 is already delivered and IP-10 is deliberately deferred (07-improvement-roadmap.md Phase 5) so neither runs here -- see bench-mixed-soak for H7 data anyway"

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

# --- v0.1 roadmap orchestration (docs-v01/Benchmark/07-improvement-roadmap.md IP-01..IP-11,
# 08-hazard-fix-specs.md's per-item Targets/Verification sections) ------------------------------

IP_SUITE_SCALE ?= S2
BLAST_RADIUS_CONTROL ?= BM_STU_005
FULL_CATALOG_SCALES := S1 S2 S3

# IP-01/IP-02/IP-03/IP-04/IP-05/IP-07 all share the same exit criterion: the P0 read catalog
# (BENCH_SCENARIO_FILES -- BM-STU/BK/CRS/ENR/ME) re-run at S1, S2, and S3, then classified for
# growth. Reseeds each scale fresh rather than assuming out/ already holds usable exports (unlike
# bench-scale-sweep itself, which deliberately reads whatever is already there -- see its own
# caveats). REPS follows bench-report's own REPS convention (median of the last REPS runs); each
# scale gets REPS full bench-all passes before its bench-report is rendered.
bench-full-catalog: bench-node-modules
	@for s in $(FULL_CATALOG_SCALES); do \
		$(MAKE) bench-seed SCALE=$$s; \
		for i in $$(seq 1 $(REPS)); do \
			$(MAKE) bench-all SCALE=$$s VUS=$(VUS) DURATION=$(DURATION) \
				WARMUP_DURATION=$(WARMUP_DURATION) COOLDOWN_DURATION=$(COOLDOWN_DURATION) \
				|| echo ">>> $$s rep $$i: one or more scenarios exited non-zero -- data still written, continuing"; \
		done; \
		$(MAKE) bench-report SCALE=$$s REPS=$(REPS) $(if $(BASELINE),BASELINE=$(BASELINE)); \
	done
	$(MAKE) bench-scale-sweep

# IP-08 (08-hazard-fix-specs.md): BM-IDN-001's ramp must run alone to find its own knee
# (auth-login.js's header comment; bench-auth-ramp above pins that). But IP-08 is judged on blast
# radius, not the knee -- "other endpoints' latency, sampled concurrently, ... should no longer
# degrade together with login latency" -- which needs the opposite: the ramp running WHILE
# something else is measured. This backgrounds a repeating BM-STU-005 (read-single control)
# loop against student-search.js for the duration of the ramp, following the same
# background-then-kill shape bench-mixed-soak uses for monitor-soak.js above, and kills it once
# the ramp completes. Deliberately not one measurement window sized to match the ramp's exact
# wall-clock (auth-login.js's buildOptions(null, ...) call means no shared warm-up, so that's
# computable from DURATION/COOLDOWN_DURATION, but brittle to hardcode here) -- the two exports in
# out/ are meant for a manual before/after p95 comparison, same as this item's Verification section
# describes; there is no automated pass/fail verdict for a concurrent-degradation check like this.
bench-auth-blast-radius: bench-node-modules
	@mkdir -p out
	@echo "IP-08 blast-radius check: BM-IDN-001's ramp running concurrently against a $(BLAST_RADIUS_CONTROL) control."
	@echo "This is NOT BM-IDN-001's own number -- use bench-auth-ramp alone for that (auth-login.js's header comment)."
	@( while true; do \
		k6 run \
			--env BASE_URL=$(BENCH_BASE_URL) \
			--env SCALE=$(SCALE) \
			--env BM_ONLY=$(BLAST_RADIUS_CONTROL) \
			--env DURATION=10s --env WARMUP_DURATION=1s --env COOLDOWN_DURATION=1s \
			--quiet \
			--summary-export=out/auth-blast-radius-control-$(SCALE)-$$(date +%Y%m%dT%H%M%S%N).json \
			scenarios/student-search.js >/dev/null 2>&1; \
	done ) & control_pid=$$!; \
	$(MAKE) bench-auth-ramp SCALE=$(SCALE) DURATION=$(DURATION) \
		WARMUP_DURATION=$(WARMUP_DURATION) COOLDOWN_DURATION=$(COOLDOWN_DURATION); \
	kill $$control_pid 2>/dev/null || true; \
	echo ">>> compare out/auth-blast-radius-control-$(SCALE)-*.json p95s against BM-STU-005's own Read-single SLO (<=50ms) -- degradation during the ramp is IP-08's target metric (08-hazard-fix-specs.md)."

# The eleven-item roadmap end to end, sequenced by 07-improvement-roadmap.md's phase order (not by
# IP-* number -- see that document for why config/tooling comes first, search-shape work second,
# and so on). Runs the S2-scoped items against IP_SUITE_SCALE (default S2, "the scale the SLOs are
# written for" per bench/README.md); bench-full-catalog sweeps S1/S2/S3 itself regardless.
#   IP-01/IP-07 -> bench-xc-003 (pool sweep) + bench-full-catalog (S1/S2/S3 read catalog)
#   IP-02/IP-03/IP-05 -> bench-full-catalog (same S1/S2/S3 data: BM-STU-002/003/004, BM-BK-001, BM-CRS-001)
#   IP-04 -> bench-full-catalog's enrollment-list/me-reads legs + bench-jmh (BM-JMH-004 mapping-cost check)
#   IP-06 -> bench-cascade-delete at N=200 -- destructive, so it runs LAST; the dataset needs
#            restoring afterward before any further bench-* run at IP_SUITE_SCALE (its own target
#            already prints that reminder)
#   IP-08 -> bench-auth-ramp (the ramp's own knee) + bench-auth-blast-radius (the blast-radius check)
#   IP-09 -> already delivered (08-hazard-fix-specs.md) -- nothing to run
#   IP-10 -> deliberately deferred, no target metric yet (07-improvement-roadmap.md Phase 5) --
#            not part of this sequence; run bench-mixed-soak separately if you want H7 data anyway
#   IP-11 -> bench SCENARIO=writes (BM-STU-007's error rate)
bench-ip-suite: bench-node-modules
	$(MAKE) bench-xc-003 SCALE=$(IP_SUITE_SCALE) VUS=$(VUS) DURATION=$(DURATION) \
		WARMUP_DURATION=$(WARMUP_DURATION) COOLDOWN_DURATION=$(COOLDOWN_DURATION)
	$(MAKE) bench-full-catalog REPS=$(REPS) $(if $(BASELINE),BASELINE=$(BASELINE))
	$(MAKE) bench-seed SCALE=$(IP_SUITE_SCALE)
	$(MAKE) bench SCENARIO=writes SCALE=$(IP_SUITE_SCALE) VUS=$(VUS) DURATION=$(DURATION) \
		WARMUP_DURATION=$(WARMUP_DURATION) COOLDOWN_DURATION=$(COOLDOWN_DURATION)
	$(MAKE) bench-jmh JMH_REGEX=$(JMH_REGEX)
	$(MAKE) bench-auth-ramp SCALE=$(IP_SUITE_SCALE) DURATION=$(DURATION) \
		WARMUP_DURATION=$(WARMUP_DURATION) COOLDOWN_DURATION=$(COOLDOWN_DURATION)
	$(MAKE) bench-auth-blast-radius SCALE=$(IP_SUITE_SCALE)
	$(MAKE) bench-cascade-delete SCALE=$(IP_SUITE_SCALE)
	@echo "=== bench-ip-suite done: IP-01..IP-11 except IP-09 (already delivered) and IP-10 (deferred, no target) ==="
	@echo ">>> $(IP_SUITE_SCALE) is now post-cascade-delete -- restore the dataset (04-workload-data-preparation.md §5) before any further bench-* run at this scale."
