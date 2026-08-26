# Sprint Backlog

Project Management Documentation — Part 3 of 3 ([Product Backlog](./01-product-backlog.md) → [Sprint Plan](./02-sprint-plan.md) → Sprint Backlog).

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

## 6. Sprint 4 — Cross-cutting, hardening, v1.0 (34h)

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

**Status:** the audit (row 1) found all 7 resolutions already correctly implemented, and 6 of the 7 already had direct, on-point tests written incidentally as part of earlier UC tickets:

1. Email format/duplicate (400/409) — `Email` VO + `StudentService.register`/`update` — `StudentRegistrationIntegrationTest`, `StudentUpdateIntegrationTest`
2. Unknown FK → 400, not 409 — `BookService.addBook`/`assignOwner`, `EnrollmentService.enroll` — `BookCreationIntegrationTest`, `EnrollmentCreationIntegrationTest`
3. Cross-student single-record read → 403 — `StudentService`/`BookService`/`EnrollmentService.getDetail` — `OwnRecordsScopingIntegrationTest`
4. Student search/list transparently scoped, never 403 — `StudentService`/`BookService.search` — `OwnRecordsScopingIntegrationTest`, `RbacMatrixIntegrationTest`
5. Initial-password 404 collapsing (info-hiding) — `StudentService.viewInitialPassword` — `InitialPasswordViewIntegrationTest`
6. `/me/**` by non-Student → 403 — `SecurityConfig` filter chain — `MeControllerIntegrationTest` *(the endpoint was `/me/books-and-courses` at the time; split into `/me/profile`, `/me/courses`, `/me/books` by PM-022 in Sprint 5)*
7. Idempotent unassign-owner → 200 — `BookService.unassignOwner`/`Book.clearOwner` — `BookUnassignmentIntegrationTest`, `BookServiceTest`, `BookControllerTest`

The one gap found: item 1's malformed-email path was tested on student *create* (`StudentRegistrationIntegrationTest.rejectsMalformedEmail`) but not on *update* — `StudentUpdateIntegrationTest` only covered the duplicate-email (409) case there. Closed by adding `StudentUpdateIntegrationTest.rejectsMalformedEmailOnUpdate` (400); no production code changes were needed since `Email`'s validation already runs identically on both paths.

### PM-015 — JaCoCo coverage report + traceability matrix (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| Add the JaCoCo Maven plugin + CI report-publish step | Build config | 1h |
| Run the full suite; capture coverage numbers | Verification | 0.5h |
| Update `Testing/README.md`'s UC → File Index: mark UC-1–25 implemented + tested | Docs | 1.5h |

**Status:** `jacoco-maven-plugin` 0.8.12 added to `management/pom.xml` (`prepare-agent` + `report` bound to the `verify` phase, so it runs automatically in CI with no extra invocation); `.github/workflows/ci.yml` uploads `management/target/site/jacoco/` as a build artifact after `mvn verify`. Full suite run (`./mvnw verify`, Testcontainers-backed): **449 tests, 0 failures, 1 skipped** (the pre-existing `@Disabled` TC-XC-013 from PM-011, not a new gap). Coverage: **97.6% line, 80.7% branch, 98.5% instruction** across 153 analyzed classes. `Testing/README.md`'s UC → File Index now has a Status column marking all UC-1–25 implemented + tested, naming the concrete test class(es) per UC.

**Sprint 4 subtotal: 8 + 4 + 5 + 3 + 6 + 5 + 3 = 34h** ✓ matches `02-sprint-plan.md`.

---

## 7. Sprint 5 — Role-scoped access rework (34h)

Not part of the original plan. It came out of walking the finished demo UI role by role: with all four domain roles holding read access to everything, each role's screens showed data it had no business with, and two endpoints made the operator handle database ids. See [01-product-backlog.md](./01-product-backlog.md) §8a (Epic H).

Ordering matters here and is deliberate: **PM-020 → PM-021 → PM-019 → PM-022**. Re-keying first means the new list endpoints are born business-key-addressed rather than re-keyed a second time; tightening the grants last means the tests covering those new endpoints already exist when the RBAC matrix changes underneath them.

### PM-020 — Business keys end to end (6h)

| Task | Layer | Est. |
| --- | --- | --- |
| Move `StudentCode` from `student/domain/` to the module root (published language, beside `StudentId`); add `StudentLookup.idOf(StudentCode)` as the single code→id translation point, replacing `existsById` in `book`/`enrollment` | Domain, Application | 1.5h |
| Re-key the enrollment API on `studentCode`: `EnrollmentCreateRequest`, `EnrollStudentCommand`, both path variables, `EnrollmentService.enroll`/`end`/`getDetail` | Web, Application | 1.5h |
| Re-key the book API: `BookOwnerRequest.studentCode`, `BookCreateRequest.ownerStudentCode`, `GET /books?ownerStudentCode=` | Web, Application | 1h |
| Remove every surrogate id from every response DTO; `ownerId` → `ownerStudentCode`, resolved per distinct owner rather than per row | Web, Application | 1h |
| Update the affected tests; re-key the fixtures that read ids out of registration responses to read them from the database instead | Tests | 1h |

