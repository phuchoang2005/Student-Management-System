# Benchmark Scenarios

Benchmark Documentation — Part 3 of 5 ([Benchmark Strategy](./01-benchmark-strategy.md) → [Benchmark Plan](./02-benchmark-plan.md) → Scenarios → [Workload Data Preparation](./04-workload-data-preparation.md) → [Baseline & Reporting](./05-baseline-and-reporting.md)).

The scenario catalog. Each entry is one measurable question, addressed to one endpoint, built to expose one of the hazards in [01-benchmark-strategy.md](./01-benchmark-strategy.md) §3, and judged against one SLO class from §4.2 of that document.

Scenario IDs follow **`BM-<MODULE>-<NNN>`**, deliberately mirroring the `TC-<MODULE>-<NNN>` convention in [`Testing/03-test-cases/`](../Testing/03-test-cases/). Module codes: `STU` student, `BK` book, `CRS` course, `ENR` enrollment, `IDN` identity/auth, `ME` own-records, `XC` cross-cutting, `JMH` microbenchmark.

This is documentation only — no k6 code is included here.

---

## 1. How to read a scenario

| Column | Meaning |
| --- | --- |
| **BM ID** | Stable identifier. Cited by run records, findings, and issues. |
| **Endpoint** | The path and method exercised, as fixed by `api-specification.md`. |
| **Workload shape** | VU count, request mix, and the parameters that matter (page size, page depth, batch size). |
| **Hazard** | Which of H1–H8 this scenario is built to expose. `—` means it is a control. |
| **UC** | The use case the endpoint implements, per [`BA-docs/use-cases.md`](../BA-docs/use-cases.md). |
| **SLO** | The class from `01-benchmark-strategy.md` §4.2 the result is judged against. |

Every scenario authenticates once per VU as the role its endpoint requires (`02-benchmark-plan.md` §1.1) — the RBAC allow-list is explicit per method and path, and a 403 measured at full speed is indistinguishable from a fast endpoint.

Unless a scenario says otherwise, it runs at **20 VUs**, at **scale S2**, for the 300 s steady-state window in `02-benchmark-plan.md` §2.

---

## 2. Student module

Role: `REGISTRAR` (except `BM-STU-006`).

| BM ID | Endpoint | Workload shape | Hazard | UC | SLO | Question it answers |
| --- | --- | --- | --- | --- | --- | --- |
| **BM-STU-001** | `GET /api/v1/students?query=&page=0&size=20` | No search term, first page. 20 VUs. | H3 | UC-13 | Read-list | The floor: paging with no filter, at the shallowest depth. Everything else in this module is read against it. |
| **BM-STU-002** | `GET /api/v1/students?query={term}&page=0&size=20` | Terms drawn from a fixed pool with a known hit distribution — some matching many rows, some matching one, some matching none. | **H1** | UC-13 | Read-list | What a leading-wildcard scan across 4 columns costs, and whether the cost tracks result count or table size. *It should track table size* — that is the finding. |
| **BM-STU-003** | `GET /api/v1/students?query={term}&page=0&size=100` | Same as BM-STU-002 at maximum page size. | H1 | UC-13 | Read-list | Whether page size or scan cost dominates. Isolates the row-materialization cost from the scan cost. |
| **BM-STU-004** | `GET /api/v1/students?page={deep}&size=20` | Page index drawn from the last decile of available pages. | **H3** | UC-13 | Read-list | The `OFFSET` walk. Compare directly against BM-STU-001 — same query, same page size, only the depth differs, so the delta *is* the offset cost. |
| **BM-STU-005** | `GET /api/v1/students/{code}` | Codes drawn uniformly from the seeded set. | — | UC-14 | Read-single | The control. A unique-index lookup should be flat across all scales; if this degrades, the problem is infrastructural, not query-shaped, and every other result in the run is suspect. |
| **BM-STU-006** | `POST /api/v1/students` | 5 VUs only. Each iteration registers a new student with a generated unique code and email. | **H5** | UC-1 | Write-simple | Registration cost, which includes a BCrypt hash *and* an AES-GCM encryption of the initial password. Low VU count deliberately: this scenario mutates the dataset, and at high concurrency it would compete with itself for CPU. Cross-reference the split against `BM-JMH-001` and `BM-JMH-002`. |
| **BM-STU-007** | `PUT /api/v1/students/{code}` | 10 VUs, updating distinct students to avoid manufactured lock contention. | — | UC-2 | Write-simple | Baseline write cost on a versioned aggregate — the optimistic-locking read-modify-write, without the identity work BM-STU-006 carries. |

