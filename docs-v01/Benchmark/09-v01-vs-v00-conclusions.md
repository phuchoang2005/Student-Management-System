# v0.1 vs. v0.0 — Benchmark Conclusions

Benchmark Documentation — v0.1 addendum, Part 3. Compares [`2026-08-29-S2-156d247.md`](./result/2026-08-29-S2-156d247.md) (the first `docs-v01/Benchmark/result/` run, against the current code — Sprint 9 + Sprint 10, `IP-01`…`IP-09`/`IP-11` landed, `IP-10` deferred) to [`docs-v00/Benchmark/06-conclusions-and-recommendations.md`](../../docs-v00/Benchmark/06-conclusions-and-recommendations.md) and the run records it's built from. Regression verdicts below follow [`05-baseline-and-reporting.md`](../../docs-v00/Benchmark/benchmark-strategy/05-baseline-and-reporting.md) §2's band table (Improvement / No change / Investigate / Block), applied per scenario.

**Scope: fast smoke.** This run covers the S2 P0 read catalog and `BM-XC-003` (the pool-saturation sweep) only. It does not cover S1/S3, `writes`/`enrollment-batch`/login-ramp, `cascade-delete`, the 30-minute soak, or JMH — see §6 for what that leaves unverified, and this folder's [`README.md`](./README.md) for why that scope was chosen.

**A methodology caveat that applies to every number below:** this run's host has 2 physical CPU cores; every v0.0 baseline ran on a 4-core host. Per `05-baseline-and-reporting.md` §1.1, a host change alone means this is not a clean baseline-quality comparison. Where a finding below is corroborated by independent evidence (query plans, `performance_schema` digests) rather than relative timing alone, the host difference doesn't change the conclusion — flagged inline where it might.

---

## 1. Verdict

**Mixed, not a clean win.** Two of the roadmap's items verify cleanly against their `08-hazard-fix-specs.md` hypotheses — `IP-01` (pool size) and `IP-04` (enrollment N+1). One does not: `IP-02`/`IP-03` (search) produced a severe, independently-root-caused **regression**, not the order-of-magnitude improvement the roadmap predicted. `IP-05` (keyset pagination) is untested by this run's scope, not confirmed working. The honest summary is: **the N+1 and pool fixes worked as designed; the search fix needs to be reopened before it can be called done.**

| Item | Hypothesis (`08-hazard-fix-specs.md`) | Result this run | Verdict |
| --- | --- | --- | --- |
| `IP-01` (pool 10→30) | Read-single controls return to SLO; `BM-XC-003` plateau moves higher | Plateau moved from ~34 to ~40-45 req/s; controls still breach SLO for other reasons (§3) | **Partially confirmed** |
| `IP-02`/`IP-03` (search) | `BM-BK-001` p95 drops an order of magnitude toward SLO | `BM-BK-001` p95 grew **2594 ms → 14558 ms (+461%)** | **Regression — Block** |
| `IP-04` (enrollment N+1) | `BM-ENR-002` stops costing ~5× `BM-ENR-001`; `BM-ENR-003` drops sharply | `BM-ENR-003` p95 **2520 ms → 544 ms (-78%)**; `BM-ENR-001/002` both -50%+ | **Confirmed** |
| `IP-05` (keyset paging) | `BM-STU-004`'s depth cost stops growing with page depth | Not tested — S2 alone shows no depth cost in v0.0 either (§6) | **Not verified this run** |
| `IP-07` (buffer pool) | Softens the S2→S3 `BM-BK-001` cliff | Not tested — no S3 run this pass | **Not verified this run** |
| `IP-11` (harness fix) | `BM-STU-007` error rate < 0.1% | Not exercised — `writes` not run this pass | **Not verified this run** |

---

## 2. Pool confound (`IP-01`) — partially resolved

Config confirmed live: `spring.datasource.hikari.maximum-pool-size=30` (up from the framework default of 10), MySQL `max_connections=151` (ample headroom).

| VUs | p95 (v0.0, pool=10) | req/s (v0.0) | p95 (v0.1, pool=30) | req/s (v0.1) |
| --- | --- | --- | --- | --- |
| 5 | 203 ms | 29.6 | 156 ms | 40.2 |
| 10 | 346 ms | 34.4 | 327 ms | 40.9 |
| 20 | 561 ms | 34.2 | 602 ms | 45.3 |
| 40 | 1081 ms | 33.8 | 1530 ms | **38.7** |

The sharp knee v0.0 found *exactly* at VUs=10 (the old pool size) is gone, and the throughput ceiling moved up (~34 → ~40-45 req/s) — the confound is real and `IP-01` measurably helped. But it did not move as far as tripling the pool might suggest, and p95 at VUs=40 is *worse* in absolute terms than v0.0's own VUs=40 number (1530 ms vs. 1081 ms). The leading suspect (§1's host caveat) is that a 2-core host hits CPU saturation well before a 30-connection pool would — `IP-01` likely has more headroom to show on a host closer to v0.0's 4 cores. This needs a same-host re-run to separate "the fix has a ceiling" from "this host has a ceiling" before treating 40-45 req/s as `IP-01`'s real plateau.