**Status:** done. `StudentLookup` is now `{idOf, summaryOf, profileOf}` — `existsById` is gone from the port, the service, and the repository, since resolving a code *is* the existence check. `EnrollmentService` carries two error vocabularies on purpose: an unresolvable code is a `400` when supplied as a reference (`enroll`, `search`) and a `404` when it is part of one enrollment's address (`getDetail`, `end`), so ending an enrollment cannot be used to probe whether a student exists.

### PM-021 — Related data as endpoints, not embedded fields (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| `GET /api/v1/enrollments?studentCode\|courseCode` — exactly one filter required; both directions return the same row shape | Web, Application | 1.5h |
| `EnrollmentRepository.findByCourseCode` + its join query and count, mirroring the existing `findByStudentId` pair | Port, Internal | 1h |
| Delete the `StudentDetail.books`/`.courses` and `CourseDetail.roster` stubs and their view records | Web, Application | 0.5h |
| Integration coverage for both filter directions, the both/neither `400`, and the unresolvable-code `400` | Tests | 1h |

**Status:** done. The three stubs had returned `List.of()` since Sprint 1 and were documented as backend gaps for the UI to disclose; they are now closed rather than disclosed. Removing the roster also removes `course`'s only outbound module dependency.

### PM-019 — Per-resource read grants (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| Split the single four-role `GET` allow-list in `SecurityConfig` into one matcher per resource | Web/security | 1h |
| Rewrite `RbacMatrixIntegrationTest`'s read slice to assert both directions from one matrix table | Tests | 1.5h |
| Invert the enrollment cases in `OwnRecordsScopingIntegrationTest`: the grant is withdrawn, not scoped, so even a Student's *own* enrollment is a 403 | Tests | 0.5h |
| Update the RBAC tables in `02-component-diagram.md` §4, `04-authentication-authorization.md` §6.1, `06-low-level-design.md` §11.1, and `cross-cutting.md` §1 | Docs | 1h |

**Status:** done. Registrar loses books; Librarian loses courses and enrollments; Course Administrator loses books but keeps `GET /students/**` for roster click-through; Student loses enrollments outright. `EnrollmentService.getDetail` consequently lost its `callerStudentId` parameter and its ownership branch — withdrawing the grant removed the comparison rather than defending it.

### PM-022 — Student self-service split (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| Replace `GET /me/books-and-courses` with `/me/profile`, `/me/courses`, `/me/books` | Web | 1h |
| `StudentLookup.profileOf` + the `StudentProfile` read-model, so `me` can serve a full record | Application | 0.5h |
| Drop `MeController`'s hand-rolled prefixed paging in favour of an ordinary `Pageable` | Web | 0.5h |
| Rewrite `MeControllerIntegrationTest` against the three endpoints | Tests | 1h |

**Status:** done. `/me/profile` is what lets the Student screen show their own record directly instead of making them search for themselves, and is the only endpoint that tells a Student their own student code — the login response carries just `{role, mustChangePassword}`. Splitting also removed the one endpoint in the API that answered `400` where every other one clamped an oversized page size.

### PM-023 — Frontend rebuild (13h)

| Task | Layer | Est. |
| --- | --- | --- |
| Scaffold Next.js 16 + TypeScript + Chakra v3; rewrites replacing the Vite proxy; port `client`/`endpoints`/`types`, `AuthContext`/`RequireAuth`/`permissions`, and the three hooks | Frontend | 5h |
| Shared components: `DataTable`, `Pagination`, `RecordCard`, the dialogs, `FormField`, `ErrorBanner`, `AppShell` | Frontend | 2h |
| Per-role screens: students (two shapes), books, courses (two shapes), enrollments (two shapes), staff accounts | Frontend | 5h |
| Rewrite `UI-UX/01-frontend-strategy.md` around the new stack and the narrowed role model | Docs | 1h |

**Status:** done. `npm run typecheck` and `npm run build` both pass. The type layer carries the PM-020 rule: the response types have no `id` field, so a screen cannot reach for one — `grep -rn "studentId\|ownerId" src` returns nothing outside comments.

### PM-024 — Generated docs HTML (4h)

