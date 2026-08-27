# Benchmark Strategy

Benchmark Documentation — Part 1 of 5 (Benchmark Strategy → [Benchmark Plan](./02-benchmark-plan.md) → [Scenarios](./03-benchmark-scenarios.md) → [Workload Data Preparation](./04-workload-data-preparation.md) → [Baseline & Reporting](./05-baseline-and-reporting.md)).

Derived from the implemented system in `management/`, and from the specification set it was built against (`docs/BA-docs/`, `docs/SA-docs/`, `docs/Testing/`). This document answers _what kind of performance measurement this system needs, why it needs it now, and what "fast enough" means_ — not the concrete run schedule, scenario list, or datasets, which are the subject of [02-benchmark-plan.md](./02-benchmark-plan.md), [03-benchmark-scenarios.md](./03-benchmark-scenarios.md), and [04-workload-data-preparation.md](./04-workload-data-preparation.md).

Written **after** implementation, unlike every other doc set in this repository. That ordering is the point: the hazards in §3 are not speculative risks a designer imagined, they are properties of code that now exists and can be pointed at by file and line.

---

## 1. Purpose & Scope

### 1.1 Purpose

Establish the first performance baseline for the Student Management System — a single-process Spring Boot REST API (5 Spring Modulith business modules plus `shared` and `me`) over one MySQL 8 schema — and define the standing practice by which that baseline is re-measured, compared, and defended against regression.

The system has never been measured. It has also never had a stated performance requirement (§2.2). Both gaps are closed here: §5 proposes the first Service Level Objectives, and §3 enumerates the specific code paths whose cost is expected to grow with data volume.

### 1.2 In Scope

- **HTTP-level load** against the running API — every endpoint class in `api-specification.md`, exercised through the real Spring Security filter chain with a real `JSESSIONID` session, against a real MySQL instance.
- **Latency, throughput, error rate, and saturation** at four dataset scales (`04-workload-data-preparation.md` §1), because the question this set exists to answer is not "how fast is it" but **"what shape is the curve as data grows."**
- **Database-side attribution** — `EXPLAIN ANALYZE`, the MySQL slow query log, and `performance_schema` statement digests — so a slow endpoint can be traced to the statement responsible rather than guessed at.
- **The eight named hazards in §3**, each with at least one scenario in `03-benchmark-scenarios.md` built to measure it.
- **A narrow set of JMH microbenchmarks** (§7) covering the four places in this codebase where work is genuinely CPU-bound rather than I/O-bound.
- **Regression detection** — the comparison protocol and thresholds in [05-baseline-and-reporting.md](./05-baseline-and-reporting.md).

### 1.3 Out of Scope

| Item | Reason |
| --- | --- |
| Frontend performance (Web Vitals, bundle budgets, Lighthouse) | `management-frontend/` is a client-rendered SPA that proxies to this API (`management-frontend/README.md`); it has no server-side render path under load. Its perceived latency is this API's latency plus a fixed client cost, so measuring the API measures the part that varies. A separate, lightweight front-end practice is worth having later; it is not this document. |
| Multi-node / clustered / horizontally-scaled load | Ruled out by the deployment the system actually describes: one process, no clustering, in-memory sessions (`01-system-overview.md` §5). Benchmarking a topology that does not exist would produce numbers nothing could act on. H7 (§3) measures the thing that *blocks* that topology instead. |
| Chaos / fault injection / failure-mode testing | A different discipline with a different goal (resilience, not throughput). Nothing in the system's design — no retries, no circuit breakers, no external integrations (`01-system-overview.md` §5) — currently has failure behavior worth injecting into. |
| Capacity planning / sizing for a real population | There is no real population, and no projected one. Sizing recommendations derived from invented demand would be false precision. This set produces a *curve*; whoever has real demand can read the sizing off it. |
| Security / rate-limit / DoS-resistance testing | Rate limiting is explicitly out of scope of the design (`api-specification.md` §6). Load testing an unprotected endpoint proves only that it is unprotected, which is already documented. |
| MySQL server tuning as a deliverable | The benchmark *records* the server configuration that produced each result (`05-baseline-and-reporting.md` §3) and holds it fixed for comparability. Choosing a production MySQL configuration is an ops exercise on real hardware, not a finding this set can produce from a laptop. |

---

## 2. Why Benchmark Now

### 2.1 This reverses a documented decision — deliberately, and with a reason

