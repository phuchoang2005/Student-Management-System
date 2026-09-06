# Conclusions & Recommendations

Benchmark Documentation — synthesis of the six accepted runs in [`result/`](./result/), read against the hazard register and SLOs in [`benchmark-strategy/01-benchmark-strategy.md`](./benchmark-strategy/01-benchmark-strategy.md) and the regression/reporting rules in [`benchmark-strategy/05-baseline-and-reporting.md`](./benchmark-strategy/05-baseline-and-reporting.md).

Unlike the five `benchmark-strategy/` documents, which specify what to measure and how, this document answers the question the whole set exists to answer: **given what was actually measured, what does it mean, and what should change?** It draws only on the six run records — no new numbers are produced here.

Per `05-baseline-and-reporting.md` §5, this document does not itself authorize a code change. Every recommendation below still needs its own GitHub issue carrying the before-number, the hypothesis, and (later) the after-number, exactly as that section requires.

---

## 1. Overall Verdict

The system is **functionally correct under load** — real error rates were 0.00% in every scenario except two runs contaminated by benchmark-harness bugs (§5 below), and both bugs were found, fixed, and documented in the same session they appeared. Correctness was never the finding.

**Performance is another matter.** Every one of the 19 scenarios in both P0 baselines (S1 and S2, [`2026-08-26-S1-1587ed3.md`](./result/2026-08-26-S1-1587ed3.md), [`2026-08-26-S2-1587ed3.md`](./result/2026-08-26-S2-1587ed3.md)) breached its proposed SLO class — including single-row, by-business-key control reads (`BM-STU-005`, `BM-BK-004`, `BM-ENR-004`, `BM-ME-001`) that should be flat regardless of table size. That is not nineteen independent problems. The 2026-08-27 runs isolated why: **one untuned setting — the HikariCP default pool size of 10 — is the dominant amplifier behind almost the entire result set** (§2). Once that is priced separately, three real, hazard-specific problems remain (§3), one confirmed defect beyond what was originally hazarded (H6, §3.5), and a longer tail of costs that are large in absolute terms but deliberate, already-accepted, or provably noise (§4).

| | |
| --- | --- |
| Scenarios run | 19 P0/P1 HTTP scenarios (S1/S2), 6 P0 scenarios (S3), 3 P2/cross-cutting scenarios, 4 JMH microbenchmarks |
| SLO breaches, S1/S2 baselines | 19 / 19 — see the pool-contention finding below before reading this as 19 hazards |
| Real error rate | 0.00% everywhere except two harness bugs (§5) |
| Hazards with a clean, isolated measurement | H1, H2, H3, H4, H5, H6, H7 (H8 closed as a prerequisite, partially — see §6, P1) |
| Confirmed real defect (not merely "slow") | H6 — cascade event-publication loss under burst delete (§3.5) |

---

## 2. The Single Biggest Lever: An Untuned Connection Pool

`BM-XC-003` ([`2026-08-27-S2-d891911-2.md`](./result/2026-08-27-S2-d891911-2.md)) exists for exactly one purpose: separate genuine per-row query cost from queueing on HikariCP's default 10-connection pool. It does so cleanly.

| VUs | p95 | req/s |
| --- | --- | --- |
| 5 | 203 ms | 29.6 |
| 10 | 346 ms | 34.4 |
| 20 | 561 ms | 34.2 |
| 40 | 1081 ms | 33.8 |

Throughput **plateaus at ~34 req/s starting exactly at 10 concurrent VUs** — the pool size — while p95 latency keeps climbing linearly to 5× past that point. Past 10 concurrent requests, added load buys no more work done, only queueing. The live actuator snapshot taken during the same session's 30-minute soak confirms it directly: **10/10 connections active, 31–33 threads pending, sustained for the soak's full duration**, not a transient spike.

This single fact reframes every number in the two 2026-08-26 baselines. Those runs drove 20 VUs against a 10-connection pool and found every scenario — including flat-by-design controls — breaching its SLO. `BM-XC-003` shows why: at 20 VUs the pool was already saturated regardless of what any individual query cost. The 2026-08-26 records flagged this as a suspected confound and could not isolate it (`2026-08-26-S1-1587ed3.md` Finding F2); `BM-XC-003` is the run that resolves the suspicion into a measurement.

**Practical consequence:** tuning `HikariCP`'s pool size is the one change most likely to move the *largest number* of scenarios at once, because it is upstream of every hazard below rather than specific to any one of them. It is also the cheapest to make — a configuration value, not a code change — and the cheapest to verify, because `BM-XC-003` is already built to re-run and produce a new plateau point.