---

## 3. H1 — search (`IP-02`/`IP-03`): the headline finding, and it's a regression

This is the most important result in this run, and it's the opposite of what the roadmap predicted.

| BM ID | v0.0 p95 (S2) | v0.1 p95 (S2) | Δ | Verdict |
| --- | --- | --- | --- | --- |
| BM-STU-001 (no-filter control) | 242 ms | 704 ms | **+191%** | **Block** |
| BM-STU-002 (search, size 20) | 263 ms | 717 ms | **+173%** | **Block** |
| BM-STU-003 (search, size 100) | 407 ms | 787 ms | **+93%** | **Block** |
| BM-BK-001 (search, worst case) | 2594 ms | 14558 ms | **+461%** | **Block** |
| BM-CRS-001 (search) | 440 ms | 628 ms | **+43%** | **Investigate** |

**Root cause, evidenced at rung 3 (`05-baseline-and-reporting.md` §4):** Sprint 9 collapsed the filtered-search, no-filter, and cursor-continuation cases into one parameterized query per repository (`SpringDataStudentRepository.java:23-33`, `SpringDataBookRepository.java:28-37`):

```sql
SELECT * FROM books
WHERE (:query IS NULL OR :query = '' OR MATCH(isbn, title, author) AGAINST (:query IN BOOLEAN MODE))
  AND (:ownerId IS NULL OR owner_id = :ownerId)
  AND (:afterKey IS NULL OR isbn > :afterKey)
ORDER BY isbn
LIMIT :limit
```

Two compounding problems, both confirmed against the live database, not inferred from timing alone:

1. **`ORDER BY isbn` is not FULLTEXT's relevance order**, so `EXPLAIN` shows `Using filesort` even though the `MATCH` lookup itself uses the new index. For a common vocabulary term (`Guide*`), that's **2,435 of 8,000 books — 30% of the table** — that MySQL must fetch and sort before `LIMIT` can trim it to 20 rows. The live `performance_schema` digest confirms this isn't rare: the books-search query averages **833.5 rows examined per execution** across 1,878 real calls this run.
2. **`BM-STU-001`, which passes no query at all, also regressed +191%** — the filesort/FULLTEXT story doesn't explain that on its own. The students-search digest shows the *same* combined query executing 33,640 times this run at an average of **57.5 ms each (1.94M ms cumulative DB time)** regardless of whether `:query` is bound to `NULL`. The likely mechanism is that one parameterized statement now serves three logically distinct query shapes, and MySQL's plan for the parameterized form doesn't specialize per-call the way three separate, purpose-built queries (the pre-fix design) would.

This directly contradicts `08-hazard-fix-specs.md`'s `IP-02` hypothesis ("an order-of-magnitude change... not an incremental one"). It got an order-of-magnitude change — in the wrong direction, for the worst-case scenario. **Recommendation: reopen `IP-02`/`IP-03` before treating H1 as fixed.** The likely direction — not designed here, flagged for whoever picks this up — is splitting the combined query back into distinct no-filter/filtered statements and either accepting `ORDER BY <key>`'s filesort cost only for the no-filter case, or exploring a relevance-then-key-order two-step for the filtered case. This needs a rung-4 (JFR/profiler) pass before a fix is designed, not just this rung-3 read.

Three search scenarios that don't hit the `OR`-branch problem the same way *did* improve — `BM-BK-002` (owner-filtered, no FULLTEXT branch, uses `findByOwnerId`'s separate, single-purpose query, -25%), `BM-BK-003`/`BM-BK-004` (-38%/-48%) — consistent with the root cause being specific to the combined-query shape, not FULLTEXT indexing or cursor pagination in general.

---

## 4. H2 — enrollment N+1 (`IP-04`): confirmed working

| BM ID | v0.0 p95 (S2) | v0.1 p95 (S2) | Δ | Verdict |
| --- | --- | --- | --- | --- |
| BM-ENR-001 (20-row page) | 703 ms | 355 ms | -50% | Improvement |
| BM-ENR-002 (headline, 100-row page) | 670 ms | 308 ms | -54% | Improvement |
| BM-ENR-003 (course-filtered) | 2520 ms | 544 ms | **-78%** | Improvement |
| BM-ME-002 (`/me/courses`) | 433 ms | 692 ms | +60% | **Block** — see note |