[`Testing/01-test-strategy.md`](../Testing/01-test-strategy.md) §1.3 places "Load / stress / scalability testing beyond a basic smoke check" **out of scope**, reasoning that `01-system-overview.md` §5 "fixes a single-process, no-clustering deployment with no stated throughput target; a full performance test program is not justified until real usage patterns emerge."

That was the right call **when it was made** — before implementation, when the only thing to reason about was a specification, and the honest answer to "how will this perform" was "there is nothing yet to measure."

Two things have changed:

1. **The system exists.** All of UC-1–28 are implemented and covered by automated tests (`Testing/README.md`). Performance is no longer a property of a design to be predicted; it is a property of code to be observed.
2. **The code contains specific, enumerable hazards.** §3 lists eight. None were introduced carelessly — several are documented trade-offs whose Javadoc explains the reasoning. But every one of them is a path whose cost grows with data volume, and **none has ever been measured at a volume where that growth is visible.** The seed dataset (`Testing/04-test-data-preparation.md`) is a handful of rows per table; at that size a full table scan and an indexed lookup are indistinguishable.

The exclusion in `Testing/01` §1.3 therefore remains correct **as written, for functional testing** — that document is still the source of truth for what the test suite covers, and this set adds no functional test cases. What this set does is take the deferred question ("until real usage patterns emerge") and answer the part that does not require real users: *what does this code do when the tables are large?* Measuring it costs a few hours now and is the cheapest it will ever be.

### 2.2 There are no non-functional requirements to test against

A search across `BA-docs/req.md`, `SA-docs/01-system-overview.md`, and `SA-docs/api-specification.md` for latency, throughput, response-time, concurrency, or scalability requirements returns **nothing**. `01-system-overview.md` §5 describes deployment *characteristics* (one process, one connection pool, in-memory sessions) but states no target for any of them.

This has a direct consequence for how §5 must be read: **every SLO in this document is a proposal**, derived from what a registrar's office plausibly looks like, not a measured requirement and not an agreed one. They exist so that a benchmark run can produce a verdict instead of an uninterpretable number. They should be revised the moment anyone has better information — and revising them is a documentation change, not a failure.

---

## 3. Hazard Register

The evidence base for the whole set. Each hazard has an ID used throughout [03-benchmark-scenarios.md](./03-benchmark-scenarios.md), and each is verifiable at the cited location.

