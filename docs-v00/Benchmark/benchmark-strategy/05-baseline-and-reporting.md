# Baseline & Reporting

Benchmark Documentation — Part 5 of 5 ([Benchmark Strategy](./01-benchmark-strategy.md) → [Benchmark Plan](./02-benchmark-plan.md) → [Scenarios](./03-benchmark-scenarios.md) → [Workload Data Preparation](./04-workload-data-preparation.md) → Baseline & Reporting).

What happens to a run's numbers after the run: how a baseline is established, how later runs are judged against it, the exact record written to [`result/`](./result/), and how to diagnose a run that came back red.

This is the document that makes the other four worth having. A benchmark that is not recorded in a comparable form is a number someone remembers imprecisely.

---

## 1. What a Baseline Is

**A baseline is the first accepted run at a given dataset scale**, recorded in [`result/`](./result/), against which every later run at that scale is compared.

There is one baseline per scale, not one for the project — S1, S2, and S3 results are not comparable to each other in absolute terms and are never used to judge each other. (The S1→S2→S3 *curve* is a separate deliverable, `BM-XC-004`, and it compares shapes rather than values.)

A run becomes the baseline when it is **accepted**, which requires all of:

1. The run protocol in `02-benchmark-plan.md` §2 was followed — warm-up discarded, steady-state window measured at the documented duration.
2. The host was quiet and the k6 driver was not itself saturated (`01-benchmark-strategy.md` §7.2). This matters more than it did under the old three-repetition protocol, since there is no median left to absorb one noisy repetition — see `02-benchmark-plan.md` §2.2.
3. Error rate was below 0.1% in every scenario. **A run with errors is not a slow run, it is a broken run**, and its latencies describe a system that was failing.
4. The complete configuration in §3 was recorded.

(The original four-condition list had a fourth entry — "the three repetitions agreed to within ~20% at p95" — which no longer applies now that `02-benchmark-plan.md` §2.2 runs a single repetition per scenario. There is deliberately no automated replacement for it; a number that looks implausible against its neighbors is a re-run-it-by-hand signal, not a rejected baseline.)

### 1.1 When a baseline is replaced

Replace it — do not accumulate — when:

- **The pinned configuration changed** (JVM version or heap flags, MySQL version or `innodb_buffer_pool_size`, host, container CPU limits). Any of these invalidates comparison, so the old baseline can no longer be used and keeping it invites an incorrect one.
- **The dataset seed or scale definition changed** (`04-workload-data-preparation.md` §3). Same reasoning.
- **A performance fix landed and was verified.** The improved run becomes the new floor; otherwise the next regression is measured against a number the code has already beaten.

Never replace a baseline because a run came back worse. That is the case the baseline exists for.

Superseded baselines stay in `result/` as records. They are not deleted — the history of how the system's performance moved is exactly what a record set is for.

---

## 2. Regression Policy

Thresholds are **deltas against the baseline at the same scale and the same concurrency**, never absolute latencies. `01-benchmark-strategy.md` §7.1 explains why: absolute numbers from a shared laptop host are not portable, but the delta between two runs on that host is, because the noise applies to both sides.

| Δ p95 vs. baseline | Verdict | Action |
| --- | --- | --- |
| Better than −10% | **Improvement** | Record it. If it was intentional, this run becomes the new baseline (§1.1). If it was *not* intentional, investigate — an unexplained speedup is as much a signal as a slowdown, and is often a scenario that stopped doing what it thought it was doing. |
| −10% to +20% | **No change** | Noise. Take no action; do not tune toward it. |
| +20% to +50% | **Investigate** | Minor-severity issue (`01-benchmark-strategy.md` §10), citing the `BM-*` ID. Re-run first — a single repetition triple can still be unlucky. |
| Above +50% | **Block** | Major-severity issue. Do not merge the change that caused it without either a fix or a written, accepted justification. |
| Error rate ≥ 0.1% | **Block, regardless of latency** | Critical-severity. This is a correctness finding that a performance run happened to surface. |

Three qualifications, each of which prevents a specific way of misusing the table:

- **p99 moves are read alongside p95, not instead of it.** p99 on the 30 s steady-state window (`02-benchmark-plan.md` §2.1) is a small number of samples and is legitimately noisier; a p99 regression with a flat p95 is usually GC or a scheduling artifact, not a code change. A p99 regression *with* a p95 regression is the same finding, seen twice.
- **The bands are per scenario, not per run.** One scenario at +60% is a block even if the run's other twenty are flat — an average across scenarios is exactly the kind of aggregation that hides the finding.
- **CI smoke output is advisory and never produces a verdict from this table.** `02-benchmark-plan.md` §5 sets its thresholds an order of magnitude loose precisely because GitHub-hosted runners cannot support these bands.

