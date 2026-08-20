# Sprint Backlog

Project Management Documentation — Part 4 of 4 ([Product Backlog](./01-product-backlog.md) → [Sprint Plan](./02-sprint-plan.md) → [Scrum Artifacts](./03-scrum-artifacts.md) → Sprint Backlog).

Decomposes every item [02-sprint-plan.md](./02-sprint-plan.md) pulls into a sprint down to the concrete implementation sub-tasks needed to deliver it — the classic Scrum Sprint Backlog artifact, one level more granular than the Sprint Plan's per-sprint scope list. No new scope is introduced here: every task is sourced from a class, method, or DDL statement already fixed in [SA-docs/06-low-level-design.md](../SA-docs/06-low-level-design.md) (LLD), or from the specific gap/risk citation [01-product-backlog.md](./01-product-backlog.md) already gives each `PM-0xx` platform item.

---

## 1. How to read this backlog

Every **user-story item** (`US-x.x`) decomposes into the same 4–5-task shape, following the package layout LLD §2.1 fixes for all five modules (`web/ → application/ → domain/ → port/ → internal/`):

| Layer | What the task covers |
| --- | --- |
| **Domain** | Value Object(s) + aggregate factory/behavior method(s) |
| **Port/Internal** | Repository port interface + `Jdbc*Repository`/`SpringData*Repository`/`*Row` adapter — only appears on the item that first introduces the aggregate; later items on the same aggregate skip this layer since the adapter already exists |
| **Application** | Command record + Application Service method, per the exact orchestration steps LLD already specifies for that method |
| **Web** | Controller method + Mapper + DTOs, method names matching the OpenAPI `operationId`s (LLD's own convention, e.g. §4.7) |
| **Tests** | The specific `TC-*` case range in [Testing/03-test-cases/](../Testing/03-test-cases/) this item closes |

`student` is LLD's reference module (§4, spelled out in full); `course`/`book`/`enrollment`/`identity` (§§5–8) are described there as *deltas* from `student`, so their task tables below cite the delta LLD calls out (e.g. `book`'s `StudentLookup` validation, `enrollment`'s two-lookup ordering, `identity`'s password ports) rather than re-deriving the whole shape.

Each item's task-hour column sums exactly to that item's estimate in [01-product-backlog.md](./01-product-backlog.md). **Platform items** (`PM-0xx`) have no user story or aggregate to decompose — their tasks are bespoke, sourced individually from the document `01-product-backlog.md`'s "Source" column already cites.

Three places where a later sprint's task explicitly closes a gap an earlier sprint's task had to stub — called out inline rather than silently dropped:
- US-5.1/US-5.3 (Sprint 1/2) stub the `getDetail` read-composition calls into `book`/`enrollment`; US-5.5 (Sprint 3) wires them for real once `enrollment` exists.
- US-1.1 (Sprint 1) calls `AccountProvisioning.provisionForStudent`, but `identity`'s own provisioning pieces don't exist until Sprint 3; US-5.4 (Sprint 3) closes that loop.
- Course removal's enrollment cascade (US-3.3, Sprint 2) ships as a no-op-safe stub; US-4.1/US-4.2 (Sprint 3) wire the real listener, and PM-013 (Sprint 4) is where it's fully tested under cascade scenarios.
- `06-low-level-design.md` §13 specifies three `StudentDeleted` listeners (`book`, `enrollment`, `identity`), but only `enrollment`'s was decomposed into a task at first (US-4.2, Sprint 3) — the other two were left as an unscheduled gap (US-1.3's task table, Sprint 1, even flagged it: "full cascade listeners land with `book`/`enrollment`/`identity` in later sprints", but no later task ever materialized for `book`/`identity`). PM-018 (Sprint 4, added after a Sprint 3 docs/code audit) closes it, immediately ahead of PM-013 whose cascade tests assume both mechanisms exist — implementing `identity`'s literal listener signature turned out to be a genuine `ApplicationModules.verify()` cycle (see PM-018's own note), so `identity` ended up deprovisioned synchronously via `AccountProvisioning` instead, same pattern as `AccountProvisioning#provisionForStudent`.

---

## 2. Sprint 0 — Platform setup (19h)

### PM-000 — Flyway baseline migration (6h)

