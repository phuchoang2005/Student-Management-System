# Hazard Fix Specs

Benchmark Documentation — v0.1 addendum, Part 2 of 2 ([Improvement Roadmap](./07-improvement-roadmap.md) → Hazard Fix Specs).

Per-recommendation detail for each `IP-01`…`IP-11` item the [roadmap](./07-improvement-roadmap.md) sequences. Each entry follows the same shape: **Source** (what `06-conclusions-and-recommendations.md` §6 and the underlying hazard evidence already establish — cited, not re-derived), **Approach** (technical direction; new detail is marked as such), **Targets** (`BM-*` scenarios to re-run), **Hypothesis & target metric** (tied to the SLO classes in `01-benchmark-strategy.md` §4.2), **Verification**, and **Dependencies**.

This document does not authorize any code change — `05-baseline-and-reporting.md` §5 still requires a linked GitHub issue per item before implementation.

---

## IP-01 — Raise HikariCP's connection pool size

**Source:** `06` §6 P0 #1; the confound is established in `06` §2 via `BM-XC-003` ([`2026-08-27-S2-d891911-2.md`](../../docs-v00/Benchmark/result/2026-08-27-S2-d891911-2.md)) — throughput plateaus at ~34 req/s starting exactly at 10 concurrent VUs (the pool size), with the live actuator snapshot showing 10/10 connections active and 31–33 threads pending, sustained for a full 30-minute soak.

