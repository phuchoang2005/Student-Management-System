# Test Cases — Cross-Cutting Concerns

Testing Documentation — [Test Strategy](../01-test-strategy.md) → [Test Plan](../02-test-plan.md) → Test Cases (Cross-Cutting) → [Test Data Preparation](../04-test-data-preparation.md).

Covers concerns that span every module rather than belonging to one: the full RBAC matrix, the must-change-password gate, optimistic locking, cross-module cascade/lifecycle behavior, the shared error envelope, the 7 explicit ambiguity resolutions documented in `api-specification.md` §5, and architecture/layering conformance (ArchUnit). Per-module functional/negative/boundary cases live in [student.md](./student.md), [book.md](./book.md), [course.md](./course.md), [enrollment.md](./enrollment.md), [identity-auth.md](./identity-auth.md).

---

## 1. RBAC Matrix (Role × Endpoint)

Source of truth: `06-low-level-design.md` §11.1.

| Verb + path | Required role | Notes |
| --- | --- | --- |
| `POST /api/v1/auth/login` | None (public) | — |
| `GET /api/v1/auth/demo-accounts` | None (public) | Only reachable when `app.demo-accounts.enabled=true` (dev/test); registered at all in `prod` — see §9 below |
| `POST /api/v1/auth/password` | Any authenticated role | Only endpoint reachable while `mustChangePassword = true` |
| `GET /api/v1/students/*/initial-password` | REGISTRAR | — |
| `POST/PUT/DELETE /api/v1/students/**` | REGISTRAR | — |
| `POST/DELETE /api/v1/enrollments/**` | REGISTRAR | No `PUT` — `Enrollment` has no update use case. The `POST` matcher covers `/enrollments/batch` (UC-26) without a rule of its own |
| `POST/PUT/DELETE /api/v1/courses/**` | COURSE_ADMINISTRATOR | — |
| `POST/PUT/DELETE /api/v1/books/**` | LIBRARIAN | — |
| `POST /api/v1/staff-accounts` | SYSTEM_ADMINISTRATOR | UC-24 |
| `PATCH /api/v1/staff-accounts/*/status` | SYSTEM_ADMINISTRATOR | UC-25 |
| `GET /api/v1/sessions` | SYSTEM_ADMINISTRATOR | UC-27. Explicit, not inherited: the per-resource read allow-list below does not cover `/sessions`, so without this matcher a `GET` would fall through to `.anyRequest().authenticated()` |
| `DELETE /api/v1/sessions/*` | SYSTEM_ADMINISTRATOR | UC-28 |
| `GET /api/v1/students/**` (except initial-password above) | REGISTRAR, LIBRARIAN, COURSE_ADMINISTRATOR, STUDENT | COURSE_ADMINISTRATOR holds this only to open a profile from a course roster. STUDENT's "own records only" scoping is applied inside the Application Service, not the filter chain — see §1.3 |
| `GET /api/v1/books/**` | LIBRARIAN, STUDENT | STUDENT scoped as above. REGISTRAR and COURSE_ADMINISTRATOR are denied |
| `GET /api/v1/courses/**` | REGISTRAR, COURSE_ADMINISTRATOR, STUDENT | Not scoped — the catalogue is not personal data. LIBRARIAN is denied |
| `GET /api/v1/enrollments/**` | REGISTRAR, COURSE_ADMINISTRATOR | STUDENT is denied outright, not scoped — their courses come from `/me/courses` |
| `GET /api/v1/me/**` | STUDENT only | See [identity-auth.md](./identity-auth.md) TC-IDN-021 |

**Reads are granted per resource, not as one blanket "any authenticated role".** Each role reads what its own work needs (`02-component-diagram.md` §4). SYSTEM_ADMINISTRATOR has no domain-module `GET` access at all — see §9.

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

