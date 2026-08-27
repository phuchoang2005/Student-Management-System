# Benchmark Results

Recorded benchmark runs. One file per run, named **`YYYY-MM-DD-<scale>-<short-sha>.md`** — for example `2026-09-03-S2-8e099d4.md`. A second run on the same day at the same scale and commit takes a `-2` suffix.

The template for a run file, and the meaning of every column in it, is fixed by [`../05-baseline-and-reporting.md`](../05-baseline-and-reporting.md) §3.

---

## These files are records, not specification

Everything else under `docs/` describes what the system should be, and is edited as that understanding improves. **These files describe what happened on one day, on one machine, and are not edited after acceptance.**

- **Append, never revise.** If a run file is wrong, write a new one and cross-reference the supersession in both. A tidied record set cannot be trusted, which defeats the only purpose it has.
- **Store raw percentiles, derive verdicts.** The SLOs in [`../01-benchmark-strategy.md`](../01-benchmark-strategy.md) §4.2 are proposals and will be revised. Keeping the underlying p50/p95/p99 means a revision can re-judge old runs without re-running them.
- **Superseded baselines stay.** When a baseline is replaced ([`../05-baseline-and-reporting.md`](../05-baseline-and-reporting.md) §1.1), its file remains here. How performance moved over time is exactly what this folder is for.
- **Record the failures too.** A run discarded because the driver saturated, or because the dataset turned out not to be what it claimed, is worth a short file saying so. The next person to hit it will search here first.

Large artifacts — raw k6 output, JFR recordings, database dumps — do **not** belong here. Keep them in an ignored path (`bench/out/` or equivalent) and name them from the run file.

---

## Run Index

Newest first. Verdict is one of **baseline** (accepted as the reference for its scale), **pass**, **regression**, or **discarded**.

| Date | Scale | Git SHA | Verdict | File |
| --- | --- | --- | --- | --- |
| 2026-08-27 | JMH | d891911 | baseline (no server/DB — see file) | [2026-08-27-JMH-d891911.md](./2026-08-27-JMH-d891911.md) |
| 2026-08-27 | S2 | d891911 | baseline (P2 — cascade-delete, mixed-role soak, `BM-XC-003`/`BM-XC-004` — see file) | [2026-08-27-S2-d891911-2.md](./2026-08-27-S2-d891911-2.md) |
| 2026-08-27 | S2 | d891911 | baseline (P1 — writes, batch enrollment, login ramp — see file) | [2026-08-27-S2-d891911.md](./2026-08-27-S2-d891911.md) |
| 2026-08-26 | S3 | 1587ed3 | baseline (P0 subset, stress probe — see file) | [2026-08-26-S3-1587ed3.md](./2026-08-26-S3-1587ed3.md) |
| 2026-08-26 | S2 | 1587ed3 | baseline | [2026-08-26-S2-1587ed3.md](./2026-08-26-S2-1587ed3.md) |
| 2026-08-26 | S1 | 1587ed3 | baseline | [2026-08-26-S1-1587ed3.md](./2026-08-26-S1-1587ed3.md) |

<!-- Row shape, for the next entry:
| 2026-09-03 | S2 | 8e099d4 | baseline | [2026-09-03-S2-8e099d4.md](./2026-09-03-S2-8e099d4.md) |
-->

**Read all three 2026-08-26 records' Findings sections before trusting the numbers.** They were produced under a fast, single-repetition protocol (`02-benchmark-plan.md` §2, revised mid-Sprint-7 from an original 3-repetition/300s design that cost ~13h to run once) on a shared 4-core host, and every scenario in all three runs — including by-key controls expected to be flat — breached its SLO. The leading suspect was 20 VUs queuing against Hikari's default 10-connection pool, confounded with genuine per-row query cost; that first pass could not separate the two. Accepted as the first baselines per `../05-baseline-and-reporting.md` §1 (protocol followed, errors were 0% everywhere, configuration recorded) but flagged, not treated as clean hazard findings.

**The confound is resolved as of the 2026-08-27 runs.** `BM-XC-003` (in the second 2026-08-27 S2 file) isolates it cleanly: throughput plateaus at ~34 req/s starting exactly at 10 concurrent VUs — the Hikari pool size — while latency keeps climbing past that point. The 2026-08-26 numbers were real query cost *plus* pool queueing; the two are now separable. The 2026-08-27 runs also cover P1 (H4, H5) and P2 (H6, H7) for the first time — the harness for these existed since Sprint 8 but had never actually been run and recorded — and found two real bugs in the benchmark harness itself along the way (a seed-data `version` bug that made every write scenario fail, and a `SIGTERM`-vs-`SIGINT` bug that silently dropped the H7 soak companion script's output), both fixed in the same session. **Read the first 2026-08-27 S2 file's Finding F2 before trusting `BM-STU-007`'s numbers anywhere they appear** — that one confound is not yet resolved.