**Approach:** raise `spring.datasource.hikari.maximum-pool-size` above the framework default of 10 in `management/src/main/resources/application.properties` (or the `benchmark` profile's properties file, if pool sizing should differ between profiles — a decision this spec does not make). Check MySQL-side `max_connections` headroom before raising it far, per `06` §6's own caveat. No application code changes.

**Targets:** `BM-XC-003` (the scenario purpose-built to find the new plateau) and, since the pool is a confound rather than a hazard-specific fix, the full P0 read catalog (`BM-STU-001–005`, `BM-BK-001–004`, `BM-CRS-001–003`, `BM-ENR-001–004`) at S1/S2.

**Hypothesis & target metric:** `BM-XC-003`'s plateau moves from ~34 req/s at 10 VUs to a new, higher VU count before p95 starts climbing linearly — the new plateau point is the deliverable, not a specific number, since the right pool size is bounded by MySQL's `max_connections` and host CPU, not by this document. Read-single controls (`BM-STU-005`, `BM-BK-004`, `BM-ENR-004`, `BM-ME-001`) — which breached their SLO in every S1/S2 baseline purely from pool queueing — are expected to return to their Read-single SLO (≤ 50 ms p95) once this lands, since nothing about their own query cost should have changed.

**Verification:** re-run `BM-XC-003` at the same VU sweep (5/10/20/40) and record the new plateau; re-run the full P0 read catalog and confirm the read-single controls now pass without any other code change.

**Dependencies:** none — first item in [Phase 1](./07-improvement-roadmap.md#2-phase-1--foundation-config--tooling-no-application-code-risk). Every other `IP-*` item's before-number should be re-measured against the post-`IP-01` floor, not the original S1/S2 baselines, or its own effect will be conflated with this one's.

---

## IP-02 — Replace leading-wildcard `LIKE` search

**Source:** `06` §6 P0 #2; hazard H1, `01-benchmark-strategy.md` §3 — `LIKE CONCAT('%', :query, '%')` across ISBN/title/author (books), student name/email/code, and course code/name defeats any B-tree index, cited at `student/internal/SpringDataStudentRepository.java:23-44`, `book/internal/SpringDataBookRepository.java:30-49`, `course/internal/SpringDataCourseRepository.java:21-36`. `BM-BK-001` is the worst absolute latency measured anywhere in the benchmark set (`06` §3.1): p95 2282 ms (S1) → 2594 ms (S2) → 8571 ms (S3, where the ~80,000-row table stops fitting the 128 MB `innodb_buffer_pool_size` default — see `IP-07`).

**Approach:** a MySQL `FULLTEXT` index (or an n-gram/trigram index, if term-fragment matching needs to survive — `06` §6's own phrasing leaves the choice open) on the searched columns for all three repositories named above. `01-benchmark-strategy.md` §3.1 already rules out one wrong fix here: `books.owner_id` and `enrollments.course_id` are not missing indexes and should not be touched by this work — this item is scoped to the free-text search predicate only, not the FK-filtered paths (`BM-BK-002`, `BM-CRS-002`).

**Targets:** `BM-BK-001`, `BM-STU-002`, `BM-STU-003`, `BM-CRS-001`, and the `BM-XC-004` scale-sweep legs that include them.

**Hypothesis & target metric:** `BM-BK-001` p95 at S2 drops toward the Read-list SLO (≤ 150 ms) from its measured 2594 ms — an order-of-magnitude change, not an incremental one, since a full table scan versus an index seek is a difference in access path, not in degree. `BM-XC-004`'s growth-exponent classification for `BM-BK-001` (currently 0.20, sub-linear but from a high absolute floor) should drop further toward flat.

**Verification:** re-run `BM-BK-001`, `BM-STU-002/003`, `BM-CRS-001` at S1/S2/S3; confirm via `EXPLAIN ANALYZE` (rung 3 of the escalation ladder, `05-baseline-and-reporting.md` §4) that the access path is now an index/fulltext lookup, not a scan — "rows examined ÷ rows sent near 1" is the diagnostic `05` §4 names for this.

**Dependencies:** sequenced after `IP-03` within [Phase 2](./07-improvement-roadmap.md#3-phase-2--search-endpoint-shape-fixes-h1--h3), so its own before-number isn't still paying for the redundant `COUNT(*)` scan `IP-03` removes. Independent of `IP-01`/`IP-07` at the code level, but should be measured against the Phase 1 floor.

---

## IP-03 — Stop double-scanning for `COUNT(*)`

**Source:** `06` §6 P0 #3 — every search request issues the paged read and a separate `COUNT(*)` as two statements, doubling the scan cost of every search (`01-benchmark-strategy.md` H1). Same repository files as `IP-02`.

**Approach:** `06` §6 names three options without choosing between them: cache/estimate the count, compute it only on the first page and reuse it for subsequent pages of the same query, or replace it with a cheap "has more" existence check (`SELECT 1 ... LIMIT 1 OFFSET :limit`) for deep pages. This spec does not pick one — see the coupling note below, which constrains the choice.

**A constraint `06` §6 does not state:** the backend's `PageResponse` record (`shared/web/PageResponse.java`) exposes `totalPages` to every client, and the frontend's shared `Pagination.tsx` component renders "Page N of `totalPages`" for all five list screens. **Compute-once-reuse** preserves that contract with no frontend change. **The "has more" existence check does not** — it cannot produce a `totalPages` value, so choosing it requires the frontend to drop the absolute page-number display, which is `IP-05`'s territory, not this item's. Unless that UI change is committed to, this item should implement compute-once-reuse.

**Targets:** same as `IP-02` — `BM-BK-001`, `BM-STU-002/003`, `BM-CRS-001`.

**Hypothesis & target metric:** roughly halves the per-request scan cost independent of `IP-02` — the S2 `BM-BK-001` p95 (2594 ms) should drop meaningfully even before `IP-02`'s index lands, since one full scan costs half of two. The two effects are expected to be roughly multiplicative, not additive, when combined.

**Verification:** re-run the same four scenarios; confirm via `performance_schema` digest counts (rung 3) that the query-execution count per request drops from 2 to 1.

**Dependencies:** first item in [Phase 2](./07-improvement-roadmap.md#3-phase-2--search-endpoint-shape-fixes-h1--h3) — no dependency on `IP-02`, deliberately ordered before it so its effect is isolated. Constrains the implementation choice for `IP-05` as described above.

---

## IP-04 — Batch-resolve enrollment listing's per-row lookups

**Source:** `06` §6 P0 #4; hazard H2 — `EnrollmentService.search` resolves each row's course through `courseLookup.summaryOf(...)` inside the page `map` (`enrollment/application/EnrollmentService.java:179` for the student-filtered path, `:189` for the course-filtered path, `:200` for `findByStudent` backing `GET /api/v1/me/courses`), up to 100 extra round trips for one page. `BM-ENR-003` measured p95 2520 ms at S2 (`06` §3.2) — the third-worst scenario at that scale.

**Approach:** `06` §4 already names the reference pattern — `BookService.search`'s per-page owner-code memo (`book/application/BookService.java:177`, `:232`), which resolves a page's distinct owner codes through one `Map<Long, String>` built for the page rather than once per row. Port the same shape to `EnrollmentService`: collect the page's distinct course codes (or student codes, for the course-filtered path), resolve them through one bulk `IN`-clause lookup into a page-scoped map, then map each row against that map instead of calling `courseLookup.summaryOf(...)` per row. This requires `CourseLookup` (and, for the course-filtered path, `StudentLookup`) to expose a bulk lookup method if one doesn't already exist — confirm during implementation rather than assuming the current lookup interface's shape.

**Targets:** `BM-ENR-001`, `BM-ENR-002`, `BM-ENR-003`, `BM-ME-002`, and `BM-JMH-004` (MapStruct mapping cost, to confirm the fix didn't just move the cost into mapping).

**Hypothesis & target metric:** `BM-ENR-002` (100-row page) should stop costing roughly 5× `BM-ENR-001` (20-row page) — `06` §3.2 names this exact ratio as what currently confirms the N+1 dominates. After the fix, the two should scale with page size the way `BM-BK-003` already does (`06` §4): a page of 100 enrollments spanning a handful of distinct courses should cost a handful of lookups, not 100. `BM-ME-002`'s p95 (433 ms at S2) should converge toward `BM-ME-003`'s (which has no N+1 today) rather than sitting roughly double it.

**Verification:** re-run `BM-ENR-001/002/003` and `BM-ME-002`; confirm via `performance_schema` digest execution counts (rung 3) that a `size=100` page now issues on the order of one bulk lookup query, not ~101 individual ones — the same "statements per request" check `05-baseline-and-reporting.md` §4 names for proving H2.

**Dependencies:** none — independent module (`enrollment/`), no file overlap with Phase 2. Runs as [Phase 3](./07-improvement-roadmap.md#4-phase-3--enrollment-n1-h2), safely parallel with Phase 2.

---

## IP-05 — Keyset (seek) pagination for deep list pages

**Source:** `06` §6 P0 #5; hazard H3 — `LIMIT :limit OFFSET :offset` makes MySQL generate and discard every skipped row before returning the first one the client wanted. `BM-STU-004` (deep page) grew p95 318 ms (S1) → 332 ms (S2) → 3375 ms (S3) — a 0.34 growth exponent, materially worse than `BM-STU-001` (shallow page, same query), which stayed flat across all three scales (`06` §3.3). H3 compounds with H1 on every search endpoint, since the discarded rows come out of the same scan `IP-02`/`IP-03` target.

**Approach — file-level detail new to this document, grounded in this repository's current code, not restated from v00:** replace `LIMIT :limit OFFSET :offset` with `WHERE <sort-key> > :lastSeenKey ORDER BY <sort-key> LIMIT :limit` in the three repositories (`SpringDataStudentRepository`, `SpringDataBookRepository`, `SpringDataCourseRepository`) — each already sorts by a natural key (`student_code`, `isbn`, `course_code` respectively), which is exactly the shape keyset pagination needs. This is not a repository-internal change, though:

- **Backend contract:** `shared/web/PageResponse.java` currently exposes `(page, size, totalElements, totalPages, content)` — a page-number contract. A cursor-based response needs a different shape (e.g., a `nextCursor` alongside `content`), which is a breaking change to every client of every paged endpoint, not only the three search endpoints this hazard names.
- **Frontend contract:** `management-frontend/src/lib/api/types.ts`'s `Page<T>` interface mirrors `PageResponse` exactly. `management-frontend/src/lib/api/endpoints.ts` passes `page`/`size` as query params from ~9 call sites (`students.search`, `books.search`, `courses.search`, `enrollments.byStudent`/`byCourse`, `me.courses`/`me.books`, `staffAccounts.list`). The shared `Pagination.tsx` component renders "Page N of `totalPages`" with prev/next controls built on absolute page-number math, and the shared `usePagedResource.ts` hook stores `page: number` as state and resets it to 0 on query change. All five list screens (`students`, `courses`, `books`, `staff-accounts`, `enrollments`) route through this one hook and component, so the change is bounded to roughly 8 frontend files — but it is real, and it is a UX change (Prev/Next-only navigation, since a cursor has no notion of "page 47"), not only a backend one.

Given that footprint, this item needs an explicit product decision — keep absolute page numbers (meaning `IP-03`'s count query can only be reduced, not eliminated) or move to Prev/Next-only navigation (meaning the count can be dropped, per `IP-03`'s coupling note) — before implementation starts. This spec does not make that call.

**Targets:** `BM-STU-004` (the headline H3 scenario), read against its own control `BM-STU-001`; also re-check `BM-BK-001`/`BM-STU-002/003`/`BM-CRS-001` since H1 and H3 compound on the same endpoints.

**Hypothesis & target metric:** `BM-STU-004`'s S3 p95 (3375 ms) should converge toward `BM-STU-001`'s (flat across scales) once the discarded-row cost is eliminated — the whole point of keyset pagination is that page depth stops being a cost variable at all.

**Verification:** re-run `BM-STU-004` and `BM-STU-001` at S1/S2/S3 and confirm the growth exponent for `BM-STU-004` drops from 0.34 toward flat; confirm via `EXPLAIN ANALYZE` that the query no longer generates and discards offset rows.

**Dependencies:** last item in [Phase 2](./07-improvement-roadmap.md#3-phase-2--search-endpoint-shape-fixes-h1--h3), sequenced after `IP-02`/`IP-03` land on the same repository classes, and gated on the product decision above. The largest cross-cutting footprint of any `IP-*` item in Phase 2 — budget accordingly.

---

## IP-06 — Fix H6's silent event-publication loss

**Source:** `06` §6 P1 #6; hazard H6, and the **one confirmed defect** in the benchmark set, not merely a slow path (`06` §3.5). `BM-XC-001` at N=200 found 568 of 801 `EVENT_PUBLICATION` rows still incomplete more than 30 minutes after a 200-student burst delete — permanently dropped, not delayed, because `ThreadPoolTaskExecutor` does not retry a rejected task and the executor backing `StudentDeleted`/`CourseDeleted` cascades runs on a 2–4 thread pool with a 50-slot queue (`shared/async/AsyncConfig.java:30-32`). `06` §3.5 is explicit that no student, book-ownership, or enrollment data is at risk — the `ON DELETE SET NULL`/`CASCADE` constraints (`V1__init_schema.sql:42,51,68`) are synchronous and independent of the async listener. What is wrong is Spring Modulith's own `event_publication` audit trail.

**Approach:** `06` §6 names three options without choosing between them: widen `AsyncConfig`'s pool/queue to absorb a realistic burst (e.g., sized for a 200-student delete), add retry/backoff for rejected `event_publication` tasks instead of silently dropping them, or bound the input by requiring bulk deletes above some size to be client-paginated. Whichever is chosen must be documented as the bound accepted, per `06` §6's own instruction — an implicit, unstated bound is exactly what produced this defect.

**Targets:** `BM-XC-001` at N=10, 50, and 200.

**Hypothesis & target metric:** zero rejected tasks and zero permanently-incomplete `EVENT_PUBLICATION` rows at N=200 within a bounded, stated wall-clock window (the window itself is a design choice the fix must state, not a preexisting SLO — no SLO class in `01-benchmark-strategy.md` §4.2 currently covers async cascade completion time).

**Verification:** re-run `BM-XC-001` at N=200, confirm 801/801 (or the equivalent count at whatever N is used) publications complete, and confirm the completion wall-clock time is recorded and bounded per the fix's own stated design.

**Dependencies:** none — independent subsystem (`shared/async`) from every other Phase 2–4 item. Runs in [Phase 4](./07-improvement-roadmap.md#5-phase-4--isolation--correctness-h6--h5-blast-radius), safely parallel with Phases 2 and 3.

---

## IP-07 — Raise `innodb_buffer_pool_size`

**Source:** `06` §6 P1 #7; hazard H1 (sharpens `BM-BK-001`'s S2→S3 cliff specifically). The 128 MB `innodb_buffer_pool_size` is the untouched container default; `06` §3.1 hypothesizes it is why the S2→S3 jump for `BM-BK-001` (2.59 s → 8.57 s) is sharper than the S1→S2 jump (2.28 s → 2.59 s) — the ~80,000-row S3 books table stops fitting in 128 MB and the scan starts hitting disk through Colima's VM layer.

**Approach:** raise `innodb_buffer_pool_size` in the MySQL container configuration (`management/`'s Docker Compose setup) to a size that fits at least the S2 dataset — `04-workload-data-preparation.md` §1 gives the row counts needed to size it. A Docker Compose change, not an application change.

**Targets:** the `BM-XC-004` scale sweep, specifically its `BM-BK-001` leg.

**Hypothesis & target metric:** currently `IP-02`/`IP-03`/`IP-07`'s effects on `BM-BK-001` are conflated — `06` §3.1 says so directly ("currently the two are conflated"). This item's job is to separate them: re-running the S1→S2→S3 sweep with a larger buffer pool but *before* `IP-02`/`IP-03` land should show whether the S2→S3 cliff softens on buffer-pool size alone, isolating how much of `BM-BK-001`'s worst-case cost is disk I/O versus scan shape.

**Verification:** re-run the `BM-XC-004` sweep for `BM-BK-001` with the enlarged buffer pool, compare the S2→S3 delta against the original 2.59 s → 8.57 s jump.

**Dependencies:** grouped with `IP-01` in [Phase 1](./07-improvement-roadmap.md#2-phase-1--foundation-config--tooling-no-application-code-risk) — same verification shape (config change, re-run a sweep, read the new curve), and resolving it before Phase 2 means `IP-02`/`IP-03`'s own before-numbers aren't still contaminated by a buffer-pool miss that has nothing to do with query shape.

---

## IP-08 — Isolate login bursts from the rest of the application

**Source:** `06` §6 P1 #8; hazard H5. `BM-JMH-001` puts a floor under BCrypt strength 10's per-call cost (90.79 ms/hash, 94.70 ms/verify — a stated security property, not a defect). `BM-IDN-001`'s concurrency ramp shows the knee: p95 170 ms at 1 VU → 823 ms at 10 VUs (+384%) → 1706 ms at 25 → 3124 ms at 50 → 6069 ms at 100 (`06` §3.4). Nothing in the current configuration isolates that degradation from the rest of the application's Tomcat thread pool.

**Approach:** `06` §6 names two options without choosing between them: a bulkhead (a dedicated, bounded thread pool for the auth endpoint, separate from Tomcat's general pool) or basic rate-limiting/backpressure (`429`) once a login queue passes a threshold. This does not touch BCrypt's work factor — `06` §3.4 and `01-benchmark-strategy.md` H5 are both explicit that the per-call cost is deliberate.

**Targets:** `BM-IDN-001` (the ramp itself), plus any concurrently-running scenario during a login burst — the `BM-XC-002` mixed-role soak is the natural vehicle for checking that other endpoints stop starving.

**Hypothesis & target metric:** `BM-IDN-001`'s own knee may not move (BCrypt is still BCrypt), but other endpoints' latency, sampled during a concurrent login burst, should no longer degrade together with login latency — the fix's target metric is the blast radius, not the login endpoint's own number.

**Verification:** re-run `BM-IDN-001`'s ramp while sampling a non-auth endpoint (e.g., `BM-STU-005`) concurrently; confirm the non-auth endpoint's p95 stays within its own SLO class regardless of login concurrency.

**Dependencies:** none — independent subsystem (auth endpoint / Tomcat configuration) from `IP-06`. Runs in [Phase 4](./07-improvement-roadmap.md#5-phase-4--isolation--correctness-h6--h5-blast-radius), safely parallel with `IP-06` and with Phases 2–3.

---

## IP-09 — Keep actuator + Micrometer permanently wired

**Status: already implemented, and exceeded.** Corrected in this v0.1 revision after verifying directly against the current codebase — the entry below originally described this as scheduled work; it is not.

**Source:** `06` §6 P1 #9; hazard H8. Server-side metrics (Hikari pool state, session counts) were what let the 2026-08-27 runs measure things the 2026-08-26 baselines could only guess at (`2026-08-27-S2-d891911-2.md` Finding F4) — `06` §6 asked for this to become a permanent fixture rather than one-off instrumentation for that session.

**What's actually in place:** `management/pom.xml` carries `spring-boot-starter-actuator` and `micrometer-registry-prometheus` as permanent dependencies, not benchmark-session scaffolding. `application-benchmark.properties` exposes `management.endpoints.web.exposure.include=health,metrics,prometheus` only under the `benchmark` Spring profile, served on its own embedded connector (`management.server.port=8081`) — a different mechanism than the single-port, property-toggled approach `01-benchmark-strategy.md` §8 originally sketched, and arguably a stronger one: the main port's `SecurityConfig.java:161-170` still gates `/actuator/**` behind `SYSTEM_ADMINISTRATOR` (permitting only `/actuator/health` unauthenticated for liveness tooling), while `:8081` is reachable without a session login specifically so Prometheus can scrape it, isolated from the application's own port entirely. No explicit `application-prod.properties` disable line exists — none is needed, since the `benchmark` profile (and its properties file) is simply never active in a `prod` boot, an opt-in-only safety equivalent to the opt-out hard-disable pattern PM-017 established for demo accounts, achieved a different way. On top of the metrics endpoint itself, `docs-v00/Benchmark/benchmark-strategy/06-dashboard-building.md` specifies a full six-dashboard Grafana/Prometheus stack (Benchmark Overview, HTTP & Load Testing, JVM Runtime, Spring Boot Runtime, MySQL Performance, Performance Correlation) built on top of exactly this metrics surface — well beyond what `06` §6's recommendation asked for.

**Targets:** none directly — this was always a prerequisite for attribution, not a hazard fix in its own right. It already benefits every other `IP-*` item's verification run.

**Outcome:** every future red run can reach rung 2 of the escalation ladder (`05-baseline-and-reporting.md` §4) today, without any ad hoc setup step. No further verification action is needed for this item — Phase 1's exit criterion (re-running the P0 read catalog) can rely on this observability immediately.

**Dependencies:** none remaining. No longer gates [Phase 1](./07-improvement-roadmap.md#2-phase-1--foundation-config--tooling-no-application-code-risk)'s exit criterion the way originally written — see that document's corrected Phase 1 section.

---

## IP-10 — Move sessions off-heap

**Source:** `06` §6 P2 #10; hazard H7. `SessionRegistryImpl` is heap-resident and `maximumSessions(SessionLimit.UNLIMITED)` (`shared/security/SecurityConfig.java:64`, `:125`) places no cap. The 30-minute soak measured roughly 1.7–2.3 MB per active session in the trustworthy early/mid-soak window (`06` §3.6, `2026-08-27-S2-d891911-2.md` Finding F5 — the late-soak 17.2 MB/session figures are a measurement artifact of a shrinking denominator, not a real cost). `01-system-overview.md` §5 already names in-memory sessions as the blocker for horizontal scaling.

**Approach:** Redis-backed Spring Session, sized using the ~1.7–2.3 MB/session figure above, per `06` §6's own suggestion. Not designed further here — `06` §6 is explicit this is not urgent without an active horizontal-scaling plan, and design effort ahead of that plan risks designing against the wrong constraints.

**Targets:** `BM-XC-002` (the soak that produces the per-session heap figure) and `BM-IDN-004` (the session-registry read).

**Hypothesis & target metric:** deferred — no target metric until a horizontal-scaling plan exists to define what "enough" sessions off-heap looks like.

**Verification:** deferred.

**Dependencies:** none scheduled — [Phase 5](./07-improvement-roadmap.md#6-phase-5--tracked-not-scheduled-p2-long-horizon), tracked only. Promotion trigger: the existence of a horizontal-scaling plan, at which point this item leaves this phase and is sequenced by that plan.

---

## IP-11 — Fix the benchmark harness's VU-sharding collision

**Source:** `06` §6 P2 #11; a benchmark-harness bug, not an application defect (`06` §5). `BM-STU-007` (student update) shows a 46.96% error rate in both 2026-08-27 records because `bench/lib/vuShard.js`'s per-VU sharding does not fully isolate row targets across scenario stages under k6 v2.2.0's actual VU numbering — some concurrent `PUT`s land on the same student row and correctly receive `409 Conflict` from the application's optimistic locking. Direct reproduction outside k6 confirms the locking itself is correct (4×409 + 1×200 for 5 genuinely concurrent writers on one row).

**Approach:** fix `bench/lib/vuShard.js`'s sharding logic so it isolates row targets correctly across scenario stages under k6 v2.2.0's VU numbering. Harness code only — `management/` is untouched by this item.

**Targets:** `BM-STU-007` — this is the only scenario this item affects.

**Hypothesis & target metric:** `BM-STU-007`'s error rate drops to within the standard < 0.1% threshold (`05-baseline-and-reporting.md` §1), making it usable as a Write-simple regression baseline for the first time.

**Verification:** re-run `BM-STU-007` at S2 and confirm the error rate is below 0.1%; only then can its latency numbers be used as a baseline per `05` §1's acceptance conditions.

**Dependencies:** none — grouped into [Phase 1](./07-improvement-roadmap.md#2-phase-1--foundation-config--tooling-no-application-code-risk) alongside `IP-01`/`IP-07` since it is tooling-only and carries no application-code risk, the same reason those two are grouped there.

---

## Out of Scope (this document)

- Sequencing, phasing, and cross-item dependency rationale — see [`07-improvement-roadmap.md`](./07-improvement-roadmap.md).
- Why each hazard matters, the SLO classes, and the run evidence each hypothesis above is measured against — see `docs-v00/Benchmark/`, especially [`01-benchmark-strategy.md`](../../docs-v00/Benchmark/benchmark-strategy/01-benchmark-strategy.md), [`05-baseline-and-reporting.md`](../../docs-v00/Benchmark/benchmark-strategy/05-baseline-and-reporting.md), and [`06-conclusions-and-recommendations.md`](../../docs-v00/Benchmark/06-conclusions-and-recommendations.md).
- Filing the GitHub issue each item requires before implementation — not performed by this version (see [`README.md`](./README.md), "Non-goals").
