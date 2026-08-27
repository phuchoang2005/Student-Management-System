---
name: Performance finding
about: A benchmark result that needs a linked issue before any code change (05-baseline-and-reporting.md §5)
title: "[BM-XXX-NNN] "
labels: performance
---

## Scenario

- **BM-\* ID:**
- **Hazard ID (H1–H8):**
- **Scale / concurrency:**

## Baseline vs. observed

| | Baseline | Observed | Δ |
| --- | --- | --- | --- |
| p95 | | | |
| p99 | | | |
| Error rate | | | |

- **Baseline run record:** `docs/benchmark-strategy/result/<file>.md`
- **Observed run record:** `docs/benchmark-strategy/result/<file>.md`

## Attribution (05-baseline-and-reporting.md §4 escalation ladder)

Which rung explained it, and what it said — don't skip rungs; a profile read before the query log usually re-answers a question the query log already answered.

- **Rung reached:**
  - [ ] 1 — k6 output (latency vs. errors, all requests vs. a tail, degraded across the window vs. started bad)
  - [ ] 2 — Actuator / Prometheus (Hikari pending-wait/active-connections, busy threads, GC pause)
  - [ ] 3 — MySQL slow log + `performance_schema` digests (which statement, how many times per request, rows examined ÷ rows sent)
  - [ ] 4 — JFR / async-profiler (where the CPU/allocations actually are)
- **What that rung showed:**

Before concluding anything, rule out the benchmark itself (05 §4.1): driver saturation, a skipped warm-up, a dataset that wasn't what it claimed, wrong responses passing as fast ones, or a pinned config that moved.

## Hypothesis

What specifically is expected to change, and by roughly how much?

## Severity (01-benchmark-strategy.md §10)

- [ ] Critical — errors under load, or an SLO breach severe enough to make the endpoint unusable at S2
- [ ] Major — an SLO class breached at S2 within the 2× tolerance, or a >50% regression against baseline
- [ ] Minor — a 20–50% regression against baseline, or an S3-only breach
- [ ] Enhancement — a measurement gap (a hazard with no scenario, or a scenario that can't be attributed)

---

**No code change is made on this finding without this issue being linked** (`01-benchmark-strategy.md` §10). A fix is verified by re-running the same scenario, at the same scale and seed, on the same host, and recording the result in `result/`. If the measured improvement doesn't match the hypothesis — in either direction — that gap is itself worth a line: it means the model of why it was slow was wrong, even if the change happened to help.