### 2.1 SLO verdicts are separate from regression verdicts

Two independent judgments, and conflating them causes a specific mistake:

- **Regression** — "did this change make it worse?" Compared against the baseline.
- **SLO** — "is it fast enough?" Compared against the class targets in `01-benchmark-strategy.md` §4.2, at S2.

A scenario can be **stable and still too slow** (no regression, breaches SLO — that is a standing hazard, not a new defect) or **fast and newly worse** (meets SLO, regressed 40% — that is a real finding, and waiting for it to breach the SLO before acting means acting after it is much harder to bisect). The run record carries both verdicts, separately, for every scenario.

---

## 3. The Run Record

One file per run, in [`result/`](./result/). Filename: **`YYYY-MM-DD-<scale>-<short-sha>.md`** — for example `2026-09-03-S2-8e099d4.md`. A second run on the same day at the same scale and SHA gets a `-2` suffix.

Records are **append-only**: once accepted, a run file is not edited. If it was wrong, write a new one and note the supersession in both. The value of a record set is that it was not tidied afterwards.

The template:

````markdown
# Benchmark Run — S2 — 8e099d4

| | |
| --- | --- |
| Date | 2026-09-03 |
| Git SHA | 8e099d4 (branch: main, clean working tree) |
| Dataset scale | S2 |
| Dataset seed | 20260903 |
| Dataset source | dump `bench/out/s2-20260903.sql.gz` |
| Host | Apple M-series, 10 cores, 32 GB, macOS 24.6.0 |
| Host CPU during run | peak 74%, k6 process peak 11% |
| JVM | Temurin 21.0.x, `-Xms2g -Xmx2g` |
| MySQL | `mysql:8.4` via Colima, 4 CPU / 8 GB, `innodb_buffer_pool_size=4G` |
| Spring profile | `benchmark` (actuator on, demo-accounts disabled) |
| k6 | v0.5x.x |
| Protocol | ~15 s warm-up discarded, 30 s steady state, 1 repetition (`02-benchmark-plan.md` §2) |
| Baseline compared against | `2026-08-20-S2-5d360e6.md` |
| Verdict | **2 regressions, 1 SLO breach** |

## Results

| BM ID | VUs | p50 | p95 | p99 | req/s | err % | SLO class | SLO | Δ p95 vs. baseline |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BM-STU-001 | 20 | 18 ms | 41 ms | 78 ms | 412 | 0.00 | Read-list | PASS | +3% |
| BM-STU-002 | 20 | 96 ms | 210 ms | 340 ms | 141 | 0.00 | Read-list | **BREACH** | +6% |
| BM-ENR-002 | 20 | 240 ms | 520 ms | 810 ms | 62 | 0.00 | Read-list | **BREACH** | **+61%** |
| … | | | | | | | | | |

## Saturation

| Signal | Observed | Note |
| --- | --- | --- |
| Hikari active / pool size | 10 / 10 sustained during BM-ENR-002 | pool exhausted — see findings |
| Hikari pending-connection wait p95 | 180 ms | |
| Tomcat busy threads (peak) | 34 / 200 | not the constraint |
| GC pause total / max | 1.2 s over 300 s / 41 ms | not the constraint |

## Findings

- **F1 — BM-ENR-002, +61% vs. baseline, H2.** `performance_schema` shows 101 statements per request at `size=100`, confirming the per-row `CourseLookup.summaryOf` (`EnrollmentService.java:179`). Hikari sat at 10/10 for the duration: each request holds a connection across all 101 queries, so H2 is the *concurrency* constraint here, not only a latency one. Issue #NN.
- **F2 — BM-STU-002 SLO breach, no regression, H1.** Stable against baseline; simply above the Read-list target at S2. Standing hazard, not a new defect. `EXPLAIN ANALYZE` confirms a full scan, twice per request (rows + count).

## Actions taken

| # | Action | Issue |
| --- | --- | --- |
| F1 | Filed; not fixed in this run | #NN |
| F2 | Filed as standing hazard; no change proposed yet | #NN |
````

Two things about the template that are load-bearing:

- **Raw percentiles are stored, and verdicts are derived from them.** The SLOs are proposals that will be revised (`01-benchmark-strategy.md` §4.2). Storing only "PASS/BREACH" would mean a revision invalidates every historical record; storing the numbers means a revision can re-judge old runs without re-running them.
- **The header table is not ceremony.** Every row in it is something that, if changed and unrecorded, makes a later comparison silently wrong.

---

## 4. Diagnosing a Red Run

An escalation ladder, cheapest rung first. **Do not skip rungs** — the expensive tools answer questions the cheap ones have usually already answered, and a profile read before the query log is how an afternoon disappears into a flame graph that was pointing at JDBC all along.

