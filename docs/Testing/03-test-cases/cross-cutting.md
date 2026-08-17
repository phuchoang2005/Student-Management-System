# Test Cases — Cross-Cutting Concerns

Testing Documentation — [Test Strategy](../01-test-strategy.md) → [Test Plan](../02-test-plan.md) → Test Cases (Cross-Cutting) → [Test Data Preparation](../04-test-data-preparation.md).

Covers concerns that span every module rather than belonging to one: the full RBAC matrix, the must-change-password gate, optimistic locking, cross-module cascade/lifecycle behavior, the shared error envelope, the 7 explicit ambiguity resolutions documented in `api-specification.md` §5, and architecture/layering conformance (ArchUnit). Per-module functional/negative/boundary cases live in [student.md](./student.md), [book.md](./book.md), [course.md](./course.md), [enrollment.md](./enrollment.md), [identity-auth.md](./identity-auth.md).

---

## 1. RBAC Matrix (Role × Endpoint)

Source of truth: `06-low-level-design.md` §11.1.

| Verb + path | Required role | Notes |
| --- | --- | --- |
| `POST /api/v1/auth/login` | None (public) | — |
| `POST /api/v1/auth/password` | Any authenticated role | Only endpoint reachable while `mustChangePassword = true` |
| `GET /api/v1/students/*/initial-password` | REGISTRAR | — |
| `POST/PUT/DELETE /api/v1/students/**` | REGISTRAR | — |
| `POST/DELETE /api/v1/enrollments/**` | REGISTRAR | No `PUT` — `Enrollment` has no update use case |
| `POST/PUT/DELETE /api/v1/courses/**` | COURSE_ADMINISTRATOR | — |
| `POST/PUT/DELETE /api/v1/books/**` | LIBRARIAN | — |
| `GET /api/v1/**` (all list/search/detail endpoints, except initial-password above) | Any authenticated role | STUDENT's "own records only" scoping is applied inside the Application Service, not the filter chain — see §1.3 below |
| `GET /api/v1/me/books-and-courses` | STUDENT only | See [identity-auth.md](./identity-auth.md) TC-IDN-021 |

### 1.1 Write-endpoint authorization — negative cases (one per non-owning role, per resource)

### TC-XC-001 — Non-Registrar roles cannot write `student` resources
- **Related UC / Rule:** `06-low-level-design.md` §11.1
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As LIBRARIAN, then as COURSE_ADMINISTRATOR, then as STUDENT: attempt `POST`, `PUT`, and `DELETE` on `/api/v1/students/**`.
- **Expected Result:** `403 Forbidden` for all 9 combinations (3 roles × 3 verbs).

### TC-XC-002 — Non-Librarian roles cannot write `book` resources
- **Related UC / Rule:** `06-low-level-design.md` §11.1
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As REGISTRAR, COURSE_ADMINISTRATOR, and STUDENT: attempt `POST`, `PUT`/`PATCH`, and `DELETE` on `/api/v1/books/**`.
- **Expected Result:** `403 Forbidden` for every combination.

### TC-XC-003 — Non-Course-Administrator roles cannot write `course` resources
- **Related UC / Rule:** `06-low-level-design.md` §11.1
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As REGISTRAR, LIBRARIAN, and STUDENT: attempt `POST`, `PUT`, and `DELETE` on `/api/v1/courses/**`.
- **Expected Result:** `403 Forbidden` for every combination.

### TC-XC-004 — Non-Registrar roles cannot write `enrollment` resources
- **Related UC / Rule:** `06-low-level-design.md` §11.1
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As LIBRARIAN, COURSE_ADMINISTRATOR, and STUDENT: attempt `POST` and `DELETE` on `/api/v1/enrollments/**`.
- **Expected Result:** `403 Forbidden` for every combination.

### TC-XC-005 — STUDENT can never perform any write operation
- **Related UC / Rule:** `01-system-overview.md` §2 (Student: read-only); `06-low-level-design.md` §11.1
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As STUDENT, attempt every write endpoint across all 4 resource types.
- **Expected Result:** `403 Forbidden` in every case — confirms STUDENT has zero write authority anywhere in the API, matching the "Student cannot enroll or end their own enrollment" note in `use-cases.md` UC-11/UC-12.

