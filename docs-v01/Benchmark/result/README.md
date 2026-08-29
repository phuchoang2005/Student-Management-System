# Benchmark Results — v0.1

Recorded benchmark runs verifying `docs-v01/Benchmark/07-improvement-roadmap.md`'s `IP-*` fixes against `docs-v00/Benchmark/result/`'s closed baseline set. Same convention as that folder ([`../../docs-v00/Benchmark/result/README.md`](../../docs-v00/Benchmark/result/README.md), not restated here): one file per run, `YYYY-MM-DD-<scale>-<short-sha>.md`, append-only, raw percentiles stored rather than only verdicts, large raw artifacts kept out of git (`bench/out/`). Read that file first if you haven't already.

**This folder holds only runs against v0.1-era code** (post-Sprint-9/10, `IP-01`…`IP-09`/`IP-11` landed, `IP-10` deferred) — `docs-v00/Benchmark/result/`'s six runs stay there unchanged as the pre-fix baseline set every comparison below is measured against.

---

## Run Index

Newest first. Verdict is one of **baseline** (accepted as the reference for its scale), **pass**, **regression**, or **discarded**.

| Date | Scale | Git SHA | Verdict | File |
| --- | --- | --- | --- | --- |
| 2026-08-29 | S2 | 156d247 | **mixed — regression found** (P0 read catalog + `BM-XC-003` pool sweep only — see file) | [2026-08-29-S2-156d247.md](./2026-08-29-S2-156d247.md) |

<!-- Row shape, for the next entry:
| 2026-09-05 | S1 | <sha> | pass | [2026-09-05-S1-<sha>.md](./2026-09-05-S1-<sha>.md) -->

**Read the 2026-08-29 record's Findings section before trusting the numbers.** Two things about it that aren't obvious from the Results table alone:

- **It is a "fast smoke" run, not a full verification pass.** It covers the S2 P0 read catalog and `BM-XC-003` only — no S1/S3 (so `IP-05`'s core "depth stops costing anything" claim is not actually tested — v0.0's own data shows the OFFSET-depth cost only diverged sharply at S3), no `writes`/`enrollment-batch`/login-ramp (`IP-08`, `IP-11`'s `BM-STU-007`), no `cascade-delete` (`IP-06`), no 30-minute soak (H7), no JMH. `docs-v01/Benchmark/07-improvement-roadmap.md`'s phase exit criteria list what a full follow-up run still needs to cover.
- **It found a real regression, not just improvements.** `IP-04` (enrollment N+1) and `IP-01` (pool size) both verify. `IP-02`/`IP-03` (search) do not: `BM-BK-001` p95 grew from 2594 ms to 14558 ms (+461%), and even the no-filter control (`BM-STU-001`) regressed +191% — root-caused to the combined-query shape the search rewrite introduced, not a harness artifact. See `docs-v01/Benchmark/09-v01-vs-v00-conclusions.md` for the full analysis.

Two harness bugs were found and fixed while producing this record (`bench/scenarios/student-search.js`'s dead deep-page discovery under cursor pagination; `bench/lib/reportStats.js`'s `latestExports` picking a `BM-XC-003` sweep file instead of the real `bench-all` export for `enrollment-list`) — both fixed before the record was written, not worked around.