### TC-XC-006 — Each role reads exactly the resources its own work needs, and no others
- **Related UC / Rule:** `06-low-level-design.md` §11.1; `02-component-diagram.md` §4; `04-authentication-authorization.md` §6.1
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** For each of the 4 domain roles, call `GET /students`, `GET /books`, `GET /courses`, and `GET /enrollments` — every cell of the matrix, in both directions.
- **Expected Result:** the granted cells return `200 OK` (content may be scoped for STUDENT — see §1.3); **every other cell returns `403 Forbidden`**, not an empty result:

  | | `/students` | `/books` | `/courses` | `/enrollments` |
  | --- | :-: | :-: | :-: | :-: |
  | REGISTRAR | 200 | **403** | 200 | reachable |
  | LIBRARIAN | 200 | 200 | **403** | **403** |
  | COURSE_ADMINISTRATOR | 200 | **403** | 200 | reachable |
  | STUDENT | 200 (scoped) | 200 (scoped) | 200 | **403** |

  "reachable" rather than `200` for the two granted enrollment cells: an unfiltered `GET /enrollments` is a `400` by design (§11 below), and a `400` there *is* the pass condition — it proves the request got past the filter chain and was rejected on the endpoint's own terms rather than stopped as a `403`.

  The denied direction is the point of this case. An earlier version of this matrix granted all four domain roles read access to everything, which let a Registrar enumerate the book catalogue and a Librarian enumerate enrollments — neither of which any use case asks for.

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
- **Expected Result:** rejected (`401` or `403` — Spring's default entry point for this chain answers anonymous domain `GET`s with `403`) for every one except `POST /api/v1/auth/login`.

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

TC-XC-009–011 cover `student`; `book` needs the same "own records only" scoping per `02-component-diagram.md` §4 — see TC-XC-043–045 in §10 below.

**`enrollment` is the exception, and deliberately so.** It was originally in this list, scoped the same way. It is now *withdrawn* instead: role STUDENT holds no grant on `/api/v1/enrollments/**` at all, so there is no scoping to test — only a flat `403`, whether the caller asks about their own enrollment or someone else's. A Student's enrolled courses come from `GET /api/v1/me/courses`, derived from the session principal rather than from a student code the caller supplies, so there is no identifier to substitute and no ownership comparison that could be got wrong. Withdrawing a grant is strictly stronger than scoping it, and TC-XC-045 asserts the withdrawal.

All of TC-XC-009–011 and TC-XC-043–045 are implemented in `OwnRecordsScopingIntegrationTest`.

---

## 2. Must-Change-Password Gate

Source of truth: `04-authentication-authorization.md` §4.2.

### TC-XC-012 — An account with `mustChangePassword = true` can only reach `POST /auth/password`
- **Related UC / Rule:** `04-authentication-authorization.md` §4.2; Identity.3
- **Priority:** P0 · **Type:** Security
- **Preconditions:** Freshly-provisioned Student account, never logged a password change.
- **Steps:** Log in with the initial password; then attempt two representative otherwise-allowed reads (`GET /api/v1/students` and `GET /api/v1/me/profile`). A write endpoint isn't used as the second case: STUDENT has zero write endpoints anywhere in the API (TC-XC-005), so a write-endpoint 403 here would be indistinguishable from ordinary RBAC denial rather than gate denial.
- **Expected Result:** `403 Forbidden` on both, and on every endpoint except `POST /api/v1/auth/password`. Implemented in `MustChangePasswordGateIntegrationTest`.

### TC-XC-013 — The gate applies to every role, not only Student
- **Related UC / Rule:** `04-authentication-authorization.md` §4.2 (gate is principal-level, not role-specific)
- **Priority:** P1 · **Type:** Security
- **Steps:** If any staff account is ever created with `mustChangePassword = true` (e.g. via a future admin-provisioning flow), repeat TC-XC-012 for that role.
- **Expected Result:** Same `403` behavior — the gate is not hardcoded to the STUDENT role. (Currently only Student accounts are auto-provisioned with this flag set; this case documents the rule's generality for when staff provisioning is designed.) Represented in `MustChangePasswordGateIntegrationTest` as an `@Disabled` test with this reasoning, rather than fabricating a staff-provisioning path that doesn't exist yet.

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
| 6 | `GET /me/**` by a non-Student role → `403` | [identity-auth.md](./identity-auth.md) TC-IDN-021 |
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

## 8. Pagination Conventions

Source of truth: `api-specification.md` §3 (scheme) and §5 item 8 (defaults/cap and invalid-input handling). These cases check the envelope/param contract once at the cross-cutting level rather than duplicating it identically across every module — per-module cases (default/custom page, out-of-range page) live in [student.md](./student.md), [book.md](./book.md), [course.md](./course.md), and [identity-auth.md](./identity-auth.md) instead.

### TC-XC-036 — Every list endpoint's response uses the `{content, page, size, totalElements, totalPages}` envelope
- **Related UC / Rule:** `api-specification.md` §3 Pagination
- **Priority:** P1 · **Type:** Functional (contract)
- **Steps:** Call `GET /students`, `GET /books`, `GET /courses`, `GET /enrollments?courseCode=…`, `GET /me/books`, and `GET /me/courses`.
- **Expected Result:** every response has exactly the `PageMeta` + `content` shape, and every one is paged the same way — a plain `page`/`size` pair, with no prefixed variants anywhere. Nothing is an embedded array on a detail response any more: `StudentDetail` has no `books`/`courses`, `CourseDetail` has no `roster` (decision #10), which is what made the envelope uniform.

### TC-XC-037 — `size` above the cap is clamped, uniformly
- **Related UC / Rule:** `api-specification.md` §5 item 8
- **Priority:** P2 · **Type:** Boundary
- **Steps:** `GET /api/v1/students?size=101`, then the same on `/books`, `/courses`, `/enrollments?courseCode=…`, and `/me/books`.
- **Expected Result:** `200 OK` with `size` clamped to `100` in every case, via `spring.data.web.pageable.max-page-size`. Uniformity is the assertion here: `/me` previously hand-rolled its own page validation and answered `400` where every other endpoint clamped, which is the inconsistency splitting it into `/me/books` and `/me/courses` removed.

### TC-XC-038 — A negative `page` is rejected with `400`
- **Related UC / Rule:** `api-specification.md` §5 item 8
- **Priority:** P2 · **Type:** Negative
- **Steps:** `GET /api/v1/students?page=-1`.
- **Expected Result:** `400 Bad Request` (`ValidationError`).

---

## 9. Staff Account Provisioning & Demo Accounts (RBAC)

Negative-case coverage for the two endpoints added by UC-24/UC-25 and the demo-accounts convenience, mirroring §1.1's per-resource pattern. Positive/functional coverage for both lives in [identity-auth.md](./identity-auth.md) TC-IDN-024–032.

### TC-XC-039 — Non-System-Administrator roles cannot write `staff-accounts` resources
- **Related UC / Rule:** `06-low-level-design.md` §11.1
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As REGISTRAR, LIBRARIAN, COURSE_ADMINISTRATOR, and STUDENT: attempt `POST /api/v1/staff-accounts` and `PATCH /api/v1/staff-accounts/{id}/status`.
- **Expected Result:** `403 Forbidden` for all 8 combinations (4 roles × 2 verbs).

### TC-XC-040 — System Administrator has no read or write access to any domain module
- **Related UC / Rule:** `02-component-diagram.md` §4 (System Administrator row: no domain access)
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As SYSTEM_ADMINISTRATOR, attempt `GET /api/v1/students`, `GET /api/v1/books`, `GET /api/v1/courses`, and one write endpoint from each.
- **Expected Result:** every request is rejected — reads are `403 Forbidden` just like an out-of-role write, since `identity`'s System Administrator scoping (§1 RBAC Matrix above) grants no `GET /api/v1/**` fallthrough the way the 4 domain-facing roles get.
- **Note:** the role now holds one read grant, `GET /api/v1/sessions` (TC-IDN-033). That does not weaken this case: sessions report who is signed in and expose no student, book, course, or enrollment, so "no domain data" still describes the role exactly. This case asserts the domain modules specifically, and must not be relaxed into "the role can read nothing".

### TC-XC-041 — An unauthenticated caller cannot reach either `staff-accounts` endpoint
- **Related UC / Rule:** `06-low-level-design.md` §11.1
- **Priority:** P0 · **Type:** Security
- **Steps:** With no session cookie, call `POST /api/v1/staff-accounts` and `PATCH /api/v1/staff-accounts/{id}/status`.
- **Expected Result:** `401 Unauthorized` for both.

### TC-XC-042 — `GET /api/v1/auth/demo-accounts` requires no authentication when enabled
- **Related UC / Rule:** `04-authentication-authorization.md` §8
- **Priority:** P1 · **Type:** Security-RBAC
- **Steps:** With no session cookie and `app.demo-accounts.enabled=true` (test profile), call `GET /api/v1/auth/demo-accounts`.
- **Expected Result:** `200 OK` — this is the one endpoint besides login itself that must work before any session exists. See [identity-auth.md](./identity-auth.md) TC-IDN-031/032 for the enabled-vs-disabled functional cases.

---

## 10. Own Records Scoping — Book & Enrollment

Source of truth: `02-component-diagram.md` §4 ("Student | none | own records only — `student` and `book` scoped to `principal.studentId`… no `enrollment` access"), same rule §1.3 exercises for `student`. This scoping did not exist in production code until PM-010 (`04-sprint-backlog.md` §6, `06-low-level-design.md` §11.5) — only `/me/**` implemented it beforehand. Implemented and tested in `OwnRecordsScopingIntegrationTest`, alongside TC-XC-009–011.

`enrollment` appears here only to record that its grant was **withdrawn rather than scoped** (TC-XC-045).

### TC-XC-043 — A Student reading a book they own succeeds; reading another student's book, or an unowned book, is forbidden
- **Related UC / Rule:** `02-component-diagram.md` §4; `api-specification.md` §5.3 (403, not 404, for a well-formed request that only fails authorization)
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As a STUDENT principal owning `book-owned-01`: `GET /api/v1/books/{book-owned-01.isbn}` (own book), `GET /api/v1/books/{book-owned-by-other.isbn}` (someone else's book), `GET /api/v1/books/{book-unowned.isbn}` (nobody's book).
- **Expected Result:** `200 OK` for the caller's own book; `403 Forbidden` for both the other student's book and the unowned book. The unowned-book case is a resolved product decision, not an oversight: Students have no self-service checkout endpoint, so there's no reason for the general `/books` endpoint to double as a browsable catalog for them — "own records only" is read literally.

### TC-XC-044 — A Student's book search is scoped to their own books regardless of the `ownerStudentCode` query parameter
- **Related UC / Rule:** `02-component-diagram.md` §4; `api-specification.md` §5.4 (transparently scoped, never `403`)
- **Priority:** P1 · **Type:** Security-RBAC
- **Steps:** As a STUDENT principal owning one book while another student owns a second: `GET /api/v1/books` with no filter, then again with `ownerStudentCode=<the other student's code>`.
- **Expected Result:** `200 OK` both times, results containing only the caller's own book in both cases — the `ownerStudentCode` filter is silently overridden by the caller's identity when present, never honored and never rejected with `400`/`403` (that would leak whether the other code is valid). The supplied code is not even resolved.

### TC-XC-045 — A Student cannot reach the enrollment endpoints at all, and reads their courses through `/me` instead
- **Related UC / Rule:** `02-component-diagram.md` §4; `04-authentication-authorization.md` §6.1; UC-16
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As a STUDENT principal enrolled in one course, with a second student enrolled in another:
  1. `GET /api/v1/enrollments/{self}/{ownCourse}` — their **own** enrollment;
  2. `GET /api/v1/enrollments?studentCode={self}` — their own courses, via the list endpoint;
  3. `GET /api/v1/enrollments/{other}/{courseOtherIsEnrolledIn}` — another student's real enrollment;
  4. `GET /api/v1/enrollments/{other}/{courseOtherIsNotEnrolledIn}` — a pairing that was never created;
  5. `GET /api/v1/me/courses`.
- **Expected Result:** `403 Forbidden` for steps 1–4, **including the caller's own enrollment** — the whole resource is off role STUDENT's read allow-list, so there is nothing to scope and no ownership comparison to make. Step 5 returns `200 OK` listing the caller's own course.

  This case previously asserted `200` for step 1 and a check-before-lookup `403` for steps 3–4, to keep a probing Student from distinguishing "exists" from "does not exist" by timing. Withdrawing the grant removes that distinction structurally rather than defending it: there is no path in, so there is no signal to leak. Step 5 is what replaces the capability — same answer, derived from the session principal rather than from a student code the caller types.

---

---

## 10. Date/time round trip

MySQL `DATETIME` carries no time zone, so both halves of every round trip have to agree on which one it means. These cases pin that agreement (`06-low-level-design.md` §9.1a).

They are also a warning about test setup. Before this was fixed, every integration test bound `MySQLContainer.getJdbcUrl()` with no time-zone parameter, so the driver used the JVM's zone on **both** halves and the round trip was accidentally self-consistent — the suite was green while production was wrong. `TestDatasource.bind` now appends `serverTimezone=UTC` in one place. **A test that binds a container URL by hand reintroduces the blind spot.**

### TC-XC-046 — `createdAt` survives repeated updates and matches the stored UTC wall clock
- **Related UC / Rule:** `06-low-level-design.md` §9.1a; `05-database-schema.md` §6
- **Priority:** P0 · **Type:** Regression
- **Steps:** Create a course and note `createdAt`. Update it **twice**. Read it back. Separately, read `created_at` straight out of the column.
- **Expected Result:** `createdAt` is within seconds of the wall clock at creation, is unchanged by the updates, and the stored column interpreted as UTC matches it.
- **Two updates, not one, on purpose:** the drift compounded once per `version`, because the value read was written back on every `UPDATE`. A single update is a weaker signal than two.
- **Assert against the column, not only the API:** a self-consistent round trip is exactly what the defect already produced. Only the stored wall clock proves the zone.

### TC-XC-047 — A date of birth is stored on the day it was submitted
- **Related UC / Rule:** `06-low-level-design.md` §9.1a
- **Priority:** P0 · **Type:** Regression
- **Steps:** Register a student with `dateOfBirth: 2000-01-01`; read the response, the detail endpoint, and the `date_of_birth` column.
- **Expected Result:** `2000-01-01` in all three.
- **The defect:** `LocalDate` was written as `atStartOfDay(systemDefault())`, so at a positive UTC offset it went over the wire as the previous evening and MySQL truncated it into the `DATE` column a day early. Reproduces only at a non-UTC JVM zone — which is why running the suite in UTC alone would not catch it.

### TC-XC-048 — A student's `createdAt` is not rewound by an update
- **Related UC / Rule:** UC-1, UC-2
- **Priority:** P0 · **Type:** Regression
- **Steps:** Register a student, note `createdAt`, update them, read it again.
- **Expected Result:** Unchanged. The aggregate this was originally reported against; TC-STU-037 is the same property stated from the user's side.

### TC-XC-049 — Sub-second precision is truncated, and that is the contract
- **Related UC / Rule:** `05-database-schema.md` §6
- **Priority:** P2 · **Type:** Boundary
- **Steps:** Create any record and compare the `createdAt` in the create response with the one a subsequent `GET` returns.
- **Expected Result:** They agree to the second but may differ within it. `DATETIME` without an explicit `(n)` is `DATETIME(0)`, so MySQL discards the sub-second component the in-memory `Instant` carries. Assertions on timestamps must compare at second resolution or read the persisted value — an exact-equality check against the create response is a flaky test, not a defect.

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
| Pagination conventions | TC-XC-036–038 |
| Staff account provisioning & demo accounts (RBAC) | TC-XC-039–042 |
| Own records scoping — book; enrollment grant withdrawn | TC-XC-043–045 |
| Date/time round trip | TC-XC-046–049 |