### TC-XC-006 — Every role can read every list/search/detail endpoint (except the Student-only and Registrar-only exceptions)
- **Related UC / Rule:** `06-low-level-design.md` §11.1 ("every `GET /api/v1/**`... any authenticated role")
- **Priority:** P1 · **Type:** Security-RBAC
- **Steps:** As each of the 4 roles, call `GET /students`, `GET /books`, `GET /courses`, and the corresponding `/{id}` detail endpoints.
- **Expected Result:** `200 OK` for all roles on all these endpoints (content may be scoped for STUDENT — see §1.3).

### TC-XC-007 — Only REGISTRAR may view a student's initial password
- **Related UC / Rule:** `06-low-level-design.md` §11.1; Identity.5
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As LIBRARIAN, COURSE_ADMINISTRATOR, and STUDENT: `GET /api/v1/students/{code}/initial-password`.
- **Expected Result:** `403 Forbidden` for all three.

### 1.2 Unauthenticated access

### TC-XC-008 — Every endpoint except login rejects an unauthenticated caller
- **Related UC / Rule:** `01-system-overview.md` §4.2 (every request authenticated/authorized before reaching a module)
- **Priority:** P0 · **Type:** Security
- **Steps:** With no session cookie, call one representative endpoint from each resource type (a `GET` and a write endpoint each).
- **Expected Result:** `401 Unauthorized` for every one except `POST /api/v1/auth/login`.

### 1.3 STUDENT "own records only" scoping

### TC-XC-009 — A Student reading their own single-resource record succeeds
- **Related UC / Rule:** `api-specification.md` §5.3
- **Priority:** P1 · **Type:** Security-RBAC
- **Steps:** As a STUDENT principal linked to `student-valid-01`, `GET /api/v1/students/{student-valid-01.code}`.
- **Expected Result:** `200 OK`; full detail returned.