| Task | Layer | Est. |
| --- | --- | --- |
| `util/md-to-html.js` + `util/docs-template.js`: mermaid fences, inlined SVG diagrams, the pan/zoom lightbox, doc-nav derivation, `.md`→`.html` link rewriting | Build tooling | 2.5h |
| Accessibility beyond what the hand-written pages carried: skip link, `<main>`, `scope="col"`, figure semantics, keyboard-operable diagram triggers, a real `role="dialog"` lightbox with focus trap and restore, live-region zoom readout, `prefers-reduced-motion` | Build tooling | 1h |
| Delete the committed `.html`, gitignore it, add `make docs`/`docs-watch`/`docs-clean` | Build config | 0.5h |

**Status:** done. The twins had already drifted apart — `SA-docs/01-system-overview.html` claimed "Part 1 of 5" where its Markdown source said "Part 1 of 6" — which is the concrete argument for generating them.

**Sprint 5 subtotal: 6 + 4 + 4 + 3 + 13 + 4 = 34h**

**Sprint 5 DoD.** Full suite green: **475 tests, 0 failures, 1 skipped** (the same pre-existing `@Disabled` TC-XC-013 from PM-011) — up from Sprint 4's 449, the difference being the new enrollment-list, `/me`-split, and both-directions RBAC cases. `npm run typecheck` and `npm run build` pass in `management-frontend/`. `npx @redocly/cli lint openapi.yaml` passes. The role matrix was also walked end to end against a running stack, one HTTP call per cell.

---

## 8. Coverage check

| Sprint | Task-hour subtotal (this document) | Scope-hour subtotal (`02-sprint-plan.md`) | Match |
| --- | --- | --- | --- |
| Sprint 0 | 19h | 19h | ✓ |
| Sprint 1 | 36h | 36h | ✓ |
| Sprint 2 | 34h | 34h | ✓ |
| Sprint 3 | 40h | 40h | ✓ |
| Sprint 4 | 34h | 34h | ✓ |
| Sprint 5 | 34h | — (added after the plan) | n/a |
| Sprint 6 | 28h | — (added after the plan) | n/a |
| Sprint 7 | 34h | — (added after the plan) | n/a |
| Sprint 8 | 21h | — (added after the plan) | n/a |
| **Total** | **280h** | **163h planned + 117h added** | ✓ |

Every one of the 62 items in [01-product-backlog.md](./01-product-backlog.md) §9's ranked list appears exactly once above, decomposed into 2–6 tasks apiece. Sprints 5 through 8 have no counterpart rows in [02-sprint-plan.md](./02-sprint-plan.md): that document plans the four sprints scoped up front, and Epics H, I, and J were all added afterwards — H from the demo walkthrough, I from using the application, J from reading the code both produced. All three are recorded as addenda there rather than folded into the timeline. If a source document changes (LLD, test cases, or the Product Backlog's estimates), review this set for drift the same way [README.md](./README.md) already flags for the other three PM docs.

---

## 9. Sprint 6 — Registrar workflow and session oversight (28h)

Not part of the original plan, and not part of Sprint 5's either. Where Epic H came out of *demoing* the product, this came out of *using* it. See [01-product-backlog.md](./01-product-backlog.md) §8b (Epic I).

Ordering: **PM-025 first and alone**, then PM-026, PM-027, US-4.3, and US-7.3/7.4 + PM-028 together. PM-025 rewrites the JDBC converter graph and touches every integration test's datasource binding; nothing else should be moving while it lands.

### PM-025 — Timestamp and date UTC correctness (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| `shared/persistence/JdbcConversionsConfig`: a `JdbcCustomConversions` bean registering `@ReadingConverter LocalDateTime→Instant` and `@WritingConverter LocalDate→Timestamp`, both at `ZoneOffset.UTC` | Internal | 1h |
| `TestDatasource.bind` — one place binding a Testcontainers MySQL to `spring.datasource.*`, appending `serverTimezone=UTC` | Tests | 0.5h |
| Repoint all 29 `@DynamicPropertySource` blocks at the helper | Tests | 1h |
| `TimestampRoundTripIntegrationTest` — TC-XC-046–048, asserting against the raw column as well as the API | Tests | 1.5h |
| Document the UTC convention in `05-database-schema.md` §6 and the mechanism in `06-low-level-design.md` §9.1a | Docs | 1h |

**Status:** done. The reported symptom was a wrong registration time; the cause was that only the *read* half was wrong — Connector/J returns `LocalDateTime` for a `DATETIME` and Spring's stock converter interprets it at the JVM's zone, while the write correctly used the connection's UTC. Because `toRow` writes back the `createdAt` it last read, the error compounded 7h per `version`, which is why it looked like a plausible timestamp in any single sitting rather than an obvious bug.

Two things were found rather than fixed-as-specified. `students.date_of_birth` was being stored a day early by the mirror-image defect on `LocalDate`'s *write* side, which nobody had reported. And the test suite had been actively hiding both: every test bound a container URL with no time-zone parameter, so both halves used the same wrong zone and every assertion passed. A `@WritingConverter Instant→LocalDateTime` was written and then deleted — `determineCustomWriteTarget` asks for `(Instant, Timestamp)` first and the store converter already claims that pair, so it was dead code.