---

## 3. Book module

Role: `LIBRARIAN`.

| BM ID | Endpoint | Workload shape | Hazard | UC | SLO | Question it answers |
| --- | --- | --- | --- | --- | --- | --- |
| **BM-BK-001** | `GET /api/v1/books?query={term}&page=0&size=20` | Terms across ISBN, title, and author. | **H1** | UC-15 | Read-list | The same scan hazard as BM-STU-002 on a table that is expected to be larger. Confirms H1 is a shape problem, not a students-table problem. |
| **BM-BK-002** | `GET /api/v1/books?ownerStudentCode={code}&page=0&size=20` | Owners drawn from the skewed ownership distribution (`04` §2). | — | UC-15 | Read-list | The filtered path, which resolves the owner to an id and uses the FK index. Should be markedly faster than BM-BK-001 — and if it is not, the owner filter is not doing what it looks like it does. |
| **BM-BK-003** | `GET /api/v1/books?page=0&size=100` | Unfiltered, maximum page size, drawing books with many distinct owners. | — | UC-15 | Read-list | **A deliberate non-hazard check.** `BookService` memoizes owner lookups per page (`01` §3.1), so a page of 100 books owned by 100 different students should cost ~100 lookups while a page owned by 5 should cost ~5. This scenario exists to confirm the memo works as documented, and to catch it silently regressing later. |
| **BM-BK-004** | `GET /api/v1/books/{isbn}` | ISBNs drawn uniformly. | — | UC-16 | Read-single | Control, as BM-STU-005. |
| **BM-BK-005** | `PATCH /api/v1/books/{isbn}/owner` | 10 VUs, distinct books. | — | UC-7 | Write-simple | Assignment write cost — one cross-module `StudentLookup.idOf` plus one versioned update. |

---

## 4. Course module

Role: `COURSE_ADMINISTRATOR`.

| BM ID | Endpoint | Workload shape | Hazard | UC | SLO | Question it answers |
| --- | --- | --- | --- | --- | --- | --- |
| **BM-CRS-001** | `GET /api/v1/courses?query={term}&page=0&size=20` | Terms across course code and name. | **H1** | UC-17 | Read-list | H1 on the smallest of the three tables. The expected result — that this is the least affected — is what establishes that H1 scales with table size rather than being a constant per-query cost. |
| **BM-CRS-002** | `GET /api/v1/courses?page=0&size=20` | Unfiltered listing. | — | UC-17 | Read-list | The course list carries an enrolled-student count per row, resolved by a single grouped `LEFT JOIN` over the page's codes (`course/internal/SpringDataCourseRepository.java:47-53`) rather than one query per row. Measures whether that join stays cheap as `enrollments` grows — it is the one place a *set-based* alternative to N+1 already exists, so it is the reference point for what H2 could look like fixed. |
| **BM-CRS-003** | `GET /api/v1/courses/{code}` | Codes drawn from the skewed enrollment distribution — deliberately including the most-enrolled course. | — | UC-18 | Read-single | Detail read includes a `COUNT(*)` over that course's enrollments. For a course with 5,000 enrollments this is not a constant-time read, and the skewed distribution is what surfaces it. |
| **BM-CRS-004** | `DELETE /api/v1/courses/{code}` | 5 VUs. Requires reseeding after; run last in this module. | H6 | UC-10 | Write-simple | Delete latency *as the client sees it* — which excludes the async cascade. Pairs with `BM-XC-001`, which measures the part this one cannot see. |

---

## 5. Enrollment module

Role: `REGISTRAR`.