`BM-ENR-001/002/003` all verify `08-hazard-fix-specs.md`'s hypothesis cleanly — the memo-pattern port from `BookService` worked. `BM-ME-002` is the one exception in this hazard group and doesn't fit the same story (it improved nowhere near as much, and moved the wrong direction relative to baseline) — `EnrollmentService.findByStudent` (the `/me/courses` backing method, `08`'s `IP-04` spec §"Targets") uses a distinct code path from `search`'s student-filtered/course-filtered branches, and this run didn't isolate why. Worth a follow-up digest check before assuming the same fix covers it.

---

## 5. H3 — deep paging (`IP-05`): not actually tested this run

`BM-STU-004` (-7%, no change) looks unremarkable, and that's expected, not a null result on the fix. v0.0's own data (`2026-08-27-S2-d891911-2.md`) shows the OFFSET-depth cost was already flat S1→S2 (318 ms → 332 ms) — the divergence only appeared at S3 (3375 ms, 0.34 growth exponent). This fast-smoke run doesn't include S3. **`IP-05`'s core claim — that keyset pagination makes page depth stop being a cost variable at all — needs an S1/S2/S3 re-run to verify, not this record.** The `bench/scenarios/student-search.js` harness fix made this pass (walking `cursor` forward during warm-up instead of relying on the now-removed `totalPages` field) is a prerequisite for that future run, already landed.

---

## 6. Deferred — not covered by this run

Per this folder's fast-smoke scope, these need a follow-up run before they can be called verified. `docs-v01/Benchmark/07-improvement-roadmap.md`'s phase exit criteria state what each needs:

- **H3 full curve / `IP-05`** (§5 above) — S1/S2/S3 re-run of `BM-STU-004` vs. `BM-STU-001`.
- **H4 (batch enrollment)** — deliberate cost, unaffected by any `IP-*` item; not re-measured.
- **H5 full ramp + `IP-08` blast radius** — needs `bench-auth-ramp` (`BM-IDN-001`) plus a concurrent non-auth scenario to check the blast-radius hypothesis.
- **H6 / `IP-06`** — needs `bench-cascade-delete` (`BM-XC-001` at N=200) to confirm the `EVENT_PUBLICATION` drain no longer loses events.
- **H7 / `IP-10`** — deliberately deferred by the roadmap (Phase 5, no horizontal-scaling plan yet); the 30-minute soak wasn't re-run.
- **`IP-11`'s `BM-STU-007`** — needs `writes` to confirm the harness fix drops its error rate below 0.1%.
- **JMH** — not re-run; nothing in this pass touched BCrypt cost or value-object validation.

---

## 7. Recommendation

1. **File an issue for §3's search regression before anything else** — it's a P0-severity finding by `05-baseline-and-reporting.md` §2's own table (multiple scenarios past the +50% Block threshold), on the endpoint the roadmap called the worst absolute latency in the whole benchmark set.
2. **Re-run `BM-XC-003` and the read catalog on a host matching v0.0's 4 cores**, or explicitly accept the host difference and re-baseline going forward — right now every Δ in this record carries that confound.
3. **Schedule the deferred scope (§6)** as the next `docs-v01/Benchmark/result/` run once §3's fix lands, so the follow-up run's before-numbers aren't still measuring the regression.

---

## 8. Update (2026-09-01) — §3's regression fixed, `IP-02`/`IP-03` reopened and re-verified

Recommendation 1 above is done. [`result/2026-09-01-S2-71ac8be.md`](./result/2026-09-01-S2-71ac8be.md) is the verification run. Root cause (§3) was the combined `search` `@Query` per repository stopping the planner from specializing per call — fixed by splitting `SpringDataStudentRepository`/`SpringDataBookRepository`/`SpringDataCourseRepository`'s single combined statement (keyword search folded into a no-filter/owner/cursor `OR`-branch) into two purpose-built statements: a keyword-search query (`MATCH` always present) and a no-keyword `browse` query, with the `Jdbc*Repository` adapters routing to whichever applies. No schema change; the `IP-02` FULLTEXT indexes were already correct.

- **`BM-STU-001`'s +191% regression is fully reversed** (704 ms → 204 ms, measured on a quiet host before contention set in this session) — confirms the root cause was the combined-query shape, not FULLTEXT indexing itself. `BM-STU-002`/`003` improved -47%/-56%. `BM-CRS-001` improved -18% (smaller effect expected — `courses` is the smallest of the three tables, per `03-benchmark-scenarios.md`'s own design intent for that scenario).
- **`BM-BK-001`'s wall-clock numbers this session are not usable as a regression verdict** — the host was not quiet (unlike this run's own protocol requirement), decisively evidenced by `BM-BK-004` (a control on code this fix never touched) also getting slower under k6 than in this doc's own regressed run. `performance_schema` digests (rung 3) confirm the fix at the database level regardless: the `search`/`browse` split produced two independently-costed statements post-fix, matching the diagnostic this doc used to find the regression in the first place.
- **New finding, not a new regression:** the book/course `browse` query's owner/scope-optional branch has the same class of problem for a fully-unfiltered first page (`EXPLAIN` shows a full table scan) — confirmed via `EXPLAIN` to be present identically in the *pre-fix* query shape too, so it predates this fix and this Sprint. Not fixed in this pass, deliberately, for the same reason §3's fix stayed scoped to one isolated change. Flagged as a follow-up (`2026-09-01-S2-71ac8be.md` Finding F3) rather than bundled in.

**Still open:** a clean, quiet-host re-run of `book-search`/`course-list` to get a trustworthy `BM-BK-001` absolute number (recommendation 2 above, unchanged), and the FULLTEXT/`ORDER BY`-filesort interaction this doc's §3 already deferred to a rung-4 profiler pass.