Verified by disabling the config and re-running: `createdAt` came back 14h early after two updates (7h × 2, confirming the compounding) and `2000-01-01` read back as `1999-12-31`.

### PM-026 — Edit forms fetch the full record (2h)

| Task | Layer | Est. |
| --- | --- | --- |
| `StudentFormDialog`: fetch `StudentDetail` via `useResource` when editing; seed `dateOfBirth`; guard the seed against a stale record; block submit while loading | Frontend | 1h |
| `CourseFormDialog`: the same for `description` | Frontend | 0.5h |
| Regression cases TC-STU-035–036 | Tests | 0.5h |

**Status:** done. Reported as "the information doesn't change". The dialog was typed on `StudentSummary`, which carries no `dateOfBirth`, so the field opened empty on every edit — and being `required`, the browser's own constraint check rejected the form before any handler ran, so Save appeared inert. Not reproducible through the API, which is why no backend test caught it.

`CourseFormDialog` had the same defect on `description` and is the worse of the two: nothing there is `required`, so the form submitted happily and wrote the blank back, destroying the text rather than refusing to save. A stale-error check on two confirm handlers (`removeAction.error` read from the closed-over render rather than the run's return value) was corrected in the same pass.

### PM-027 — Enrolled-student count on courses (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `CourseRepository.enrollmentCountsFor`/`enrollmentCountOf`; `CourseEnrollmentCountRow`; the `LEFT JOIN` and single-course queries | Port, Internal | 1h |
| `CourseService.search`/`getDetail` — one counts query per page, both now `@Transactional(readOnly = true)`; the field on both views and both DTOs | Application, Web | 0.5h |
| Students column on the courses list, course detail, and the Course Admin list | Frontend | 0.5h |
| TC-CRS-028–033 | Tests | 1h |

**Status:** done. The interesting constraint was that `course` cannot call `enrollment` — `enrollment` already depends on `course`, so the reverse edge closes a cycle `ApplicationModules.verify()` rejects. The count is read by joining the `enrollments` table from `course`'s own JDBC adapter instead: a SQL dependency rather than a Java one, which is the same escape `enrollment` already used in the other direction. It is now reciprocal, and recorded on both sides.

`LEFT JOIN` rather than `JOIN` is load-bearing — a course nobody is enrolled in must return `0`, not vanish from the list. This is also a documented tension with `api-specification.md` §5 decision #10, resolved as decision #11: #10's reasons are paging and authorization, and a scalar has neither, so a count is safe on the response a roster is deliberately kept off.

### US-4.3 — Enroll into several courses at once (8h)

| Task | Layer | Est. |
| --- | --- | --- |
| `EnrollmentBatchService` — a separate bean, not transactional, looping through the proxied `EnrollmentService` | Application | 2h |
| `BatchEnrollmentRequest`/`ResultDto`/`Response`; `POST /enrollments/batch`; mapper methods | Web | 1.5h |
| Multi-select course picker replacing the single-code dialog, with a per-course outcome summary | Frontend | 3h |
| TC-ENR-022–028 | Tests | 1.5h |

**Status:** done. The whole design rests on one detail: `@Transactional` is applied by a proxy, so a loop written inside `EnrollmentService` calling its own `enroll` would be self-invocation — proxy bypassed, annotation inert, every course sharing one transaction, which is exactly the all-or-nothing behaviour the story exists to avoid. A separate bean calling through an injected reference crosses the proxy and gives each course its own transaction. TC-ENR-023 is written to fail if anyone ever folds it back.

Status code chosen as `200`, not `207`: the latter is a WebDAV code defined against an XML body, and using it here would be a pun that buys a client nothing. An unknown *student* stays a whole-request `400` while an unknown *course* is a per-course outcome, because the student is the subject of the request rather than one of its items. Duplicate codes within one request are collapsed rather than reported twice.

### US-7.3 / US-7.4 / PM-028 — Active sessions, revocation, session fixation (10h)

| Task | Layer | Est. |
| --- | --- | --- |
| `SessionRegistry` + `HttpSessionEventPublisher` beans; `sessionConcurrency` with `SessionLimit.UNLIMITED`; **`loginFilter.setSessionAuthenticationStrategy`** with rotation before registration | Security | 2h |
| `SessionRevokedExpiredStrategy` — 401 in the standard envelope, replacing a default that answers 200 with prose | Security | 1h |
| `SessionService` (SHA-256 handles, `getAllPrincipals` iteration, self-revocation guard) + `SessionController`/`SessionMapper`/`ActiveSessionDto` | Application, Web | 2.5h |
| `/sessions` tab, capability, nav entry; 401 ejection wired through `client.ts` → `AuthContext` | Frontend | 2.5h |
| TC-IDN-033–041 | Tests | 2h |

**Status:** done. The estimate assumed the session registry would populate itself once declared; it does not, and finding out why took most of the security half. The login filter is installed with `addFilterAt`, so no `AbstractAuthenticationFilterConfigurer` ever runs for it — and that configurer is the only consumer of the `SessionAuthenticationStrategy` that `.sessionManagement()` publishes. The filter kept its inherited no-op strategy, with no fallback, and the registry would have been permanently empty.

That same gap turned out to be **PM-028 already**: with a no-op strategy the session id was never rotated on login, so the application had no session-fixation protection. It had gone unnoticed precisely because nothing else depended on the strategy being real. One line in the composite fixes both, which is why the two were done together.

Two more defaults needed replacing rather than accepting. `ConcurrentSessionFilter`'s expired-session strategy prints a sentence and never sets a status, so a revoked session's next request would have answered `200 OK` with prose — indistinguishable from success. And session ids are never emitted: they are bearer credentials, so the API publishes a SHA-256 digest as an opaque handle (decision #13).

One trap is documented in the code because it is invisible: `AuthenticatedPrincipal` is a record with value-based equality and the registry keys its map on the principal object, while `AuthController.changePassword` swaps that object mid-session without telling the registry. Looking a principal up by reconstructing one therefore matches nothing, silently. Every read iterates `getAllPrincipals()` instead.

---

## 10. Sprint 7 — Benchmark harness and P0 baseline (34h)

Epic J, first half. See [01-product-backlog.md](./01-product-backlog.md) §8c and [02-sprint-plan.md](./02-sprint-plan.md)'s Sprints 7–8 addendum.

**PM-029/030/031 are executed; PM-032/033/034 are specified and not executed.** Sprints 5–6 carry retrospective `**Status:**` notes saying what their estimates got wrong — the same convention starts here for the three items that have shipped. `benchmark-strategy/result/` still holds an index and no run records, since a baseline (PM-034) needs PM-032/033 first.

Ordering: **PM-029 first**, because it is a prerequisite rather than a hazard fix — without server-side metrics, `05-baseline-and-reporting.md` §4's escalation ladder stops at rung 1 and a slow scenario cannot be attributed to anything. Then PM-030 → PM-031 → PM-032 (harness, data, tooling), PM-033 (the read catalog), and PM-034 last, since a baseline is only meaningful once the four before it are stable.

### PM-029 — Actuator + Micrometer under a `benchmark` profile (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `management/pom.xml`: `spring-boot-starter-actuator` + `micrometer-registry-prometheus` | Build config | 0.5h |
| `application-benchmark.properties` exposing `health`, `metrics`, `prometheus` only — profile-conditional in the style PM-017 already established for `app.demo-accounts.enabled` | Config | 1h |
| `SecurityConfig` matcher for `/actuator/**` under the same profile — the allow-list has no `.anyRequest().authenticated()` fall-through, so an unmatched path is unreachable rather than open | Security config | 1h |
| Verify `/actuator/prometheus` scrapes with the profile active and is absent without it | Verification | 0.5h |

Per `01-benchmark-strategy.md` §8; closes hazard H8, which degrades the benchmark rather than the system.

**Status:** done. `management.endpoints.web.exposure.include=` is explicitly empty in `application.properties` and only `health,metrics,prometheus` in `application-benchmark.properties`; `SecurityConfig` gates `/actuator/health` `permitAll()` and everything else under `/actuator/**` behind `hasRole("SYSTEM_ADMINISTRATOR")`. Verified against a disposable, throwaway MySQL container (never the dev `management-mysql`): with the profile active, `/actuator/health` is 200 anonymously and `/actuator/prometheus`/`/actuator/metrics` are 200 for a `SYSTEM_ADMINISTRATOR` session and 403 for anonymous/wrong-role; without the profile, health still 200s (Spring Boot's own always-on default) but prometheus/metrics 404 even for an authenticated `SYSTEM_ADMINISTRATOR` — confirming the exposure gate, not just the RBAC gate, is what closes H8 outside a benchmark run. `./mvnw test` (275 tests, including ArchUnit and `ApplicationModules.verify()`) is unaffected.

### PM-030 — `bench/` k6 harness skeleton (6h)

| Task | Layer | Est. |
| --- | --- | --- |
| `bench/lib/session.js` — log in **once per VU, never per iteration**; carry `JSESSIONID` explicitly and assert liveness; every VU is a session, which is what makes H7 measurable at all | Harness | 2.5h |
| `bench/lib/slo.js` — the five §4.2 SLO classes as reusable k6 threshold objects | Harness | 1h |
| `bench/lib/config.js` — base URL, scale selection, VU profiles, all from env vars | Harness | 1h |
| `bench/README.md` — prerequisites, how to run, and why the harness sits outside `management/` | Docs | 1h |
| Confirm the new top-level directory changes neither `./mvnw verify` timing nor ArchUnit / `ApplicationModules.verify()` results | Verification | 0.5h |

Per `02-benchmark-plan.md` §1.1–1.2. `session.js` is the one piece the plan calls **not optional**: k6 was chosen over Gatling and JMeter precisely so this code could live in JS outside `management/`, where no architecture rule can reach it.

**Status:** done, skeleton only — `bench/lib/{config,session,slo}.js` and `bench/README.md` exist; `bench/scenarios/*.js` (PM-033) and `make bench-*` targets (PM-032) deliberately do not yet. `session.js`'s `login()`/`assertLive()` pair was exercised end to end against a real running instance (not just read for review): logging in as each of the account-cohort roles PM-031 seeds and hitting each role's liveness path (`/api/v1/me/profile`, `/api/v1/students`, `/api/v1/books`, `/api/v1/courses`) all returned 200. `bench/` sits entirely outside `management/src/`, so `./mvnw test`/`verify` are unaffected by construction, not just by inspection.

### PM-031 — Deterministic dataset generator + scales S1–S4 (8h)

| Task | Layer | Est. |
| --- | --- | --- |
| `bench/seed/scales.*` — row counts and distribution parameters for S1 (50/20/100/150) through S4 | Seed data | 1h |
| Generator core: seeded RNG, bulk `INSERT`/`LOAD DATA` for `students`/`courses`/`books`/`users` against the Flyway-migrated schema (V1→V4). No DDL in the generator, no benchmark-only migration | Seed data | 2.5h |
| Enrollment pairs with `uq_enrollments_student_course` deduplicated **in the generator** — `INSERT IGNORE` would silently change the row count and break determinism | Seed data | 1.5h |
| Distributions: Zipf-like per course, skewed per student (mean ~6 with a 15–20 tail), 20–30% NULL `books.owner_id`, and a search vocabulary whose term→hit-count table ships beside the dataset. Shuffle before assigning `student_code`, so insertion order does not match key order | Seed data | 1.5h |
| Account cohort: a few hundred `STUDENT` users drawn from the middle of the enrollment distribution with real BCrypt hashes at the application's strength and `must_change_password=FALSE`, plus one staff account per role; demo accounts fixed off and recorded as off | Seed data | 1h |
| Verify: `SELECT COUNT(*)` per table, a duplicate check on each unique key, RNG seed written into the run record | Verification | 0.5h |

Per `04-workload-data-preparation.md` §§1–4. Distribution matters more than volume — a uniform S2 would make H1 and H2 look better than they are. PII rules are inherited verbatim from `Testing/04-test-data-preparation.md` §7: fabricated data only, `@example.test` addresses; the generator and its seed are committed, its output and any `mysqldump` are not.

**Status:** done, with two implementation notes worth recording. First, the cohort size follows §4.2's prose ("a few hundred students... plus one staff account per role" — 20/300/300 across S1/S2/S3) rather than the summary table's approximate `users` column (~55/~5,010/~50,010), since the two are arithmetically inconsistent and the prose is the more deliberate spec. Second, each scale's enrollment-count mixture is tuned (not identical across scales) so the expected total lands near that scale's declared row count while keeping §2's shape (skewed, ~10% tail at 15–20 courses) — a single mixture tuned for S2 alone overshot S1's total by ~2× when tried directly.

Verified end to end against disposable, throwaway MySQL containers (never the dev `management-mysql`), never through the app: S1, S2, and **S3 (50,000 students, 1,000 courses, 80,000 books, 401,209 enrollments)** all ran, matched their declared counts, and passed every unique-constraint check. S3 took 41.5s. Distribution shape confirmed empirically, not just by construction — at S3 the top course carries 38,567 enrollments against a ~30-enrollment tail; at S2 the top course carries 3,000+; book ownership NULL rate landed at 21–25% (target 20–30%) across runs. `student_code` insertion order was confirmed decorrelated from sort order by direct query (`id=1` did not map to the lexicographically-first code). Two same-seed runs produced byte-identical `students`/`enrollments` data (MD5-compared). The account cohort's bcryptjs-generated hash was confirmed — via both a direct `BCryptPasswordEncoder.matches()` call and a real `POST /api/v1/auth/login` against a running instance — to interoperate with Spring Security's encoder, not merely assumed compatible. The search-term hit-count table is genuinely observed (queried through each repository's exact `LIKE` shape post-load), and the three `neverUsed` vocabulary terms confirmed 0 hits everywhere, giving a true zero-hit search control.

### PM-032 — `make bench-*` targets (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| `bench-seed SCALE=` — drop, re-migrate, regenerate at the named scale, record the seed | Build tooling | 1h |
| `bench SCENARIO= SCALE=` and `bench-all SCALE=` — raw k6 output into `bench/out/` | Build tooling | 1h |
| `bench-report` and `bench-jmh` | Build tooling | 0.5h |
| `make help` entries; confirm `make bench` does **not** implicitly depend on `make up` | Verification | 0.5h |

Per `02-benchmark-plan.md` §1.3.

### PM-033 — Read-path scenario scripts (8h)

| Task | Layer | Est. |
| --- | --- | --- |
| `scenarios/student-search.js` — BM-STU-001–005: the H3 paging floor, H1 search at default and `size=100`, a deep page from the last decile, and `GET /students/{code}` as the control | Scenario | 2h |
| `scenarios/book-search.js` — BM-BK-001–004, including BM-BK-003's check of the per-page owner memo, which `01-benchmark-strategy.md` records as a deliberate **non-hazard** | Scenario | 1.5h |
| `scenarios/course-list.js` — BM-CRS-001–003, with the grouped `LEFT JOIN` list as the reference point for what "H2 fixed" would look like | Scenario | 1h |
| `scenarios/enrollment-list.js` — BM-ENR-001–004; BM-ENR-002 at `size=100` is the headline H2 measurement, ~101 statements per request | Scenario | 2h |
| `scenarios/me-reads.js` — BM-ME-001–003; BM-ME-002 vs. BM-ME-003 is the clearest before/after illustration of what fixing H2 would buy | Scenario | 1h |
| Response-correctness checks on every scenario, so a fast `4xx` cannot pass for a fast `200` | Scenario | 0.5h |

Per `03-benchmark-scenarios.md` §§1–6. Defaults unless a scenario states otherwise: 20 VUs, scale S2, 300s steady state, authenticating once per VU as the role the endpoint requires.

### PM-034 — P0 baseline runs at S1/S2/S3 (6h)

| Task | Layer | Est. |
| --- | --- | --- |
| Per-run MySQL config: slow log on, `performance_schema` digests reset | Config | 0.5h |
| S1 seed, full read catalog, accepted as the first baseline | Run | 1h |
| S2 seed, app restart, full read catalog — three repetitions, median reported, >~20% p95 spread means the host is too noisy and the run is recorded and repeated | Run | 2h |
| S3 seed, app restart, P0 read scenarios only | Run | 1.5h |
| Run records into `benchmark-strategy/result/` as `YYYY-MM-DD-<scale>-<short-sha>.md`, each carrying its SLO verdict and its regression verdict separately, with the S1→S2→S3 curve classified flat / linear / worse per scenario | Docs | 1h |

Per `02-benchmark-plan.md` §§2–4 and `05-baseline-and-reporting.md` §1. One scale per run, reseed and restart between scales, reads before writes, nothing else on the host, host CPU recorded. A baseline is accepted only against §1's five conditions and is never replaced because a run came back worse.

**Sprint 7 subtotal: 3 + 6 + 8 + 3 + 8 + 6 = 34h**

---

## 11. Sprint 8 — Full scenario catalog, microbenchmarks, regression gate (21h)

Epic J, second half — everything `02-benchmark-plan.md` §3 says can be deferred without making Sprint 7's three runs uninterpretable. Also unexecuted.

### PM-035 — Write- and auth-path scenario scripts (6h)

| Task | Layer | Est. |
| --- | --- | --- |
| `scenarios/writes.js` — BM-STU-006/007, BM-BK-005, BM-CRS-004. BM-STU-006 carries a hidden BCrypt cost: registration provisions an account | Scenario | 1.5h |
| `scenarios/enrollment-batch.js` — BM-ENR-005–008, characterizing H4 at 1, 10, and 50 courses against BM-ENR-005's single-enrollment unit | Scenario | 1.5h |
| `scenarios/auth-login.js` — BM-IDN-001 ramping 1→10→25→50→100 VUs, **run alone**; the deliverable is the knee of the curve, not a single number | Scenario | 1.5h |
| BM-IDN-002 wrong-password login, BM-IDN-003 staff-account control, BM-IDN-004 `/sessions` read during the BM-XC-002 soak | Scenario | 1h |
| Reseed between write repetitions; BM-CRS-004 ordered last within its module since it destroys the dataset | Run | 0.5h |

Per `03-benchmark-scenarios.md` §§1–5. H4's cost is deliberate (`api-specification.md` §5 decision #12) — quantifying it produces client guidance for the API docs, not a defect. BM-IDN-002 is the exception to this whole set's framing: a measurable timing difference between a known and an unknown username is a user-enumeration finding and goes to the security channel, not the performance one.

### PM-036 — Cross-cutting scenarios and the scale sweep (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| `scenarios/cascade-delete.js` — BM-XC-001 at N = 10/50/200, reporting **two** numbers: HTTP latency, and wall-clock until `event_publication` drains (H6, against `AsyncConfig`'s core-2/max-4 pool and 50-slot queue) | Scenario | 1.5h |
| `scenarios/mixed-soak.js` — BM-XC-002, 30 minutes at ~70% reads / ~20% writes / ~10% logins; the deliverable is heap bytes per active session (H7) | Scenario | 1.5h |
| BM-XC-003 — connection-pool saturation at 5/10/20/40 VUs spanning the default Hikari pool of 10, driven through BM-ENR-002 | Scenario | 1h |
| BM-XC-004 — scale sweep of the six P0 scenarios at S1/S2/S3, plotted per scenario | Run | 1h |

Per `03-benchmark-scenarios.md` §7. BM-XC-004 is what `01-benchmark-strategy.md` calls the single most valuable artifact the set produces: growth classified as flat, linear, or worse is the finding, and it is the one output that does not depend on the absolute latency of this particular host.

### PM-037 — JMH microbenchmark suite (5h)

| Task | Layer | Est. |
| --- | --- | --- |
| `jmh-core` + `jmh-generator-annprocess` at test scope, added to the **existing explicit** `annotationProcessorPaths` beside Lombok and MapStruct — a bare `<dependency>` generates nothing, silently | Build config | 0.5h |
| First benchmark class under `management/src/test/java/.../benchmark/`; run `./mvnw test` immediately to confirm ArchUnit and `ApplicationModules.verify()` accept it, falling back to a separate minimal Maven project if either objects | Tests | 1h |
| BM-JMH-001 — BCrypt `encode`/`matches` at strengths 4–14, the cost curve behind H5 | Tests | 1.5h |
| BM-JMH-002 `AesPasswordCipher` as-written vs. a reused `Cipher`; BM-JMH-004 MapStruct page mapping at 20 and 100 rows | Tests | 1.5h |
| BM-JMH-003 — `Email`/`StudentCode`/`Isbn`/`Credits` construction, expected to be noise. The null result is recorded as the deliverable rather than treated as a failed measurement | Tests | 0.5h |

Per `03-benchmark-scenarios.md` §8 and `01-benchmark-strategy.md` §8. The guardrail is stated twice in the source and is repeated here because it is what keeps this item at *Should*: **a JMH result may never justify a code change on its own.**

### PM-038 — CI benchmark smoke job (3h)

| Task | Layer | Est. |
| --- | --- | --- |
| A `benchmark-smoke` job in `.github/workflows/ci.yml` on `workflow_dispatch` plus an optional nightly `main` schedule — separate from `verify`, never gating it, never triggered by a PR | CI | 1.5h |
| S1 seed, 60s warm-up discarded, 60s steady state | CI | 1h |
| Assertions: `http_req_failed` < 1% and a p95 ceiling ~10× the S1 SLO — and nothing that resembles a real SLO | CI | 0.5h |

Per `02-benchmark-plan.md` §5. A shared GitHub runner cannot produce a latency number worth comparing; this job exists to catch a change that breaks the harness or the seed, and its output is advisory. It is a smoke alarm, not a thermometer.

### PM-039 — Regression bands + performance-defect workflow (2h)

| Task | Layer | Est. |
| --- | --- | --- |
| `make bench-report` rendering raw k6 output into the §3 run-record template, carrying SLO verdict and regression verdict **separately** for every scenario | Build tooling | 1h |
| The band table applied per scenario rather than per run: better than −10% improvement, −10%→+20% no change, +20%→+50% investigate, above +50% block, and error rate ≥0.1% blocks regardless of latency. CI output marked advisory and excluded from verdicts | Docs | 0.5h |
| GitHub issue template carrying the `BM-*` id, the hazard id, baseline vs. observed with scale and concurrency, the attribution rung reached, and the hypothesis | Docs | 0.5h |

Per `05-baseline-and-reporting.md` §§2–5 and `01-benchmark-strategy.md` §10. Two rules travel with this item: **no code change on a benchmark finding without a linked issue**, and before concluding anything, rule out the benchmark itself against §4.1's five-item checklist — a saturated driver, an uncounted warm-up, a dataset that isn't what it claims, wrong responses, or pinned config that moved.

**Sprint 8 subtotal: 6 + 5 + 5 + 3 + 2 = 21h**

Not every finding these two sprints produce is a defect. A quantified deliberate cost (H4) belongs in the API documentation as client guidance; a confirmed non-hazard (BM-BK-003, BM-JMH-003) is recorded as a null result; and BM-IDN-002's timing delta leaves the performance channel entirely.