| ID | Hazard | Why it grows | Evidence |
| --- | --- | --- | --- |
| **H1** | **Unindexable search.** Every list endpoint filters with `LIKE CONCAT('%', :query, '%')` across three or four columns. A leading wildcard defeats any B-tree index, so each search is a full table scan — and because the paged read and its `COUNT(*)` are two separate statements, **each search request scans the table twice.** | Linear in table size, ×2 per request | `student/internal/SpringDataStudentRepository.java:23-44`, `book/internal/SpringDataBookRepository.java:30-49`, `course/internal/SpringDataCourseRepository.java:21-36` |
| **H2** | **N+1 on enrollment listing.** `EnrollmentService.search` resolves each row's course through `courseLookup.summaryOf(e.courseCode())` inside the `map` — one query per row. `spring.data.web.pageable.max-page-size=100` makes the worst case 100 extra round trips for a single page. `findByStudent`, which backs `GET /api/v1/me/courses`, has the same shape. This is a **half-optimized** path, not an oversight: the method's Javadoc explains that the *constant* side of the page (the one student, or the one course, that every row shares) is deliberately hoisted out of the `map` and resolved once. Only the varying side remains per-row. | Linear in page size | `enrollment/application/EnrollmentService.java:179`, `:189`, `:200` |
| **H3** | **Deep `OFFSET` paging.** Paging is `LIMIT :limit OFFSET :offset`, which MySQL serves by generating and discarding `offset` rows before returning the first one the client wanted. Page 1 is cheap and page 500 is not, and the difference is invisible at any dataset small enough to fit on one page. Compounds H1: the discarded rows come out of a scan. | Linear in page number | the three `search` methods cited under H1 |
| **H4** | **Batch enrollment is N transactions.** `POST /api/v1/enrollments/batch` accepts up to 50 course codes and, by design, commits each one separately — `EnrollmentBatchService` is its own bean precisely so each call crosses the proxy and opens its own transaction. Per course that is `idOf` + `existsByCode` + `existsByStudentAndCourse` + `INSERT` + commit, so one HTTP request can cost ~200 statements and 50 commits. **This is deliberate** (partial success must survive a rejected sibling — `api-specification.md` §5 decision #12), and the cap of 50 already reflects an awareness of the cost. The benchmark's job is to put a number on it, not to argue with it. | Linear in batch size | `enrollment/application/EnrollmentBatchService.java:53-95`; cap at `enrollment/web/dto/BatchEnrollmentRequest.java:20` |
| **H5** | **Login is CPU-bound on BCrypt.** `passwordEncoder()` returns `new BCryptPasswordEncoder()` — strength 10, which is deliberately expensive (tens of milliseconds of pure CPU per verification, by design). Student registration pays it again on the hash side, plus an AES-GCM encryption of the generated initial password. Neither the Tomcat thread pool nor the HikariCP pool is tuned in `application.properties`, so both sit at framework defaults. A start-of-term login burst is this system's realistic saturation event, and it saturates CPU rather than the database. **The cost is a security property, not a defect** — the benchmark sizes the burst, it does not propose lowering the work factor. | Linear in concurrent logins, CPU-bound | `shared/security/SecurityConfig.java:189-191`; `identity/internal/AesPasswordCipher.java:51`, `:68`; `management/src/main/resources/application.properties` (no pool/thread tuning) |
| **H6** | **Cascade cleanup runs on a 2–4 thread pool with a 50-slot queue.** Every `StudentDeleted` / `CourseDeleted` cascade dispatches to the `taskExecutor` in `AsyncConfig`, and Spring Modulith persists each publication to `event_publication` (`V4__add_event_publication_table.sql`) before the listener runs. A bulk-delete burst — plausible at end of term — can outrun both: the queue fills, `ThreadPoolTaskExecutor` rejects, and publications are left unresolved in the registry. The DB-level `ON DELETE` constraints are the safety net for the data, but the *latency* between the HTTP 204 and the cascade actually completing is unmeasured. | Bounded pool vs. unbounded burst | `shared/async/AsyncConfig.java:30-32` |
| **H7** | **Sessions are heap-resident and unbounded.** `SessionRegistryImpl` is an in-memory bean and `maximumSessions(SessionLimit.UNLIMITED)` places no cap on concurrent sessions per user. `01-system-overview.md` §5 already names the in-memory session store as the thing that must be replaced before horizontal scaling. The benchmark can turn that qualitative note into a number: heap bytes per active session, and therefore how many sessions one process holds before it matters. | Linear in active sessions | `shared/security/SecurityConfig.java:64`, `:125` |
| **H8** | **The system is unobservable.** `management/pom.xml` declares no `spring-boot-starter-actuator` and no Micrometer registry. Every measurement is therefore black-box client-side latency, with no way to attribute it — a slow p99 could be connection-pool wait, GC pause, a lock, or the query, and nothing in the running process will say which. This hazard is different in kind from the others: it does not degrade the system, it degrades the benchmark. | Constant, but blocks diagnosis of all the above | `management/pom.xml` |

### 3.1 Two things that look like hazards and are not

Stated explicitly, because this repository's documents justify their negatives and because both are the kind of thing a reader skimming the schema would "fix":

- **`BookService.search` is not an instance of H2.** It resolves owner codes through a `Map<Long, String>` memo held for the page (`book/application/BookService.java:177`, `:232`), so a page of 20 books owned by 3 students costs 3 lookups, not 20 — and an owner-filtered page costs exactly one by construction. It already does what H2 describes as missing.
- **`books.owner_id` and `enrollments.course_id` are not missing indexes.** Neither has an explicit `CREATE INDEX` in `V1__init_schema.sql`, but both are foreign key columns, and InnoDB requires — and silently creates — an index on the referencing column of every foreign key constraint. They are indexed by side effect. Adding a redundant index would cost write throughput and buy nothing.

---

## 4. What Gets Measured

### 4.1 Metrics

| Metric | Definition | Why |
| --- | --- | --- |
| **Latency percentiles** — p50, p95, p99 | Server response time as observed by the load driver, per scenario | The distribution is the signal. A mean hides exactly the tail that users notice. |
| **Throughput** | Successful requests per second, **always reported with the concurrency that produced it** | Throughput without concurrency is not a number, it is two numbers with one missing. |
| **Error rate** | Non-2xx/3xx responses ÷ total, per scenario | Latency measured across a run that was quietly 40% 500s is a fiction. |
| **Saturation signals** | HikariCP pool wait time and active connections; Tomcat busy threads; GC pause time and frequency; MySQL `rows_examined ÷ rows_sent` per digest | These are what turn "it got slow" into "it got slow *because*." Most require H8 to be closed first. |

**Standing rule: report p95 and p99, never averages, and never a percentile without its concurrency level.** A run's headline number is `p95 @ N VUs @ scale S`; anything less is not comparable to another run.

### 4.2 Proposed Service Level Objectives

> **These are proposals, not requirements.** No performance requirement exists anywhere in `BA-docs/` or `SA-docs/` (§2.2). The targets below are derived from what the domain plausibly demands — a registrar's office serving hundreds to a few thousand students, with tens of staff working concurrently — and from what the endpoint shapes cost. They exist so a run can produce a verdict. Revise them freely once anyone has better information; that is a documentation change, not a failure.

| Class | Endpoint shape | p95 | p99 |
| --- | --- | --- | --- |
| **Read-single** | `GET` by business key (`/students/{code}`, `/books/{isbn}`, `/courses/{code}`) | ≤ 50 ms | ≤ 120 ms |
| **Read-list** | `GET` search, page 1, size 20 | ≤ 150 ms | ≤ 300 ms |
| **Write-simple** | `POST` / `PUT` / `PATCH` / `DELETE` on one aggregate | ≤ 200 ms | ≤ 400 ms |
| **Login** | `POST /api/v1/auth/login` | ≤ 400 ms | ≤ 800 ms |
| **Batch-50** | `POST /api/v1/enrollments/batch`, 50 courses | ≤ 2 s | ≤ 4 s |

Login and Batch-50 get their own classes rather than being held to Write-simple, because both are *known* to be expensive for reasons that are correct (H5's work factor, H4's per-course durability). Holding them to a target that would require breaking those properties would make the SLO the thing that is wrong.

Across the whole run:

- **Error rate < 0.1%** in any scenario, at any scale. Errors under load are a correctness finding, not a performance one.
- **No scenario may exceed its class SLO by more than 2× at scale S2** (the realistic single-institution dataset — `04-workload-data-preparation.md` §1). S3 is a stress probe and is expected to breach; that is what it is for.

---

## 5. Measurement Levels

Four levels, escalating in cost and in how much they can explain. The escalation ladder for a failing run is in `05-baseline-and-reporting.md` §4.

| Level | Purpose | Tooling |
| --- | --- | --- |
| **HTTP load** | The primary level. Drives real requests through the real filter chain and session, produces the percentiles §4 defines, and asserts the SLOs as thresholds. | **k6** (§6) |
| **Database attribution** | Explains a slow scenario in terms of statements: which digest, how many rows examined per row returned, which access path. | `EXPLAIN ANALYZE`; MySQL slow query log at `long_query_time=0.1`; `performance_schema.events_statements_summary_by_digest` |
| **JVM profiling** | Reserved for a run that is red and that the two levels above could not explain. Answers "where is the CPU / where are the allocations." | JFR (`-XX:StartFlightRecording`) or async-profiler |
| **JMH microbenchmark** | Isolated, CPU-bound method-level measurement. Narrowly scoped — see §5.1. | JMH |

### 5.1 JMH scope, and its guardrail

Most of this system's cost is I/O — round trips to MySQL — and a microbenchmark of an I/O-bound path measures the mock, not the system. So JMH is scoped to the four places where the work is genuinely CPU-bound and the answer is genuinely actionable:

| Target | Question it answers | Why it is worth a benchmark |
| --- | --- | --- |
| **BCrypt work-factor calibration**, strength 4 → 14 | What does each strength cost on this hardware, per hash and per verify? | This is the one that matters. Strength 10 is the framework default that `SecurityConfig.java:190` inherits by taking the no-arg constructor — it was never chosen against a measurement. The curve makes the security/latency trade-off explicit and sets the floor under the Login SLO (H5). |
| **`AesPasswordCipher.encrypt` / `decrypt`** | What does the per-call `Cipher.getInstance(TRANSFORMATION)` (`:51`, `:68`) cost versus a reused instance? | JCE provider lookup on every call is a known allocation cost. Small in absolute terms, but this runs on the registration path alongside BCrypt, and it is cheap to know. |
| **Value-object construction** (`Email`, `StudentCode`, `Isbn`, `Credits`) | Is domain validation measurable at all? | **Expected answer: no** — `Email`'s `Pattern` is already `static final` (`student/domain/Email.java:13`), so there is no recompilation per instance. The benchmark exists to *prove* it is noise, so that nobody spends effort optimizing a path that costs nanoseconds. A negative result here is a successful benchmark. |
| **MapStruct page mapping**, 20 and 100 rows | Does DTO mapping contribute anything at max page size? | Bounds the non-I/O share of a list response, which is the alternative hypothesis whenever H1/H2 is suspected. |

**Guardrail, and it is a rule rather than a suggestion: a JMH result may never justify a code change on its own.** It may only support a change whose effect is also visible in the k6 numbers for a scenario in `03-benchmark-scenarios.md`. Microbenchmarks are diagnostic instruments here, not evidence of user-visible improvement — a 40% win on a path that accounts for 0.3% of request time is a rounding error wearing a percentage sign.

---

## 6. Tooling

| Concern | Tool | Status |
| --- | --- | --- |
| HTTP load driver | **k6** | Recommended; not present |
| Server-side metrics | `spring-boot-starter-actuator` + `micrometer-registry-prometheus` | **Recommended addition** to `management/pom.xml` — see §8 |
| Database attribution | MySQL slow log, `performance_schema`, `EXPLAIN ANALYZE` | Available in the existing `mysql:8.4` container; needs configuration per run |
| JVM profiling | JFR (bundled with the JDK) / async-profiler | Available; no setup needed for JFR |
| Microbenchmarks | JMH (`org.openjdk.jmh:jmh-core`, `jmh-generator-annprocess`) | Recommended; not present |
| Dataset generation | SQL bulk load — see `04-workload-data-preparation.md` §4 | Not present |

### 6.1 Why k6, and why not the alternatives

- **Thresholds as code.** The SLOs in §4.2 become `thresholds` in the scenario file itself, so a run exits non-zero when it breaches. The verdict lives with the scenario rather than in someone's reading of a report.
- **Correct percentiles by default.** p95/p99 are first-class outputs, not something to compute from a CSV.
- **Low client-side cost.** The Go runtime under k6 keeps the driver from becoming the bottleneck — which matters acutely here, because on a developer laptop the driver shares a host with both the JVM and MySQL (§7).
- **It stays out of the source tree.** This is the repository-specific reason and it is decisive. `management/src/` is governed by ArchUnit layering rules, Spring Modulith boundary verification, and naming conventions enforced at build time (`CLAUDE.md`, `Testing/01-test-strategy.md` §2). A JavaScript harness in `bench/` cannot violate any of them, cannot slow `./mvnw verify`, and cannot accidentally become a dependency of the application.

**Not Gatling** — technically the strongest alternative, with better built-in reports, but it is a JVM tool that would naturally live as a Maven module inside `management/`, which is exactly the tree §6.1's last point wants to keep clean. Running it outside that tree gives up most of what makes it attractive.

**Not JMeter** — the widest familiarity of the three, but its test plans are generated XML: they do not review, do not diff, and do not merge. In a repository where every artifact is a reviewable text source, that is the wrong shape. Its client footprint is also the heaviest of the three, which §7 cannot afford.

---

## 7. Environments & Parity

| Environment | Role | Notes |
| --- | --- | --- |
| **Developer workstation** | Where runs actually happen | JVM, MySQL (Colima + `docker-compose.yml`), and the k6 driver all share one host. This is the honest description of the only environment that exists. |
| **CI (GitHub Actions)** | Regression smoke only | Shared, unpredictable runners. Suitable for catching order-of-magnitude breakage; unsuitable for latency thresholds — see `02-benchmark-plan.md` §5. |
| **Dedicated benchmark host** | Not provisioned | Would be required before any number here could be called an absolute. Out of scope until the project has a reason to exist on real hardware. |

### 7.1 The caveat that governs how every number in this set is read

**Absolute latencies produced on a shared laptop host are not portable.** The driver competes with the JVM for CPU; Colima interposes a VM between MySQL and the disk; thermal throttling and background processes are uncontrolled. A p95 of 62 ms measured here does not mean the system will serve 62 ms anywhere else.

What *is* portable is the **delta**: the same scenario, on the same host, at two dataset scales, or on two commits. That comparison holds even when the absolute floor is wrong, because the noise applies to both sides. **This document set's currency is relative change — across scales and across commits — not absolute latency**, and `05-baseline-and-reporting.md` expresses every regression threshold that way.

### 7.2 What must be pinned for a run to be comparable

Any of these changing between two runs invalidates the comparison, so all are recorded in the run file (`05-baseline-and-reporting.md` §3):

- **JVM**: version, and explicit heap flags (`-Xms` = `-Xmx`, so the run does not measure heap growth).
- **MySQL container**: image tag, CPU/memory limits, and `innodb_buffer_pool_size` — the single most important knob, because a dataset that fits in the buffer pool and one that does not are two different benchmarks.
- **Dataset**: scale and RNG seed (`04-workload-data-preparation.md` §3).
- **Host state**: CPU count, and measured host CPU utilization *during* the run. **A run in which the k6 process itself consumed a large share of host CPU is discarded, not reported** — the driver was the bottleneck and the numbers describe it, not the system.

---

## 8. Recommended `pom.xml` Additions

Not made by this documentation task — recorded here as recommendations for whoever picks up implementation, exactly as `Testing/01-test-strategy.md` §4 recorded ArchUnit and Testcontainers before either existed.

| Addition | Purpose | Note |
| --- | --- | --- |
| `spring-boot-starter-actuator` + `micrometer-registry-prometheus` | Closes **H8**. Without it, saturation signals (§4.1) are unobtainable and every red run is a guess. | Expose the metrics endpoint **only under a `benchmark` profile**, in the same profile-conditional style `PM-017` established for the demo-accounts route (`application-prod.properties` hard-disables it so it cannot be forgotten by omission). An unauthenticated metrics endpoint in a production build would be a new security surface, and the RBAC allow-list in `SecurityConfig` is deliberately fall-through-free — a new path needs its own matcher, which is the right place to make this explicit. |
| `org.openjdk.jmh:jmh-core` + `jmh-generator-annprocess` | The four microbenchmarks in §5.1. | Test-scope. Note that `jmh-generator-annprocess` is an annotation processor and `management/pom.xml` already configures an explicit `annotationProcessorPaths` list for Lombok and MapStruct — JMH must be added to that list, not merely to `<dependencies>`, or its generated benchmark classes will silently not appear. |

---

## 9. Risk-Based Prioritization

Which hazards get measured first, on the same P0/P1/P2 scheme `Testing/01-test-strategy.md` §6 uses:

| Priority | Hazards | Rationale |
| --- | --- | --- |
| **P0** | H1, H2, H3 | These degrade with *data volume*, which means they are invisible today and certain to appear later. They also affect the endpoints every role uses constantly — the list and search screens are the application's front door. |
| **P1** | H5, H4 | Degrade with *concurrency* and *request size* rather than data volume, so they are bounded and predictable, and both are known-and-accepted costs. Worth a number; not worth blocking on. |
| **P2** | H6, H7 | Degrade only under bursts or long uptime, neither of which this system has experienced. Real, but the least likely to be reached first. |
| **Prerequisite** | H8 | Not prioritized alongside the others because it is not a system hazard — it is the thing that makes diagnosing the other seven possible. Closing it first makes every subsequent run more useful. |

This ordering fixes the execution sequence in [02-benchmark-plan.md](./02-benchmark-plan.md) §3.

---

## 10. Defect Management

Benchmark findings use the same channel and severity scheme as functional defects (`Testing/01-test-strategy.md` §7): **GitHub Issues**, with severity assigned as below. Every issue references the failing **`BM-*` scenario ID** and the **hazard ID** it traces to.

| Severity | Definition for a performance finding |
| --- | --- |
| Critical | Errors under load, or an SLO breach severe enough to make an endpoint unusable at scale S2 |
| Major | An SLO class breached at S2 within the 2× tolerance, or a >50% regression against baseline |
| Minor | A 20–50% regression against baseline, or an S3-only breach |
| Enhancement | A measurement gap — a hazard with no scenario, or a scenario that cannot be attributed |

**No code change is made on a benchmark finding without a linked issue.** Performance changes are the easiest kind to make speculatively and the hardest kind to justify afterwards; requiring the issue forces the before-number, the hypothesis, and the after-number to be written down together.

---

## 11. Out of Scope (this document)

- The harness layout, run protocol, execution order, and CI integration — see [02-benchmark-plan.md](./02-benchmark-plan.md).
- Individual scenarios, their workload shapes, and their IDs — see [03-benchmark-scenarios.md](./03-benchmark-scenarios.md).
- Dataset scales, distributions, and generation — see [04-workload-data-preparation.md](./04-workload-data-preparation.md).
- The baseline definition, regression thresholds, and the run-record template — see [05-baseline-and-reporting.md](./05-baseline-and-reporting.md).
- Actual benchmark code — deliberately not produced by this documentation task, on the same basis as `Testing/` and `PM-docs/`: these five documents are the design that later drives a real harness.