### TC-XC-010 — A Student reading another student's single-resource record is forbidden
- **Related UC / Rule:** `api-specification.md` §5.3 (explicit deviation resolution: `403`, not `404`)
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As a STUDENT principal linked to `student-valid-01`, `GET /api/v1/students/{student-valid-02.code}`.
- **Expected Result:** `403 Forbidden` — the resource exists and the request is well-formed; only authorization fails (deliberately distinct from `404`, since `404` would incorrectly suggest the record doesn't exist at all).

### TC-XC-011 — A Student's search/list results are transparently scoped, not blocked
- **Related UC / Rule:** `api-specification.md` §5.4 (explicit deviation resolution)
- **Priority:** P1 · **Type:** Security-RBAC
- **Steps:** As a STUDENT principal, `GET /api/v1/students?q=<term matching multiple students, including others>`.
- **Expected Result:** `200 OK` (never `403`) with 0 or 1 result — only the caller's own record if it matches, following the same "no match → `200 []`" pattern every search use case already uses; never another student's record, even if it matches the search term.

---

## 2. Must-Change-Password Gate

Source of truth: `04-authentication-authorization.md` §4.2.

### TC-XC-012 — An account with `mustChangePassword = true` can only reach `POST /auth/password`
- **Related UC / Rule:** `04-authentication-authorization.md` §4.2; Identity.3
- **Priority:** P0 · **Type:** Security
- **Preconditions:** Freshly-provisioned Student account, never logged a password change.
- **Steps:** Log in with the initial password; then attempt one representative `GET` and one representative write endpoint the role would otherwise be allowed to call.
- **Expected Result:** `403 Forbidden` on every endpoint except `POST /api/v1/auth/password`.

### TC-XC-013 — The gate applies to every role, not only Student
- **Related UC / Rule:** `04-authentication-authorization.md` §4.2 (gate is principal-level, not role-specific)
- **Priority:** P1 · **Type:** Security
- **Steps:** If any staff account is ever created with `mustChangePassword = true` (e.g. via a future admin-provisioning flow), repeat TC-XC-012 for that role.
- **Expected Result:** Same `403` behavior — the gate is not hardcoded to the STUDENT role. (Currently only Student accounts are auto-provisioned with this flag set; this case documents the rule's generality for when staff provisioning is designed.)

### TC-XC-014 — The gate clears immediately after a successful password change, same session
- **Related UC / Rule:** `04-authentication-authorization.md` §5.1; see also [identity-auth.md](./identity-auth.md) TC-IDN-015
- **Priority:** P0 · **Type:** Security
- **Steps:** Complete UC-22 successfully; immediately call an endpoint that was blocked in TC-XC-012, in the same session.
- **Expected Result:** `200`/expected success status — no re-login required.

---

## 3. Optimistic Locking (`StaleWriteException`)

Source of truth: `06-low-level-design.md` §10. Applies to `Student`, `Course`, `Book`, `User` (each carries `@Version`); does **not** apply to `Enrollment` (no update use case exists for it, per §7).

### TC-XC-015 — Concurrent update to the same `Student` row: second writer gets `409`
- **Related UC / Rule:** `06-low-level-design.md` §10
- **Priority:** P0 · **Type:** Concurrency
- **Steps:** Two clients `GET` the same student; Client A `PUT`s successfully; Client B then `PUT`s using the version it originally read.
- **Expected Result:** Client B receives `409 Conflict` (`StaleWriteException`); Client A's write is preserved, not overwritten. (Also referenced as [student.md](./student.md) TC-STU-020.)

### TC-XC-016 — Concurrent update to the same `Course` row: second writer gets `409`
- **Related UC / Rule:** `06-low-level-design.md` §10
- **Priority:** P0 · **Type:** Concurrency
- **Steps:** Same pattern as TC-XC-015, applied to a `Course` update. (Also referenced as [course.md](./course.md) TC-CRS-013.)
- **Expected Result:** `409 Conflict`.

### TC-XC-017 — Concurrent ownership change on the same `Book`: second writer gets `409`
- **Related UC / Rule:** `06-low-level-design.md` §10 (the Librarian-reassignment race named explicitly in the design rationale)
- **Priority:** P0 · **Type:** Concurrency
- **Steps:** Two clients read the same unassigned book; both attempt to assign it to two different students at nearly the same time.
- **Expected Result:** One assignment succeeds; the other receives `409 Conflict` (`StaleWriteException`) rather than both silently "succeeding" and leaving an ambiguous final owner.

### TC-XC-018 — Concurrent password change on the same `User`: second writer gets `409`
- **Related UC / Rule:** `06-low-level-design.md` §10
- **Priority:** P1 · **Type:** Concurrency
- **Steps:** Two requests submit a password change for the same account using the same stale read.
- **Expected Result:** Second request receives `409 Conflict`.

### TC-XC-019 — `Enrollment` has no optimistic-locking case (negative confirmation)
- **Related UC / Rule:** `06-low-level-design.md` §7, §10
- **Priority:** P2 · **Type:** Design-confirmation
- **Steps:** Confirm (by code/schema inspection once implemented) that `enrollments` has no `version` column and `EnrollmentService` has no update method.
- **Expected Result:** Confirmed absent — documents *why* no concurrency test exists for this aggregate, rather than leaving a silent gap that looks like an oversight.

---

## 4. Cross-Module Cascade / Lifecycle Scenarios

Source of truth: req.md §5, `05-database-schema.md` §5 (both the Spring Modulith event path and the DB `ON DELETE` safety net).

### TC-XC-020 — Student deletion cascade, verified via the application/event path
- **Related UC / Rule:** UC-3; req.md §5 "When a student is removed"
- **Priority:** P0 · **Type:** Functional (integration)
- **Test Data:** `student-full-cascade-01`
- **Steps:** `DELETE /api/v1/students/{code}` through the real API; then verify via the API that books are unassigned, enrollments are gone, and the account can't log in (combines [student.md](./student.md) TC-STU-022–025 into one end-to-end run).
- **Expected Result:** All effects observed, confirming `StudentDeleted` is published and consumed by `book`, `enrollment`, and `identity` synchronously in one transaction.

### TC-XC-021 — Student deletion cascade, verified via direct DB-level constraint (safety-net path)
- **Related UC / Rule:** `05-database-schema.md` §5
- **Priority:** P1 · **Type:** Database Integrity
- **Steps:** With the schema migrated, delete a `students` row directly via SQL (bypassing the application layer) while it has dependent `books`, `enrollments`, and `users` rows.
- **Expected Result:** `books.owner_id` is set to `NULL` for dependent rows (`ON DELETE SET NULL`); dependent `enrollments` and `users` rows are removed (`ON DELETE CASCADE`) — confirms the DB-level safety net holds independently of the application event handlers.

### TC-XC-022 — Course deletion cascade, verified via the application/event path
- **Related UC / Rule:** UC-10; req.md §5 "When a course is removed"
- **Priority:** P0 · **Type:** Functional (integration)
- **Test Data:** `course-with-enrollments-01`
- **Steps:** `DELETE /api/v1/courses/{code}` through the real API; verify enrolled students' enrollment lists no longer include this course.
- **Expected Result:** All effects observed (combines [course.md](./course.md) TC-CRS-015 as part of a broader integration run).

### TC-XC-023 — Course deletion cascade, verified via direct DB-level constraint
- **Related UC / Rule:** `05-database-schema.md` §5
- **Priority:** P1 · **Type:** Database Integrity
- **Steps:** Delete a `courses` row directly via SQL while it has dependent `enrollments` rows.
- **Expected Result:** Dependent `enrollments` rows are removed (`ON DELETE CASCADE`).

### TC-XC-024 — Book deletion has no cascade dependents
- **Related UC / Rule:** `05-database-schema.md` §5 ("Book deleted — no dependents")
- **Priority:** P2 · **Type:** Database Integrity
- **Steps:** Delete an owned book directly via SQL.
- **Expected Result:** No other table is affected — plain row delete; the owning student's row is untouched (confirmed also at the API level in [book.md](./book.md) TC-BOOK-013).

---

## 5. Error Envelope Conventions

Source of truth: `api-specification.md` §3.

### TC-XC-025 — Every non-2xx response uses the `Error` envelope shape
- **Related UC / Rule:** `api-specification.md` §3
- **Priority:** P1 · **Type:** Functional (contract)
- **Steps:** Trigger one representative error from each HTTP status class (400, 401, 403, 404, 409).
- **Expected Result:** Every response body contains `{timestamp, status, error, message, path}`.

### TC-XC-026 — `400` validation failures additionally include a per-field `errors` array
- **Related UC / Rule:** `api-specification.md` §3 (`ValidationError`)
- **Priority:** P1 · **Type:** Functional (contract)
- **Steps:** Trigger a multi-field validation failure (e.g. register a student with both a blank first name and an invalid DOB).
- **Expected Result:** Response is `400`; body includes the base `Error` fields plus an `errors` array identifying each invalid field.

### TC-XC-027 — Login failure (`401`) reuses the same envelope shape even though it's produced outside `GlobalExceptionHandler`
- **Related UC / Rule:** `06-low-level-design.md` §11.2 (deliberately duplicated shape, since login failures are rejected in the filter chain, before any controller)
- **Priority:** P2 · **Type:** Functional (contract)
- **Steps:** Trigger TC-IDN-002/003 (login failure); inspect the response body shape.
- **Expected Result:** Same `{timestamp, status, error, message, path}` shape as every other error, confirming visual/contract consistency despite the different code path that produces it.

---

## 6. `api-specification.md` §5 Ambiguity Resolutions — Full Checklist

Every explicitly-called-out design decision from `api-specification.md` §5, confirmed covered:

| # | Decision | Covered by |
| --- | --- | --- |
| 1 | Malformed email → `400`; duplicate email → `409` | [student.md](./student.md) TC-STU-003, TC-STU-004 |
| 2 | Unknown FK reference (owner/student/course) → `400`, not `409` | [book.md](./book.md) TC-BOOK-004, TC-BOOK-008; [enrollment.md](./enrollment.md) TC-ENR-003, TC-ENR-004 |
| 3 | Student reading another principal's single-resource record → `403` | TC-XC-010 (this file) |
| 4 | Student role on search/list endpoints → `200` with scoped/empty results, not `403` | TC-XC-011 (this file) |
| 5 | `GET /students/{code}/initial-password` collapses "already changed" and "not found" into one `404` | [identity-auth.md](./identity-auth.md) TC-IDN-017, TC-IDN-018 |
| 6 | `GET /me/books-and-courses` by a non-Student role → `403` | [identity-auth.md](./identity-auth.md) TC-IDN-021 |
| 7 | `DELETE /books/{isbn}/owner` when already unowned → idempotent `200`, not `409` | [book.md](./book.md) TC-BOOK-011 |

---

## 7. Architecture Conformance (ArchUnit)

Source of truth: `06-low-level-design.md` §2.1–2.2 (package layout, class-shape conventions) and §3 (exception hierarchy). These rules verify the hexagonal shape and naming conventions the design fixes are actually followed in code, independent of any business-logic test above. Unlike every other section in this file, these cases need no database, no Spring context, and no seeded data (see [04-test-data-preparation.md](../04-test-data-preparation.md), which these cases have no entry in) — they run as plain ArchUnit/JUnit 5 tests against compiled classes, and can be written and kept green from the first module skeleton onward (see [02-test-plan.md](../02-test-plan.md) §4, §5).

### TC-XC-028 — `domain/` is framework-free
- **Related UC / Rule:** `06-low-level-design.md` §2.1 (`domain/` folder description: "framework-free — Aggregate root, Value Objects")
- **Priority:** P1 · **Type:** Architecture
- **Steps:** ArchUnit rule: no class residing in any `..domain..` package may depend on `org.springframework..` or `org.springframework.data..`.
- **Expected Result:** Zero violations — the domain layer (aggregates, Value Objects) stays a plain-Java model with no framework coupling, matching the stated design intent (Lombok is exempted, since `06-low-level-design.md` §2.2 explicitly allows it there for boilerplate without adding a *runtime* framework dependency).

### TC-XC-029 — Intra-module layering follows `web → application → domain`, with `port` accessed only from `application`/`internal`
- **Related UC / Rule:** `06-low-level-design.md` §2.1 (per-module folder shape)
- **Priority:** P0 · **Type:** Architecture
- **Steps:** ArchUnit `layeredArchitecture()` rule per module: `web` may access `application`; `application` may access `domain` and `port`; `domain` may not access `web`, `application`, `port`, or `internal`; `internal` may access `domain` and `port` (to implement it) but must not be accessed by `web`, `application`, or `domain`.
- **Expected Result:** Zero violations across all 5 modules — confirms the dependency-inversion boundary `02-component-diagram.md` §3 describes (`internal/` never directly referenced by the layers above it; Spring wires the port→adapter binding).

### TC-XC-030 — No module accesses another module's `internal/` package (ArchUnit backstop)
- **Related UC / Rule:** `06-low-level-design.md` §2.1 ("`internal/` … invisible outside the module")
- **Priority:** P1 · **Type:** Architecture
- **Steps:** ArchUnit rule: classes in `..internal..` of one module package (e.g. `org.phuchoang.management.student.internal`) must never be accessed from a different top-level module package (e.g. `org.phuchoang.management.book`).
- **Expected Result:** Zero violations. This intentionally duplicates what Spring Modulith's `ApplicationModules.verify()` already checks (see [01-test-strategy.md](../01-test-strategy.md) §2 "Module boundary" row) — kept as a second, independent check because it runs faster (no Spring context bootstrap) and fails with a more specific message pointing at the exact class pair, useful as an early signal before a full Modulith verification run.

### TC-XC-031 — Every exception under `shared.exception` is unchecked and extends `ApiException`
- **Related UC / Rule:** `06-low-level-design.md` §3 (exception hierarchy: `ApiException` abstract root; every leaf type "Unchecked (`extends RuntimeException`), under `shared.exception`")
- **Priority:** P1 · **Type:** Architecture
- **Steps:** ArchUnit rule: classes residing in `..shared.exception..` whose simple name ends with `Exception` (excluding `ApiException` itself) must be assignable to `ApiException`; separately, `ApiException` must be assignable to `RuntimeException`.
- **Expected Result:** Zero violations — guarantees `GlobalExceptionHandler`'s single `@ExceptionHandler(ApiException.class)` (`06-low-level-design.md` §3's stated design: "no per-exception-type handler methods needed") can never silently miss a new exception type added later that forgets to extend the hierarchy.

### TC-XC-032 — Representative Value Objects are records
- **Related UC / Rule:** `06-low-level-design.md` §2.2 (Value Object row: `record`, e.g. `record Email(String value)`)
- **Priority:** P2 · **Type:** Architecture
- **Steps:** ArchUnit rule: the named classes `Email`, `StudentCode`, `CourseCode`, `Isbn`, `Credits`, `DateOfBirth`, `Username`, `PasswordHash` (the Value Object set enumerated in `05-database-schema.md`/`06-low-level-design.md`) must each be a Java `record`.
- **Expected Result:** Zero violations. Scoped to a named list rather than "every class in `domain/`" since not every domain class is a Value Object (aggregate roots are plain mutable classes by the same table's own convention) — an all-`domain/`-classes-are-records rule would be a false rule to write.

### TC-XC-033 — Application Services carry no `Impl` suffix and are not interfaces
- **Related UC / Rule:** `06-low-level-design.md` §2.2 (Application Service row: "Concrete class (no `Impl` suffix, no interface) in `application/`")
- **Priority:** P2 · **Type:** Architecture
- **Steps:** ArchUnit rule: classes in `..application..` packages whose simple name matches `*Service` must not be interfaces, and no class anywhere in the codebase may have a simple name ending in `Impl`.
- **Expected Result:** Zero violations — a codebase-wide ban on the `Impl` suffix, since the design's stated reasoning ("single implementation, thin orchestrator — an interface would add indirection with no second implementation to justify it") applies uniformly, not just to `application/`.

### TC-XC-034 — Repository ports stay interfaces; Spring Data types never leak outside `internal/`
- **Related UC / Rule:** `06-low-level-design.md` §2.2 (Repository port row: "never a bare `CrudRepository<T, ID>` leaked outward")
- **Priority:** P1 · **Type:** Architecture
- **Steps:** Two ArchUnit rules: (a) every class in a `..port..` package must be an interface; (b) no class outside an `..internal..` package may depend on `org.springframework.data.repository.CrudRepository` or any other Spring Data JDBC type.
- **Expected Result:** Zero violations on both — confirms Spring Data JDBC is fully contained inside each module's `internal/` adapter, matching the stated rationale ("Keeps Spring Data JDBC entirely inside `internal/`").

### TC-XC-035 — Domain Events are records published at the module root, never under `internal/`
- **Related UC / Rule:** `06-low-level-design.md` §2.2 (Domain Event row: "record, placed at the *publishing* module's root (not `internal/`)")
- **Priority:** P2 · **Type:** Architecture
- **Steps:** ArchUnit rule: the named event classes `StudentDeleted` and `CourseDeleted` must each be a `record` residing directly in their publishing module's root package (`org.phuchoang.management.student`, `org.phuchoang.management.course` respectively), not in any subpackage.
- **Expected Result:** Zero violations — confirms consuming modules (`book`, `enrollment`, `identity`) can have these event types on their classpath without reaching into `internal/`, per the stated "published-language" reasoning.

---

## Traceability Summary

| Concern | Test Case IDs |
| --- | --- |
| RBAC matrix (write authorization) | TC-XC-001–007 |
| Unauthenticated access | TC-XC-008 |
| Student "own records" scoping | TC-XC-009–011 |
| Must-change-password gate | TC-XC-012–014 |
| Optimistic locking | TC-XC-015–019 |
| Cross-module cascade/lifecycle | TC-XC-020–024 |
| Error envelope | TC-XC-025–027 |
| §5 ambiguity resolutions | Table in §6 (cross-references) |
| Architecture conformance (ArchUnit) | TC-XC-028–035 |