| BM ID | Endpoint | Workload shape | Hazard | UC | SLO | Question it answers |
| --- | --- | --- | --- | --- | --- | --- |
| **BM-ENR-001** | `GET /api/v1/enrollments?studentCode={code}&size=20` | Students drawn from the enrollment-count distribution. | **H2** | UC-20 | Read-list | The N+1. Each row resolves its own course through `CourseLookup.summaryOf` (`EnrollmentService.java:179`), so a 20-row page should cost ~21 queries. Confirm the count against the `performance_schema` digest, not by inference. |
| **BM-ENR-002** | `GET /api/v1/enrollments?studentCode={code}&size=100` | Same, at maximum page size. | **H2** | UC-20 | Read-list | **The headline H2 measurement.** Compared against BM-ENR-001, latency should grow roughly 5× if the N+1 dominates and far less if it does not. This single comparison is what turns H2 from a code-reading observation into a number. |
| **BM-ENR-003** | `GET /api/v1/enrollments?courseCode={code}&size=100` | Courses drawn including the most-enrolled. | **H2** | UC-11 | Read-list | The mirror image: filtering by course makes the *course* constant and the *student* per-row (`EnrollmentService.java:189`). Should cost the same as BM-ENR-002 — and if the two differ, the difference is the relative cost of a student lookup versus a course lookup, which is itself worth knowing. |
| **BM-ENR-004** | `GET /api/v1/enrollments/{studentCode}/{courseCode}` | Pairs drawn from existing enrollments. | — | UC-20 | Read-single | Control. Two lookups plus the row; no N+1 possible at one row. |
| **BM-ENR-005** | `POST /api/v1/enrollments` | 10 VUs, unique student/course pairs. | — | UC-11 | Write-simple | Single-enrollment cost: `idOf` + `existsByCode` + `existsByStudentAndCourse` + `INSERT`, one transaction. **The unit that BM-ENR-006/007/008 multiply.** |
| **BM-ENR-006** | `POST /api/v1/enrollments/batch` — 1 course | 5 VUs. | H4 | UC-26 | Write-simple | The degenerate batch. Should equal BM-ENR-005 plus the batch endpoint's fixed overhead; the difference is that overhead. |
| **BM-ENR-007** | `POST /api/v1/enrollments/batch` — 10 courses | 5 VUs. | **H4** | UC-26 | Batch-50 | Midpoint of the curve. |
| **BM-ENR-008** | `POST /api/v1/enrollments/batch` — 50 courses | 5 VUs, the maximum the request cap allows (`BatchEnrollmentRequest.java:20`). | **H4** | UC-26 | Batch-50 | The worst case by design: ~200 statements and 50 commits in one request. Plotted against BM-ENR-006 and BM-ENR-007, this answers whether cost is linear in batch size (expected) or worse (would be a finding). **Not an argument against the design** — partial-success durability is deliberate (`api-specification.md` §5 decision #12); the number is what tells a client where to split a large request. |

---

## 6. Identity & authentication

| BM ID | Endpoint | Workload shape | Hazard | UC | SLO | Question it answers |
| --- | --- | --- | --- | --- | --- | --- |
| **BM-IDN-001** | `POST /api/v1/auth/login` | Ramp 1 → 10 → 25 → 50 → 100 VUs, holding each step. **Runs alone** — nothing else in the run. | **H5** | UC-21 | Login | The saturation curve. BCrypt at strength 10 is CPU-bound by design, so throughput should plateau at roughly (cores ÷ per-hash cost) and latency should then grow linearly with offered load. **The deliverable is the knee of that curve** — the concurrency at which a login burst starts queueing — because that is the number that says whether a start-of-term morning is a problem. |
| **BM-IDN-002** | `POST /api/v1/auth/login` — wrong password | 20 VUs, all failing. | H5 | UC-21 | Login | A failed login must cost the same as a successful one; Spring Security verifies against the stored hash either way. If failures are measurably cheaper, that timing difference is a user-enumeration signal — **a security finding surfaced by a performance scenario**, and worth an issue on those grounds alone. |
| **BM-IDN-003** | `GET /api/v1/staff-accounts?page=0&size=20` | 10 VUs as `SYSTEM_ADMINISTRATOR`. | — | UC-25 | Read-list | Small-table paged read with a role filter. Control for the list-endpoint shape on a table that never grows large. |
| **BM-IDN-004** | `GET /api/v1/sessions` | 10 VUs, run during the BM-XC-002 soak while many sessions are live. | **H7** | UC-27 | Read-list | Reads a snapshot of the in-memory `SessionRegistry`. Unlike every other read here it touches no database — its cost tracks *live session count*, so it should be measured while that count is high, and it is the cheapest available probe of H7. |

---

## 7. Own-records (`me`)

Role: `STUDENT`, authenticated as an account from the provisioned cohort (`04` §4.2).

| BM ID | Endpoint | Workload shape | Hazard | UC | SLO | Question it answers |
| --- | --- | --- | --- | --- | --- | --- |
| **BM-ME-001** | `GET /api/v1/me/profile` | 20 VUs, distinct student accounts. | — | UC-19 | Read-single | The lightest authenticated read in the system: principal → one row. Establishes the fixed cost of the security filter chain plus session resolution, which every other scenario's number silently includes. |
| **BM-ME-002** | `GET /api/v1/me/courses?size=20` | 20 VUs, students drawn from the enrollment-count distribution. | **H2** | UC-19 | Read-list | H2 on the student-facing path (`EnrollmentService.java:200`). Matters more than BM-ENR-001 in one respect: staff endpoints are used by tens of people, this one is used by *every student*, so its concurrency ceiling is the population, not the payroll. |
| **BM-ME-003** | `GET /api/v1/me/books?size=20` | 20 VUs. | — | UC-19 | Read-list | The owner-filtered book read, which goes through the memoized path. Contrast with BM-ME-002: same shape, same page size, one has an N+1 and the other does not. **The clearest single before/after illustration of what fixing H2 would buy.** |

---

## 8. Cross-cutting

| BM ID | Scenario | Workload shape | Hazard | UC | SLO | Question it answers |
| --- | --- | --- | --- | --- | --- | --- |
| **BM-XC-001** | Bulk student deletion, cascade completion | Delete N students (N = 10, 50, 200) as fast as the API accepts them. Measure **two** numbers: HTTP response latency, and the wall-clock time until every corresponding row in `event_publication` is marked complete. | **H6** | UC-3 | Write-simple (HTTP only) | The HTTP 204 returns before the cascade runs — by design (`02-component-diagram.md` §2.3). So the client-side number is *not* the cost of a delete; the gap between the two numbers is. With a core-2/max-4 pool and a 50-slot queue (`AsyncConfig.java:30-32`), the queue should visibly saturate somewhere in this range, and **the interesting result is whether any task is rejected** — a rejection leaves publications unresolved, which the DB-level `ON DELETE` net covers for data but not for the event log. |
| **BM-XC-002** | Mixed-role soak | 30 min at moderate, constant load. Realistic mix: ~70% reads, ~20% writes, ~10% logins, split across REGISTRAR / LIBRARIAN / COURSE_ADMINISTRATOR / STUDENT roughly in proportion to how the sidebar's permission list distributes work. | **H7**, H6 | — | per-class | The only scenario that runs long enough for slow problems to appear: heap growth per active session, GC behavior over time, connection-pool stability, and whether latency drifts. **Deliverable: heap bytes per active session**, which is the number that turns `01-system-overview.md` §5's qualitative "in-memory sessions block horizontal scaling" into something actionable. |
| **BM-XC-003** | Connection-pool saturation | One P0 read scenario (BM-ENR-002 is the best candidate — it makes the most queries per request) at VU counts spanning the default Hikari pool size of 10: 5, 10, 20, 40. | H2, H5 | — | Read-list | Where the pool becomes the constraint. Because H2 makes one request occupy a connection for many queries, the pool should saturate at a *lower* VU count here than for a single-query endpoint — which reframes H2 as a concurrency problem rather than only a latency one, and is the strongest argument the benchmark can make for fixing it. |
| **BM-XC-004** | Scale sweep | Every P0 scenario (BM-STU-002, BM-STU-004, BM-BK-001, BM-CRS-001, BM-ENR-002, BM-ME-002), run identically at S1, S2, and S3. | H1, H2, H3 | — | Read-list | **The single most valuable artifact this set produces.** Not a latency number but a *curve*: for each scenario, is cost flat, linear in row count, or worse? A flat curve retires a hazard. A linear one prices it. Given the parity caveat in `01` §7.1, this comparison is also the most trustworthy thing here, because the host noise applies equally to all three points. |

---

## 9. JMH microbenchmark catalog

Method-level, no server, no database. Scope and guardrail are fixed by `01-benchmark-strategy.md` §5.1 — restated here because it governs how these results may be used:

> **A JMH result may never justify a code change on its own.** It may only support a change whose effect is also visible in a `BM-*` scenario above.

| BM ID | Target | Parameters | Hazard | Question it answers |
| --- | --- | --- | --- | --- |
| **BM-JMH-001** | `BCryptPasswordEncoder.encode` / `.matches` | Strength 4 through 14 | **H5** | The per-strength cost curve on this hardware. Strength 10 is inherited from the no-arg constructor (`SecurityConfig.java:190`) — a framework default, never a measured choice. This curve makes the security/latency trade-off explicit and sets the floor under the Login SLO and under BM-IDN-001's knee. |
| **BM-JMH-002** | `AesPasswordCipher.encrypt` / `.decrypt` | As-written vs. a variant reusing the `Cipher` instance | H5 | What the per-call `Cipher.getInstance(TRANSFORMATION)` (`AesPasswordCipher.java:51`, `:68`) costs in JCE provider lookup. Runs on the registration path next to BCrypt, so the honest expectation is that it is dwarfed — **but a variant is benchmarked so the claim is measured rather than assumed.** |
| **BM-JMH-003** | Value-object construction: `Email`, `StudentCode`, `Isbn`, `Credits` | Valid and invalid inputs | — | **Expected result: statistical noise.** `Email`'s `Pattern` is already `static final` (`student/domain/Email.java:13`), so nothing is recompiled per instance. This benchmark exists to *prove* domain validation is free, so nobody optimizes a nanosecond path — a null result here is the successful outcome, and it should be recorded as such rather than discarded. |
| **BM-JMH-004** | MapStruct mappers — `StudentMapper`, `EnrollmentMapper` page mapping | 20 and 100 rows | H2 | Bounds the non-I/O share of a list response. Whenever BM-ENR-002 looks slow, "it is the mapping, not the queries" is the alternative hypothesis; this is what rules it out. |

---

## 10. Coverage

Every hazard has at least one scenario, and every scenario names a hazard or is explicitly a control.

| Hazard | Scenarios |
| --- | --- |
| **H1** — unindexable search | BM-STU-002, BM-STU-003, BM-BK-001, BM-CRS-001, BM-XC-004 |
| **H2** — enrollment N+1 | BM-ENR-001, BM-ENR-002, BM-ENR-003, BM-ME-002, BM-XC-003, BM-XC-004, BM-JMH-004 |
| **H3** — deep `OFFSET` | BM-STU-004, BM-STU-001 (control), BM-XC-004 |
| **H4** — batch transactions | BM-ENR-006, BM-ENR-007, BM-ENR-008, BM-ENR-005 (unit) |
| **H5** — BCrypt-bound login | BM-IDN-001, BM-IDN-002, BM-STU-006, BM-JMH-001, BM-JMH-002 |
| **H6** — async pool bounds | BM-XC-001, BM-XC-002, BM-CRS-004 |
| **H7** — unbounded sessions | BM-XC-002, BM-IDN-004 |
| **H8** — no observability | Not a scenario. Step 0 of `02-benchmark-plan.md` §3 — a prerequisite for attributing all of the above. |
| **Controls** (no hazard, by design) | BM-STU-005, BM-STU-007, BM-BK-002, BM-BK-003, BM-BK-004, BM-BK-005, BM-CRS-002, BM-CRS-003, BM-ENR-004, BM-IDN-003, BM-ME-001, BM-ME-003 |