| # | Rung | Answers | Escalate when |
| --- | --- | --- | --- |
| **1** | **k6 output** | Is it latency or errors? All requests or a tail? Did it degrade across the window (a leak or a queue) or start bad (a shape problem)? | The scenario is genuinely slower, uniformly, and errors are not the story. |
| **2** | **Actuator / Prometheus** (requires H8 closed) | *Where* is the time. Hikari pending-wait and active-connections say "pool exhausted"; busy threads say "request-bound"; GC pause says "memory". Frequently ends the investigation. | No saturation signal is elevated — the time is inside the request, not waiting for a resource. |
| **3** | **MySQL slow log + `performance_schema` digests** | Which statement, how many times per request, and how many rows examined per row returned. **This is where H1, H2, and H3 are confirmed or ruled out**, and it is the rung most findings end on. `EXPLAIN ANALYZE` the offending digest to see the access path. | The statements are few, indexed, and fast — yet the request is slow. |
| **4** | **JFR / async-profiler** | Where the CPU actually is, and what is allocating. The only rung that can explain time spent in application code rather than in I/O or waiting. | — |

Two shortcuts worth knowing:

- **Rows examined ÷ rows sent is the single most diagnostic number in rung 3.** Near 1 means the index is doing its job. In the thousands means a scan — H1, directly.
- **Statements per request is how H2 is proven rather than argued.** Divide the digest's execution count by the run's request count. If a `size=100` page shows ~101, the N+1 is confirmed and no further tooling is needed.

### 4.1 Before concluding anything, rule out the benchmark

More first-time red runs are the harness than the system. Check, in this order:

1. **Was the driver saturated?** If k6's own CPU share was high, the run measures the driver (`01-benchmark-strategy.md` §7.2) — discard it.
2. **Was the warm-up discarded?** A run that includes JIT warm-up and a cold buffer pool is slow in a way that looks exactly like a regression, and disproportionately so for short runs.
3. **Was the dataset the one claimed?** Verify row counts against `04-workload-data-preparation.md` §1 and the seed against the baseline's. A write scenario from an earlier step may have changed it (`02-benchmark-plan.md` §2.3).
4. **Are the responses correct?** A scenario returning 403 at full speed, or an empty page, looks fast and means nothing. Assert on response shape, not just status.
5. **Did the pinned configuration change?** Compare the header table against the baseline's, row by row. A different `innodb_buffer_pool_size` is a different benchmark.

---

## 5. From Finding to Change

**No code change is made on a benchmark finding without a linked issue** (`01-benchmark-strategy.md` §10). Performance changes are the easiest kind to make speculatively and the hardest to justify afterwards; the issue is what forces the before-number, the hypothesis, and the after-number to be written down in one place.

Every performance issue carries:

- the **`BM-*` scenario ID** and the **hazard ID** (H1–H8);
- the **baseline number and the observed number**, with scale and concurrency;
- the **attribution** from §4 — which rung explained it, and what it said;
- the **hypothesis**: what specifically is expected to change, and by roughly how much.

A fix is verified by re-running **the same scenario, at the same scale and seed, on the same host**, and comparing against the run that produced the finding. The verification run is recorded in `result/` like any other. If the measured improvement does not match the hypothesis — in either direction — that gap is itself worth a line in the findings section: it means the model of why it was slow was wrong, even if the change happened to help.

### 5.1 Findings that are not defects

Some results are worth recording and acting on in documentation rather than code:

- **A deliberate cost, now quantified.** H4's batch endpoint is the clearest case: 50 transactions per request is the design (`api-specification.md` §5 decision #12), and the number belongs in the API documentation as guidance on where a client should split a request — not in an issue proposing to change it.
- **A confirmed non-hazard.** `BM-BK-003` confirming the per-page owner memo, or `BM-JMH-003` confirming value-object validation is free, are successful benchmarks with null results. Record them. Their value is that they stop someone optimizing the wrong thing later, and that only works if the null result is written down.
- **A security observation.** `BM-IDN-002` compares failed-login cost against successful-login cost; a measurable difference is a user-enumeration signal. That is a security issue found by a performance scenario, and it goes to the security channel, not this one.

---

## 6. Out of Scope (this document)

- Why the hazards matter and what the SLOs are — see [01-benchmark-strategy.md](./01-benchmark-strategy.md).
- The run protocol whose output this document records — see [02-benchmark-plan.md](./02-benchmark-plan.md) §2.
- The scenarios being recorded — see [03-benchmark-scenarios.md](./03-benchmark-scenarios.md).
- The datasets and seeds the header table cites — see [04-workload-data-preparation.md](./04-workload-data-preparation.md).
- Actual run records — see [`result/`](./result/).