---

## 3. Where Performance Is Bad, and Why

Ranked by severity, using the numbers that survive the pool-contention read above.

### 3.1 Book search — the worst absolute numbers in the entire benchmark set (H1)

`BM-BK-001` (`GET /api/v1/books?query={term}`) is the single slowest scenario measured, at every scale it was run:

| Run | p95 |
| --- | --- |
| S1 ([`2026-08-26-S1`](./result/2026-08-26-S1-1587ed3.md)) | 2282 ms |
| S2 ([`2026-08-26-S2`](./result/2026-08-26-S2-1587ed3.md)) | 2594 ms |
| S3 ([`2026-08-26-S3`](./result/2026-08-26-S3-1587ed3.md)) | 8571 ms |
| Mixed-role soak, concurrent traffic ([`2026-08-27-S2-…-2`](./result/2026-08-27-S2-d891911-2.md)) | 7010 ms |

Cause: **H1**, a leading-wildcard `LIKE` scan across ISBN, title, and author, run twice per request (rows + `COUNT(*)`), on a table with no usable index for that predicate shape (`book/internal/SpringDataBookRepository.java:30-49`). The S2→S3 jump is sharper than S1→S2 (2.59 s → 8.57 s vs. 2.28 s → 2.59 s) because S3's ~80,000-row books table stops fitting the benchmark host's **128 MB `innodb_buffer_pool_size`** — the untouched container default — so the scan starts hitting disk through Colima's VM layer rather than staying in memory. The `BM-XC-004` scale-sweep classifies `BM-BK-001`'s growth exponent at 0.20 (sub-linear relative to the table's 800× row growth), but that is a relative classification; the absolute number went from 2.3 s to 8.6 s and every one of those seconds is real, felt latency on the busiest search screen in the book module.

The same shape, smaller in absolute terms because the tables are smaller, appears in `BM-STU-002`/`BM-STU-003` (student search) and `BM-CRS-001` (course search) — H1 is confirmed as a shape problem general to all three `LIKE`-based search endpoints, not a books-specific one (`03-benchmark-scenarios.md` `BM-CRS-001`'s own stated purpose).

### 3.2 Enrollment listing N+1 (H2)

`BM-ENR-003` (course-filtered enrollment listing, page size 100) reached **p95 2520 ms at S2** ([`2026-08-26-S2`](./result/2026-08-26-S2-1587ed3.md)) — the third-worst scenario measured at that scale, on a table (`enrollments`) that is not even the largest in the schema. Cause: `EnrollmentService.search` resolves each row's course through a separate `courseLookup.summaryOf(...)` call inside the page `map` (`enrollment/application/EnrollmentService.java:179,189,200`) — up to 100 extra round trips for one HTTP request. `BM-XC-003`'s choice of `BM-ENR-002` as its saturation probe is deliberate for the same reason: a request that holds a pooled connection across ~101 sequential statements exhausts the pool at a *lower* concurrency than a single-query endpoint would, which is why H2 is a concurrency problem as much as a latency one (`03-benchmark-scenarios.md` `BM-XC-003`).

The clearest before/after evidence for this is inside the benchmark set itself: `BM-ME-003` (owner-filtered book read, which goes through `BookService`'s per-page memo, §4) and `BM-ME-002` (student's own enrollment list, same N+1 as `BM-ENR-*`) are the same shape, same page size, same S2 dataset — and `BM-ME-002`'s p95 (433 ms) is roughly double `BM-ME-003`'s despite being on the smaller table, because one has the N+1 and the other does not.

### 3.3 Deep `OFFSET` paging (H3)

`BM-STU-004` (page drawn from the last decile of available pages) grew p95 **318 ms (S1) → 332 ms (S2) → 3375 ms (S3)** — a 0.34 growth exponent, the second-steepest of the six `BM-XC-004` scale-sweep scenarios, and materially worse than `BM-STU-001` (same query, shallow page, H3's own control), which stayed within a few percent across all three scales. MySQL generates and discards every skipped row before returning the first one the client wanted (`LIMIT :limit OFFSET :offset`); the discarded rows come out of the same full scan H1 already pays for, so H1 and H3 compound on the same search endpoints rather than being independent costs.

### 3.4 Login under concurrency (H5) — a deliberate per-call cost with a real operational shape

`BM-JMH-001` puts a precise floor under this: BCrypt strength 10 costs **90.79 ms per hash, 94.70 ms per verify** on the benchmark host, confirmed as clean 2× doubling per strength level (`2026-08-27-JMH-d891911.md`). That per-call cost is a stated security property (`01-benchmark-strategy.md` H5), not a defect, and this document does not recommend lowering it.

What the JMH number cannot show — and `BM-IDN-001`'s ramp does — is what happens when many logins arrive at once:

| VUs | p95 |
| --- | --- |
| 1 | 170 ms |
| 10 | 823 ms (+384%) |
| 25 | 1706 ms |
| 50 | 3124 ms |
| 100 | 6069 ms |

The knee is sharp and early: latency more than quadruples going from 1 to 10 concurrent logins, and keeps climbing roughly linearly with offered load past that. On a 4-core host running BCrypt (genuinely CPU-bound, not I/O-bound), this is expected — but it means a start-of-term login rush, this system's most realistic saturation event by design intent (`01-benchmark-strategy.md` H5), degrades every concurrent login's latency together, and nothing in the current configuration isolates that degradation from the rest of the application's Tomcat thread pool.

### 3.5 Cascade-delete event loss (H6) — the one hazard that crossed into a confirmed defect

`BM-XC-001` at N=200 ([`2026-08-27-S2-…-2`](./result/2026-08-27-S2-d891911-2.md)) found that **568 of 801 `EVENT_PUBLICATION` rows were still incomplete more than 30 minutes after a 200-student burst delete** — not delayed, permanently dropped: `ThreadPoolTaskExecutor` does not retry a rejected task, and the executor backing `StudentDeleted`/`CourseDeleted` cascades runs on a 2–4 thread pool with a 50-slot queue (`shared/async/AsyncConfig.java:30-32`).

The finding is bounded, and it matters to say precisely how: **no student, book-ownership, or enrollment data is at risk.** The `ON DELETE SET NULL` / `ON DELETE CASCADE` constraints on `books.owner_id`, `enrollments`, and `users` (`V1__init_schema.sql:42,51,68`) are synchronous, part of the same `DELETE` statement, and independent of whether the async listener ever runs. What is wrong is Spring Modulith's own audit trail of what fired — `EVENT_PUBLICATION` — which is left silently incomplete for the majority of a realistic end-of-term burst. Anything that comes to depend on that registry being complete later (a future listener, an audit report) would see a false picture today, without any error being raised anywhere.

### 3.6 Unbounded heap-resident sessions (H7)

The 30-minute soak's session curve puts the first real number on a previously qualitative risk: **roughly 1.7–2.3 MB per active session** in the trustworthy early/mid-soak window (`2026-08-27-S2-…-2.md` Finding F5; the late-soak figures up to 17.2 MB/session are a measurement artifact of a shrinking session-count denominator, not a real cost — see that finding for why). `maximumSessions(SessionLimit.UNLIMITED)` (`shared/security/SecurityConfig.java:125`) places no cap, and sessions are heap-resident (`SessionRegistryImpl`), which `01-system-overview.md` §5 already names as the blocker for horizontal scaling. This hazard degrades only under sustained uptime with many concurrent distinct logins, which is why it is rated P2 — it did not visibly hurt this benchmark's numbers, but it is the one hazard whose cost compounds with calendar time rather than dataset size or request rate, so it is invisible in every other scenario here by construction.

---

## 4. What Is *Not* Actually Bad

Worth stating explicitly, because several of these look like the hazards above at a glance and the docs are explicit that recording a null result stops someone re-discovering it later (`05-baseline-and-reporting.md` §5.1):

- **`BookService.search`'s owner-code memo already defeats H2** for book listings (`book/application/BookService.java:177,232`) — `BM-BK-003` confirms it: a 100-row page with many distinct owners costs a per-page memo, not 100 lookups. This is the "H2, fixed" reference point the enrollment module should be measured against, not a place needing further work.
- **H4's batch-enrollment cost is deliberate and now quantified, not a defect**: p95 480 ms (1 course) → 663 ms (10 courses) → 2950 ms (50 courses, worst case). The 10→50 step is close to linear (consistent with the documented ~2N+2-statement/N-commit design), which is exactly what partial-success durability (`api-specification.md` §5 decision #12) costs by design. The number belongs in client-facing API guidance on where to split a large batch, not in an issue proposing to change the transaction shape.
- **JMH value-object construction and MapStruct mapping are noise at request scale.** `Credits`/`Isbn`/`StudentCode` construction: 2–3 ns. `Email` (the one outlier, §4's own Finding F1 in the JMH record): 162 ns — five orders of magnitude below a single BCrypt hash. MapStruct page mapping at 100 rows: under 2 µs for either mapper. None of this is worth optimizing; the whole cost of a slow list response is in the query layer, not here.
- **The AES cipher's per-call `Cipher.getInstance` costs ~5× a reused instance (4.04 µs vs. 0.83 µs) — real, but dwarfed.** It runs alongside a 91 ms BCrypt hash on the registration path; the delta is roughly 0.004% of that cost. Per the JMH guardrail (`01-benchmark-strategy.md` §5.1), this number alone does not justify a change.
- **No user-enumeration timing signal.** `BM-IDN-002` (wrong password) and an interpolated correct-password login at the same concurrency land in the same latency range — the secure outcome, and a successful null result, not a finding.
- **`books.owner_id` and `enrollments.course_id` are not missing indexes** — both are FK columns InnoDB indexes by side effect (`01-benchmark-strategy.md` §3.1). Adding an explicit index here would cost write throughput for no read benefit.

---

## 5. Data Reliability Caveats

Two things temper how the numbers above should be read, both already flagged in the run records and worth repeating together here:

- **`BM-STU-007` (student update) is not a trustworthy Write-simple number in either 2026-08-27 record.** Its 46.96% error rate is a **benchmark-harness bug**, not an application defect: `bench/lib/vuShard.js`'s per-VU sharding does not fully isolate row targets across scenario stages under k6 v2.2.0's actual VU numbering, so some concurrent `PUT`s land on the same student row and correctly receive `409 Conflict` from the app's optimistic locking. Direct reproduction outside k6 confirms the locking itself is correct (4×409 + 1×200 for 5 genuinely concurrent writers on one row). This scenario's latency numbers are excluded from every ranking above and should not be used as a regression baseline until the sharding fix lands (§6, P2).
- **Every S1/S2 baseline is a single-repetition measurement on a shared 4-core laptop**, per the protocol `02-benchmark-plan.md` §2 adopted mid-Sprint-7 for cost reasons. Absolute latencies are not portable off this host (`01-benchmark-strategy.md` §7.1); only deltas — same scenario, same host, across scales or commits — are treated as evidence here. The pool-saturation finding (§2) is the one number in this set built to survive that caveat regardless, because it is a plateau (a structural change in behavior), not a single latency figure.

---

## 6. Recommendations

Ordered by expected impact ÷ cost, and grouped the way `01-benchmark-strategy.md` §10 groups severity. **None of these is authorized by this document alone** — each needs the issue `05-baseline-and-reporting.md` §5 requires before any code changes.

### P0 — highest leverage, lowest cost

| # | Recommendation | Targets | Why this first |
| --- | --- | --- | --- |
| 1 | **Raise HikariCP's `maximumPoolSize` above the framework default of 10**, and re-run `BM-XC-003` to find the new plateau. Check the corresponding MySQL-side `max_connections` headroom before raising it far. | The confound behind §2 — improves nearly every scenario in this report simultaneously | Cheapest possible change (one config value), and the one already proven, by `BM-XC-003`, to be the dominant constraint at only 10 concurrent users — a plausible number of concurrently active staff, let alone students |
| 2 | **Replace the leading-wildcard `LIKE` search with an index-friendly alternative** — a MySQL `FULLTEXT` index on the searched columns, or an n-gram/trigram index, for students/books/courses search | H1 — `BM-BK-001` (worst scenario in the whole set), `BM-STU-002/003`, `BM-CRS-001` | Directly targets the single slowest measured endpoint (2.3–8.6 s p95) |
| 3 | **Stop issuing a full second table scan for `COUNT(*)` on every search request** — cache/estimate the count, compute it only on the first page, or replace it with a cheap "has more" existence check for deep pages | H1 — same scenarios as #2 | Halves the scan cost of every search request without touching the index question |
| 4 | **Batch-resolve enrollment listing's per-row course lookups** — replace `courseLookup.summaryOf(...)` inside the page `map` with one `IN`-clause bulk lookup into a page-scoped map, mirroring the pattern `BookService.search` already uses for owners (§4) | H2 — `BM-ENR-001/002/003`, `BM-ME-002` | A known-working pattern already exists in the same codebase; this is porting it, not designing it |
| 5 | **Switch deep list pages from `OFFSET`/`LIMIT` to keyset (seek) pagination** — `WHERE id > :lastSeenId ORDER BY id LIMIT :n`, which uses the same index instead of generating and discarding offset rows | H3 — `BM-STU-004`, compounds with #2 on every search endpoint | Eliminates a cost that grows with page depth for a UI change (cursor instead of page number) most list UIs already tolerate |

### P1 — real cost or real defect, moderate effort

| # | Recommendation | Targets | Why |
| --- | --- | --- | --- |
| 6 | **Fix H6's silent event loss** — widen `AsyncConfig`'s pool/queue to absorb a realistic burst (e.g., a 200-student delete), add retry/backoff for rejected `event_publication` tasks instead of dropping them, or bound the input by requiring bulk deletes above some size to be client-paginated, and document whichever bound is chosen | H6 — confirmed defect, §3.5 | The only hazard in this set that crossed from "slow" to "silently wrong"; `01-benchmark-strategy.md` §10 rates this class of finding at least Major |
| 7 | **Raise `innodb_buffer_pool_size` off the 128 MB container default** to a size that fits at least the S2 dataset, and re-run the `BM-XC-004` scale sweep to see whether the S2→S3 jump (§3.1) is buffer-pool-fit or query-cost — currently the two are conflated | H1 — sharpens `BM-BK-001`'s S3 cliff specifically | Cheap Docker Compose change; the run record already names this as the leading unverified hypothesis for the S2→S3 jump |
| 8 | **Isolate login bursts from the rest of the application** — a bulkhead (dedicated bounded thread pool) for the auth endpoint, or basic rate-limiting/backpressure (`429`) once a login queue passes a threshold — so a start-of-term rush degrades login latency without starving every other endpoint's Tomcat threads | H5 — `BM-IDN-001`'s knee, §3.4 | Does not touch BCrypt's work factor (a deliberate security property); addresses the *blast radius* of the saturation event instead of its per-call cost |
| 9 | **Keep `spring-boot-starter-actuator` + Micrometer wired permanently behind the `benchmark` profile** rather than as one-off instrumentation — it is what let this pass measure Hikari pool state and session counts directly for the first time (`2026-08-27-S2-…-2.md` Finding F4) instead of inferring them | H8 — closes the remaining "unattributable black-box latency" gap for future runs | Every future red run gets cheaper to diagnose; the `Testing/`-parity profile-gating pattern (`01-benchmark-strategy.md` §8) already specifies how to do this safely |

### P2 — lower urgency, worth tracking

| # | Recommendation | Targets | Why later |
| --- | --- | --- | --- |
| 10 | **Move sessions off-heap before any horizontal-scaling plan** (e.g., Redis-backed Spring Session), sized using the ~1.7–2.3 MB/session figure from §3.6 | H7 | Already a known architectural blocker (`01-system-overview.md` §5); this benchmark only adds the number, it doesn't change the urgency — no horizontal-scaling plan exists yet to block |
| 11 | **Fix `bench/lib/vuShard.js`'s VU-sharding collision** so `BM-STU-007` produces a trustworthy number on the next run | Benchmark harness only — not the application | Needed before `BM-STU-007` can be trusted as a regression baseline, but it is a tooling gap, not a user-facing cost |

---

## 7. Sources

Every number above traces to one of these six accepted run records; none is re-derived or estimated here.

| Run | Scale | Covers |
| --- | --- | --- |
| [`2026-08-26-S1-1587ed3.md`](./result/2026-08-26-S1-1587ed3.md) | S1 | First P0 baseline; flags the pool-contention confound (Finding F2) |
| [`2026-08-26-S2-1587ed3.md`](./result/2026-08-26-S2-1587ed3.md) | S2 | Second P0 baseline, same confound |
| [`2026-08-26-S3-1587ed3.md`](./result/2026-08-26-S3-1587ed3.md) | S3 | Stress probe, informal S1→S2→S3 curve |
| [`2026-08-27-S2-d891911.md`](./result/2026-08-27-S2-d891911.md) | S2 | P1 — writes, batch enrollment (H4), login ramp (H5); harness bugs F1/F2 |
| [`2026-08-27-S2-d891911-2.md`](./result/2026-08-27-S2-d891911-2.md) | S2 | P2 — cascade delete (H6), mixed-role soak (H7), `BM-XC-003` (pool isolation), `BM-XC-004` (formal scale sweep) |
| [`2026-08-27-JMH-d891911.md`](./result/2026-08-27-JMH-d891911.md) | JMH | BCrypt, AES cipher, value objects, MapStruct mapping |

Hazard definitions and evidence citations: [`benchmark-strategy/01-benchmark-strategy.md`](./benchmark-strategy/01-benchmark-strategy.md) §3. SLO classes: same document §4.2. Regression/verdict rules: [`benchmark-strategy/05-baseline-and-reporting.md`](./benchmark-strategy/05-baseline-and-reporting.md) §2.