| Task | Layer | Est. |
| --- | --- | --- |
| Create `db/migration/` + `V1__init_schema.sql` skeleton (charset `utf8mb4`, engine `InnoDB`) | Migration | 0.5h |
| Transcribe `students`/`courses` tables (FK-independent, first in dependency order) | Migration | 1.5h |
| Transcribe `books`/`enrollments`/`users` tables incl. FKs, unique constraints, `chk_users_student_role` CHECK | Migration | 2h |
| Add `version BIGINT NOT NULL DEFAULT 0` to `students`/`courses`/`books`/`users` (not `enrollments`) | Migration | 0.5h |
| Apply against a fresh MySQL 8 container; verify column-by-column against `05-database-schema.md` §3 | Verification | 1.5h |

Per `06-low-level-design.md` §9.2.

### PM-001 — Makefile/docker-compose fix (2h)

| Task | Layer | Est. |
| --- | --- | --- |
| Audit `Makefile` targets referencing Postgres (`management-postgres`, `psql`, `colima`) vs. the MySQL `docker-compose.yml` | Audit | 0.5h |
| Rewrite `Makefile` targets to target the MySQL service/container name | Build tooling | 1h |
| Verify `make up`/`make down`/equivalent end-to-end against the MySQL container | Verification | 0.5h |

Per `Testing/02-test-plan.md` §5's "known inconsistency" call-out.

### PM-002 — Remove hardcoded security placeholder (1h)

| Task | Layer | Est. |
| --- | --- | --- |
| Remove `spring.security.user.name`/`password` from `application.properties` | Config | 0.25h |
| Confirm app still boots (expect blanket 401s until PM-006 wires the real filter chain); note the temporary gap | Verification | 0.75h |

Per `Testing/02-test-plan.md` §5's "known pre-existing scaffolding item" call-out.

### PM-003 — CI pipeline (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| Author `.github/workflows/ci.yml` — checkout, JDK setup matching `pom.xml`, Maven cache | CI | 1h |
| Wire the `mvn verify` step (only unit + ArchUnit levels can pass this early — no integration tests exist yet) | CI | 1h |
| Trigger on PR against `main`; confirm the check appears on a test PR | Verification | 1h |
| Document CI usage in the project README | Docs | 1h |

### PM-004 — ArchUnit + Testcontainers skeleton (6h)

| Task | Layer | Est. |
| --- | --- | --- |
| Add `archunit` + `testcontainers`/`testcontainers-mysql` test-scope dependencies to `pom.xml` | Build config | 0.5h |
| `architecture/LayeringRulesTest` (web→application→domain→port; `internal/` never imported outside its module) | Test | 2h |
| `architecture/DomainPurityTest` (`domain/` has no Spring imports) | Test | 1.5h |
| `architecture/NamingConventionsTest` (Controller/Service/Repository/Row suffix rules, §2.2) | Test | 1h |
| `shared/ModuleBoundaryTest` (`ApplicationModules.verify()` wrapped as a JUnit test) | Test | 1h |

**Sprint 0 subtotal: 6 + 2 + 1 + 4 + 6 = 19h** ✓ matches `02-sprint-plan.md`.

---

## 3. Sprint 1 — `shared` + `student` + `identity` provisioning (36h)

### PM-005 — `shared` exception hierarchy + global handler + error envelope (6h)

| Task | Layer | Est. |
| --- | --- | --- |
| `shared.exception` package: `ApiException` base + full hierarchy (11 classes, LLD §3's classDiagram) | Domain/shared | 2.5h |
| `shared.web.GlobalExceptionHandler` (`@RestControllerAdvice`), single `@ExceptionHandler(ApiException.class)` mapping to the `Error`/`ValidationError` envelope | Web/shared | 2h |
| Wire `DomainValidationException`'s field-level `ValidationError` variant for VO/bean-validation failures | Web/shared | 1h |
| Unit tests: one per HTTP-status branch (400/401/403/404/409) | Tests | 0.5h |

Per `06-low-level-design.md` §3.

### PM-006 — Spring Security filter chain skeleton (8h)

| Task | Layer | Est. |
| --- | --- | --- |
| `shared.security.SecurityConfig` — `authorizeHttpRequests` RBAC matrix (§11.1's table) | Security config | 2h |
| `JsonUsernamePasswordAuthenticationFilter` + success/failure handlers (§11.2) | Security config | 2h |
| `PasswordEncoder` bean (`BCryptPasswordEncoder`) | Security config | 0.25h |
| `MustChangePasswordFilter` chain slot (`addFilterAfter`) — stub only; real 403 logic ships with PM-011 (Sprint 4) since `identity` doesn't exist yet | Security config | 1.25h |
| Session management config (`SessionCreationPolicy.IF_REQUIRED`); CSRF-disabled decision documented inline per §11.1's note | Security config | 0.5h |
| Smoke test: unauthenticated request to a protected endpoint → 401/403 (full RBAC matrix lands in PM-010) | Tests | 2h |

### US-1.1 — Register a student (+ provisioning) (8h)

| Task | Layer | Est. |
| --- | --- | --- |
| `StudentCode`, `Email`, `DateOfBirth` VOs + `Student.register()` factory | Domain | 2h |
| `StudentRepository` port, `JdbcStudentRepository`, `SpringDataStudentRepository`, `StudentRow` | Port/Internal | 1.5h |
| `RegisterStudentCommand`, `StudentService.register()` (existsByCode → existsByEmail → `Student.register` → save → `AccountProvisioning.provisionForStudent`, same transaction) | Application | 2h |
| `StudentController.registerStudent`, `StudentMapper`, `RegisterStudentRequest`/`StudentRegistrationResponse` DTOs | Web | 1.5h |
| TC-STU registration cases (domain/application/web) | Tests | 1h |

Per `06-low-level-design.md` §4.3–§4.7.

### US-1.2 — Update a student's details (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| `Student.applyChanges()` | Domain | 0.5h |
| `UpdateStudentCommand`, `StudentService.update()` (findByCode → conditional `existsByEmailExcludingCode` → `applyChanges` → save) | Application | 1.5h |
| `StudentController.updateStudent`, `UpdateStudentRequest`/`StudentResponse` DTOs | Web | 1h |
| TC-STU update cases | Tests | 1h |

### US-1.3 — Remove a student (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| `StudentDeleted` domain event (record, module root) | Domain | 0.5h |
| `StudentService.remove()` (deleteByCode → publish `StudentDeleted`, async after commit) | Application | 1.5h |
| `StudentController.removeStudent` | Web | 0.5h |
| Verify `StudentDeleted` is publishable/consumable (full cascade listeners land with `book`/`enrollment`/`identity` in later sprints) | Verification | 1h |
| TC-STU removal cases incl. event-publication assertion | Tests | 1.5h |

### US-5.1 — Registrar looks up a student (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| `StudentRepository.search()` (`@Query` matching code/name/email) | Port/Internal | 1.5h |
| `StudentService.search()`/`getDetail()` — `getDetail`'s `BookService.findByOwner`/`EnrollmentService.findByStudent` calls stubbed until Sprint 2/3 (closed by US-5.5, §1) | Application | 1.5h |
| `StudentController.searchStudents`/`getStudent`, `StudentSummaryDto`/`StudentDetailDto` | Web | 1h |
| TC-STU search/detail cases | Tests | 1h |

**Sprint 1 subtotal: 6 + 8 + 8 + 4 + 5 + 5 = 36h** ✓ matches `02-sprint-plan.md`.

---

## 4. Sprint 2 — `course` + `book` (34h)

### US-3.1 — Create a course (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| `CourseCode`, `Credits` VOs + `Course.create()` factory | Domain | 1h |
| `CourseRepository` port, `JdbcCourseRepository`, `SpringDataCourseRepository`, `CourseRow` | Port/Internal | 1h |
| `CreateCourseCommand`, `CourseService.create()` | Application | 1h |
| `CourseController.createCourse`, mapper/DTOs | Web | 0.5h |
| TC-CRS creation cases | Tests | 0.5h |

Per `06-low-level-design.md` §5.

### US-3.2 — Update a course (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `Course.applyChanges()`, `UpdateCourseCommand`, `CourseService.update()` | Domain/Application | 1.5h |
| `CourseController.updateCourse` | Web | 0.5h |
| TC-CRS update cases | Tests | 1h |

### US-3.3 — Remove a course (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| `CourseDeleted` domain event | Domain | 0.5h |
| `CourseService.remove()` (deleteByCode → publish `CourseDeleted`) | Application | 1.5h |
| `CourseController.removeCourse` | Web | 0.5h |
| TC-CRS removal cases, incl. no-op-safe enrollment-cascade stub (real listener wired in Sprint 3, tested under load in PM-013) | Tests | 1.5h |

### US-5.3 — Course Administrator looks up courses + roster (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| `CourseRepository.search()` | Port/Internal | 1h |
| `CourseService.search()`/`getDetail()` — roster via `EnrollmentService.findRosterByCourse` stubbed until Sprint 3 | Application | 2h |
| `CourseController.searchCourses`/`getCourse`, DTOs | Web | 1h |
| TC-CRS search/roster cases | Tests | 1h |

### US-2.1 — Add a book to the catalog (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| `Isbn` VO + `Book.create()` factory | Domain | 1h |
| `BookRepository` port, `JdbcBookRepository`, `SpringDataBookRepository`, `BookRow` | Port/Internal | 1h |
| `AddBookCommand`, `BookService.addBook()` incl. `StudentLookup.existsById` owner validation (Book.4) | Application | 1h |
| `BookController.addBook`, mapper/DTOs | Web | 0.5h |
| TC-BOOK creation cases | Tests | 0.5h |

Per `06-low-level-design.md` §6.

### US-2.2 — Assign a book to a student (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| `Book.changeOwner()`, `BookService.assign()` (`StudentLookup` validation) | Domain/Application | 1.5h |
| `StaleWriteException` catch-and-rethrow in `JdbcBookRepository.save()` (§10 pattern — owner reassignment races) | Port/Internal | 1h |
| `BookController.assignBookOwner` | Web | 0.5h |
| TC-BOOK assignment cases incl. concurrent-reassignment setup | Tests | 1h |

### US-2.3 — Unassign a book (2h)

| Task | Layer | Est. |
| --- | --- | --- |
| `Book.clearOwner()`, `BookService.unassign()` | Domain/Application | 1h |
| `BookController.clearBookOwner` | Web | 0.5h |
| TC-BOOK unassignment cases | Tests | 0.5h |

### US-2.4 — Remove a book (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `BookService.remove()` (no event published — removal never cascades, §6) | Application | 1h |
| `BookController.removeBook` | Web | 0.5h |
| TC-BOOK removal cases | Tests | 1.5h |

### US-5.2 — Librarian looks up books + current ownership (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| `BookRepository.search()`/`findByOwnerId()` | Port/Internal | 1.5h |
| `BookService.search()`/`getDetail()`/`findByOwner()` | Application | 1.5h |
| `BookController.searchBooks`/`getBook`, DTOs | Web | 1h |
| TC-BOOK search/ownership cases | Tests | 1h |

**Sprint 2 subtotal: 4 + 3 + 4 + 5 + 4 + 4 + 2 + 3 + 5 = 34h** ✓ matches `02-sprint-plan.md`.

---

## 5. Sprint 3 — `enrollment` + `identity` auth (40h)

### US-4.1 — Enroll a student in a course (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| `EnrollmentRepository` port, `JdbcEnrollmentRepository` (incl. `courseId ⇄ courseCode` join per §9.1), `EnrollmentRow` | Port/Internal | 1.5h |
| `Enrollment.create()` factory | Domain | 0.5h |
| `EnrollStudentCommand`, `EnrollmentService.enroll()` — `studentLookup.existsById` → `courseLookup.existsById` → `existsByStudentAndCourse`, in that exact order (§7) | Application | 1.5h |
| `EnrollmentController.createEnrollment`, mapper/DTOs | Web | 1h |
| TC-ENR enrollment cases | Tests | 0.5h |

Per `06-low-level-design.md` §7.

### US-4.2 — End an enrollment (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `EnrollmentService.end()`; wire `onStudentDeleted`/`onCourseDeleted` `@ApplicationModuleListener`s (closes the US-1.3/US-3.3 stubs, §13) | Application | 1h |
| `EnrollmentController.endEnrollment` | Web | 0.5h |
| TC-ENR end-enrollment + cascade-listener cases | Tests | 1.5h |

### US-5.5 — View enrollment detail (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `EnrollmentService.getDetail()`/`findByStudent()`/`findRosterByCourse()`; wire into `StudentService.getDetail`/`CourseService.getDetail` (closes the US-5.1/US-5.3 stubs, §1) | Application | 1.5h |
| `EnrollmentController.getEnrollment`, `EnrollmentDetailDto` | Web | 1h |
| TC-ENR detail-view cases | Tests | 0.5h |

### US-6.1 — Log in (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| `UserRepository` port, `JdbcUserRepository`, `SpringDataUserRepository`, `UserRow` | Port/Internal | 1h |
| `AppUserDetailsService implements UserDetailsService` + `AuthenticatedPrincipal` (role/studentId/mustChangePassword) | Web/security | 1.5h |
| Wire `JsonUsernamePasswordAuthenticationFilter`/handlers (built in PM-006) against the real `UserRepository` | Security config | 1.5h |
| TC-IDN login cases incl. must-change-password flag in the response body | Tests | 1h |

Per `06-low-level-design.md` §8.5/§8.6, §11.2.

### US-6.2 — Change my password (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| `User.changePassword()`; `PasswordHasher` port + `BCryptPasswordHasher` adapter | Domain/Port | 1h |
| `ChangePasswordCommand`, `IdentityService.changePassword()` (retype check → policy → `matchesCurrentPassword` → `changePassword` → save) | Application | 1.5h |
| `AuthController.changePassword` | Web | 0.5h |
| TC-IDN change-password cases incl. initial password becoming permanently unrecoverable | Tests | 1h |

### US-6.3 — View a student's initial password (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `PasswordCipher` port + `AesPasswordCipher` adapter, `EncryptedInitialPassword` VO | Domain/Port | 1h |
| `IdentityService.viewInitialPassword()` (findByStudentCode → throw if `!mustChangePassword` → decrypt) | Application | 1h |
| `AuthController.viewStudentInitialPassword` | Web | 0.5h |
| TC-IDN cases (Registrar-only, only-until-changed) | Tests | 0.5h |

### US-5.4 — Student views own owned books and enrolled courses (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| `IdentityService.studentIdOf` (`PrincipalStudentResolver`); `InitialPasswordGenerator` port + `SecureRandomInitialPasswordGenerator`, closing US-1.1's `AccountProvisioning` dependency (§1) | Application | 1.5h |
| Scope `BookController`/`EnrollmentController` read paths to `principal.studentId` for the `STUDENT` role | Web | 1.5h |
| TC-STU/TC-BOOK/TC-ENR self-service view cases | Tests | 1h |

### PM-016 — System Administrator role: RBAC extension (2h)

| Task | Layer | Est. |
| --- | --- | --- |
| Add `SYSTEM_ADMINISTRATOR` to the `Role` enum; add `.hasRole("SYSTEM_ADMINISTRATOR")` matchers for `/staff-accounts/**` in `SecurityFilterChain` (built in PM-006) | Security config | 1.5h |
| TC-XC-039–041 RBAC negative cases | Tests | 0.5h |

Per `06-low-level-design.md` §11.1. **Sudden mid-plan addition** — see [02-sprint-plan.md](./02-sprint-plan.md) Sprint 3's capacity note.

### US-7.1 — Create a staff account (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| `User.provisionStaff()` factory; `Role.STAFF_ROLES` constant | Domain | 0.5h |
| `existsByUsername` on `UserRepository` (already listed in §8.3, first real caller); `DuplicateUsernameException` | Port/Internal | 0.5h |
| `ProvisionStaffCommand`, `IdentityService.provisionStaff()` — validate role → `existsByUsername` → generate password → `provisionStaff()` → save | Application | 1.5h |
| `StaffAccountController.createStaffAccount`, DTOs | Web | 1h |
| TC-IDN-024–027 | Tests | 1.5h |

Per `06-low-level-design.md` §8.4/§8.7, `04-authentication-authorization.md` §3a.

### US-7.2 — Deactivate/reactivate a staff account (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `User.setEnabled()`; `findById` on `UserRepository`; `UserNotFoundException` | Domain/Port | 0.5h |
| `IdentityService.setAccountEnabled()` | Application | 0.5h |
| `StaffAccountController.setStatus`; `enabled` check in `AppUserDetailsService.loadUserByUsername` (`DisabledException`) | Web/security | 1h |
| TC-IDN-028–030 | Tests | 1h |

Per `06-low-level-design.md` §8.4/§8.7/§11.3, `04-authentication-authorization.md` §3b/§4.1.

### PM-017 — Demo-accounts endpoint (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `DemoAccountsController` (`@ConditionalOnProperty(app.demo-accounts.enabled)`), `IdentityService.listDemoAccounts()` returning the 5 fixed identities | Web/Application | 1h |
| `application-prod.properties` override (`app.demo-accounts.enabled=false`); dev/test seed data for the 5 demo accounts | Config/Test data | 1h |
| TC-IDN-031–032, TC-XC-042 | Tests | 1h |

Per `06-low-level-design.md` §11.4, `04-authentication-authorization.md` §8. **Lowest-priority item of this sprint's addition** — first to move to Sprint 4 if Sprint 3 needs to shed scope (see capacity note in `02-sprint-plan.md`).

**Sprint 3 subtotal: 5 + 3 + 3 + 5 + 4 + 3 + 4 + 2 + 5 + 3 + 3 = 40h** ✓ matches `02-sprint-plan.md`.

---

## 6. Sprint 4 — Cross-cutting, hardening, v1.0 (31h)

### PM-010 — RBAC matrix integration tests (8h)

| Task | Layer | Est. |
| --- | --- | --- |
| Enumerate the role × endpoint matrix from §11.1's table into a parameterized test data source | Test setup | 1.5h |
| Integration tests: each of 4 roles against write endpoints (student/course/book/enrollment) | Tests | 3h |
| Integration tests: `STUDENT`-role "own records only" scoping (book/enrollment/self-view) | Tests | 2h |
| Integration tests: unauthenticated → 401, wrong-role → 403 | Tests | 1.5h |

**Scope note (picked up during implementation):** the "own records only" scoping this ticket's tests assume (row 3) turned out not to exist in production code — only `/me/**` implemented it; `StudentController`/`BookController`/`EnrollmentController`'s general read endpoints let any authenticated STUDENT read any record. Rather than write tests against incomplete behavior, the scoping itself was implemented as part of this ticket (`06-low-level-design.md` §11.5), so the tests exercise real, correct behavior. This widened the ticket beyond its original test-only scope; see §11.5 for the implementation and `RbacMatrixIntegrationTest`/`OwnRecordsScopingIntegrationTest` for the tests.

### PM-011 — Must-change-password gate (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| Implement `MustChangePasswordFilter.doFilterInternal()` (403 unless path == `/api/v1/auth/password`), replacing PM-006's stub | Security config | 2h |
| Confirm `addFilterAfter(mustChangePasswordFilter, AuthorizationFilter.class)` ordering | Security config | 0.5h |
| Tests: gate blocks all endpoints except password-change while flag is true; clears after `changePassword` | Tests | 1.5h |

**Status:** the filter body and ordering (rows 1–2) shipped ahead of this ticket, alongside US-6.1 (see `MustChangePasswordFilter`'s own Javadoc). This ticket's remaining scope was row 3 — the dedicated `MustChangePasswordGateIntegrationTest` covering TC-XC-012/014; TC-XC-013 is `@Disabled` pending a staff-provisioning flow that can set `mustChangePassword=true` for a non-Student role (none exists yet).

### PM-012 — Optimistic locking implementation + tests (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| Verify `@Version version` handling on `Student`/`Course`/`Book`/`User` `*Row`s (scaffolded per-module; consolidate here) | Port/Internal | 1h |
| `StaleWriteException` catch-and-rethrow at each `Jdbc*Repository.save()` (student/course/book/user — not enrollment) | Port/Internal | 2h |
| Tests: concurrent-write race per aggregate → 409 `StaleWriteException` | Tests | 2h |

Per `06-low-level-design.md` §10.

### PM-018 — Cross-module student-removal cascade: `book` + `identity` (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `BookRepository.clearOwnerByStudentId(StudentId)` port method + `JdbcBookRepository`/`SpringDataBookRepository` impl; `BookService.onStudentDeleted(StudentDeleted event)` `@ApplicationModuleListener` → `repository.clearOwnerByStudentId(event.studentId())` (closes the US-1.3 stub, §13) | Port/Internal, Application | 1.5h |
| `AccountProvisioning.deprovisionForStudent(Long)` — new port method, called synchronously from `StudentService.remove` rather than an event listener (see below); `UserRepository.deleteByStudentId(Long)` port method (declared in the port's Javadoc since US-1.3 but never added) + `JdbcUserRepository`/`SpringDataUserRepository` impl; `IdentityService.deprovisionForStudent` implements the interface method → `repository.deleteByStudentId(studentId)` (closes the US-1.3 stub, §13) | Port/Internal, Application | 1.5h |

Per `06-low-level-design.md` §13 (lines 1122/1124), with one correction found during implementation: the LLD's literal `IdentityService.onStudentDeleted(StudentDeleted event)` `@ApplicationModuleListener` signature is not buildable. `identity` already depends on `student` in the other direction (`AccountProvisioning`, called synchronously from `StudentService.register`/`update`); adding an event listener that imports `student.StudentDeleted` makes the `student`/`identity` package pair mutually dependent, and `ApplicationModules.verify()` fails the build with "Cycle detected: Slice identity -> Slice student -> Slice identity". `book` has no such reverse dependency from `student`, so its listener works exactly as specified. `identity`'s cascade is deprovisioned synchronously instead, in the same transaction as the `Student` delete — the same one-directional-dependency pattern `AccountProvisioning`'s own class Javadoc already establishes for provisioning, extended to deprovisioning. Both mechanisms have a DB-level `ON DELETE` fallback (`fk_books_owner ... SET NULL`, `fk_users_student ... CASCADE`) that keeps data consistent independently of them, but the application-level call is what makes the removal an explicit, testable part of the cascade rather than an incidental FK side effect — PM-013's cascade tests below should assert against that, not just that the row ended up correct.

### PM-013 — Cross-module cascade/lifecycle integration tests (6h)

| Task | Layer | Est. |
| --- | --- | --- |
| Student-removal cascade: books unassigned, enrollments removed, account removed | Tests | 2h |
| Course-removal cascade: enrollments removed | Tests | 1.5h |
| `@ApplicationModuleListener` at-least-once delivery verification (Event Publication Registry) | Tests | 1.5h |
| DB-level `ON DELETE` safety-net cross-check (§9.2/`05-database-schema.md` §5) | Tests | 1h |

### PM-014 — Implement and test the 7 ambiguity resolutions (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| Map each of `api-specification.md` §5's 7 items to its implementing class/method | Audit | 1h |
| Implement/verify each resolution (spread across the module that owns it) | Various | 2.5h |
| One test per resolution | Tests | 1.5h |

### PM-015 — JaCoCo coverage report + traceability matrix (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| Add the JaCoCo Maven plugin + CI report-publish step | Build config | 1h |
| Run the full suite; capture coverage numbers | Verification | 0.5h |
| Update `Testing/README.md`'s UC → File Index: mark UC-1–25 implemented + tested | Docs | 1.5h |

**Sprint 4 subtotal: 8 + 4 + 5 + 3 + 6 + 5 + 3 = 34h** ✓ matches `02-sprint-plan.md`.

---

## 7. Coverage check

| Sprint | Task-hour subtotal (this document) | Scope-hour subtotal (`02-sprint-plan.md`) | Match |
| --- | --- | --- | --- |
| Sprint 0 | 19h | 19h | ✓ |
| Sprint 1 | 36h | 36h | ✓ |
| Sprint 2 | 34h | 34h | ✓ |
| Sprint 3 | 40h | 40h | ✓ |
| Sprint 4 | 34h | 34h | ✓ |
| **Total** | **163h** | **163h** | ✓ |

Every one of the 38 items in [01-product-backlog.md](./01-product-backlog.md) §9's ranked list appears exactly once above, decomposed into 2–6 tasks apiece. If a source document changes (LLD, test cases, or the Product Backlog's estimates), review this set for drift the same way [README.md](./README.md) already flags for the other three PM docs.
