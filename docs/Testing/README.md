# Testing Documentation

Test Plan / Test Strategy, Test Cases, and Test Data Preparation for the Student Management System. These documents were originally written **ahead of implementation**, derived entirely from the specification (`docs/BA-docs/`, `docs/SA-docs/`) to drive test authoring as each module was built. As of Sprint 4 (PM-015), implementation is complete: all UC-1–25 have corresponding automated test classes under `management/src/test/java`, tracked in the [UC → File Index](#uc--file-index) below — see [`docs/PM-docs/04-sprint-backlog.md`](../PM-docs/04-sprint-backlog.md) for the JaCoCo coverage numbers from the latest full run.

This is documentation only — no test code is included here. See [02-test-plan.md](./02-test-plan.md) §4 for the intended suite/package structure once implementation begins.

---

## Reading Order

1. **[01-test-strategy.md](./01-test-strategy.md)** — what kind of testing this system needs and why: scope, test levels, tools, environments, entry/exit criteria, risk-based prioritization.
2. **[02-test-plan.md](./02-test-plan.md)** — which features get tested, in what order, in what environment: test items, build/test sequence, features to test/not test, suspension/resumption criteria, risks & assumptions.
3. **[03-test-cases/](./03-test-cases/)** — the individual test cases, organized per Spring Modulith module plus one cross-cutting file:
   - [student.md](./03-test-cases/student.md)
   - [book.md](./03-test-cases/book.md)
   - [course.md](./03-test-cases/course.md)
   - [enrollment.md](./03-test-cases/enrollment.md)
   - [identity-auth.md](./03-test-cases/identity-auth.md)
   - [cross-cutting.md](./03-test-cases/cross-cutting.md) — RBAC matrix, must-change-password gate, optimistic locking, cross-module cascade scenarios, error envelope, and the 7 explicit design-ambiguity resolutions from `api-specification.md` §5
4. **[04-test-data-preparation.md](./04-test-data-preparation.md)** — the fixture/test-data catalog referenced by ID throughout the test cases above: seed accounts, baseline fixtures, boundary values, negative/duplicate datasets, and the data isolation strategy.

## Relationship to `BA-docs` / `SA-docs`

This documentation set is **derived from, and traces back to**, the existing specification — it introduces no new business rules, roles, or endpoints of its own:

| Source | What it contributes here |
| --- | --- |
| [req.md](../BA-docs/req.md) | Business rule IDs (Student.1–4, Book.1–5, Course.1–3, Enrollment.1–4, Identity.1–7) that every test case's "Related UC / Rule" field traces back to |
| [use-cases.md](../BA-docs/use-cases.md) | The 25 use cases (UC-1–UC-25) that structure every test-case file below |
| [user-stories.md](../BA-docs/user-stories.md) | Acceptance criteria cross-referenced alongside each UC |
| `SA-docs/01`–`06` + `tactical-ddd-design.md` | Architecture, RBAC, DB schema, and exception/HTTP-status mapping that ground the cross-cutting and boundary-value test cases |
| `api-specification.md` + `openapi/` | Exact endpoint paths/verbs and the 7 explicit ambiguous-case resolutions used throughout |

If a source document changes, the corresponding test cases here should be reviewed for drift — this set is not an independent source of truth.

---

## UC → File Index

| Use Case | File | Status |
| --- | --- | --- |
| UC-1 Register Student | [student.md](./03-test-cases/student.md) | ✅ Implemented & tested — `StudentRegistrationIntegrationTest`, `StudentServiceTest` |
| UC-2 Update Student Details | [student.md](./03-test-cases/student.md) | ✅ Implemented & tested — `StudentUpdateIntegrationTest`, `StudentServiceTest` |
| UC-3 Remove Student | [student.md](./03-test-cases/student.md) | ✅ Implemented & tested — `StudentRemovalIntegrationTest`, `StudentServiceTest` |
| UC-4 Add Book | [book.md](./03-test-cases/book.md) | ✅ Implemented & tested — `BookCreationIntegrationTest`, `BookServiceTest` |
| UC-5 Assign Book to Student | [book.md](./03-test-cases/book.md) | ✅ Implemented & tested — `BookOwnershipAssignmentIntegrationTest`, `BookServiceTest` |
| UC-6 Unassign Book | [book.md](./03-test-cases/book.md) | ✅ Implemented & tested — `BookUnassignmentIntegrationTest`, `BookServiceTest` |
| UC-7 Remove Book | [book.md](./03-test-cases/book.md) | ✅ Implemented & tested — `BookRemovalIntegrationTest`, `BookServiceTest` |
| UC-8 Create Course | [course.md](./03-test-cases/course.md) | ✅ Implemented & tested — `CourseCreationIntegrationTest`, `CourseServiceTest` |
| UC-9 Update Course | [course.md](./03-test-cases/course.md) | ✅ Implemented & tested — `CourseUpdateIntegrationTest`, `CourseServiceTest` |
| UC-10 Remove Course | [course.md](./03-test-cases/course.md) | ✅ Implemented & tested — `CourseRemovalIntegrationTest`, `CourseServiceTest` |
| UC-11 Enroll Student in Course | [enrollment.md](./03-test-cases/enrollment.md) | ✅ Implemented & tested — `EnrollmentCreationIntegrationTest`, `EnrollmentServiceTest` |
| UC-12 End Enrollment | [enrollment.md](./03-test-cases/enrollment.md) | ✅ Implemented & tested — `EnrollmentEndIntegrationTest`, `EnrollmentServiceTest` |
| UC-13 View/Search Students | [student.md](./03-test-cases/student.md) | ✅ Implemented & tested — `StudentLookupIntegrationTest` |
| UC-14 View/Search Books | [book.md](./03-test-cases/book.md) | ✅ Implemented & tested — `BookLookupIntegrationTest` |
| UC-15 View/Search Courses | [course.md](./03-test-cases/course.md) | ✅ Implemented & tested — `CourseLookupIntegrationTest` |
| UC-16 View Own Books, Courses & Enrollments | [identity-auth.md](./03-test-cases/identity-auth.md) | ✅ Implemented & tested — `MeControllerIntegrationTest` |
| UC-17 View Student Detail | [student.md](./03-test-cases/student.md) | ✅ Implemented & tested — `StudentLookupIntegrationTest` |
| UC-18 View Book Detail | [book.md](./03-test-cases/book.md) | ✅ Implemented & tested — `BookLookupIntegrationTest` |
| UC-19 View Course Detail | [course.md](./03-test-cases/course.md) | ✅ Implemented & tested — `CourseLookupIntegrationTest` |
| UC-20 View Enrollment Detail | [enrollment.md](./03-test-cases/enrollment.md) | ✅ Implemented & tested — `EnrollmentLookupIntegrationTest` |
| UC-21 Login | [identity-auth.md](./03-test-cases/identity-auth.md) | ✅ Implemented & tested — `LoginIntegrationTest` |
| UC-22 Change Password | [identity-auth.md](./03-test-cases/identity-auth.md) | ✅ Implemented & tested — `ChangePasswordIntegrationTest` |
| UC-23 View Student's Initial Password | [identity-auth.md](./03-test-cases/identity-auth.md) | ✅ Implemented & tested — `InitialPasswordViewIntegrationTest` |
| UC-24 Create Staff Account | [identity-auth.md](./03-test-cases/identity-auth.md) | ✅ Implemented & tested — `StaffAccountIntegrationTest` |
| UC-25 Deactivate/Reactivate Staff Account | [identity-auth.md](./03-test-cases/identity-auth.md) | ✅ Implemented & tested — `StaffAccountIntegrationTest` |
| RBAC matrix, must-change-password gate, optimistic locking, cross-module cascades, error envelope, architecture conformance (ArchUnit), staff-account/demo-account RBAC | [cross-cutting.md](./03-test-cases/cross-cutting.md) | ✅ Implemented & tested — `RbacMatrixIntegrationTest`, `MustChangePasswordGateIntegrationTest`, `OwnRecordsScopingIntegrationTest`, `EnrollmentOptimisticLockingConfirmationTest`, `CascadeLifecycleIntegrationTest`, `EventPublicationRegistryIntegrationTest`, `GlobalExceptionHandlerTest`, `architecture/*`, `DemoAccountsIntegrationTest`, `DemoAccountsDisabledIntegrationTest` |

## Test Case Volume Summary

| File | Test cases | UCs covered |
| --- | --- | --- |
| [student.md](./03-test-cases/student.md) | TC-STU-001–034 (34) | UC-1, 2, 3, 13, 17 |
| [book.md](./03-test-cases/book.md) | TC-BOOK-001–023 (23) | UC-4, 5, 6, 7, 14, 18 |
| [course.md](./03-test-cases/course.md) | TC-CRS-001–027 (27) | UC-8, 9, 10, 15, 19 |
| [enrollment.md](./03-test-cases/enrollment.md) | TC-ENR-001–012 (12) | UC-11, 12, 20 |
| [identity-auth.md](./03-test-cases/identity-auth.md) | TC-IDN-001–032 (32) | UC-16, 21, 22, 23, 24, 25 |
| [cross-cutting.md](./03-test-cases/cross-cutting.md) | TC-XC-001–042 (42) | Spans all modules, plus architecture/layering conformance (no UC — grounded in `06-low-level-design.md` §2 instead), pagination conventions (no UC — grounded in `api-specification.md` §3 instead), and staff-account/demo-account RBAC (UC-24, UC-25) |
| **Total** | **170** | **All 25 UCs** |

Every `req.md` rule (Student.1–4, Book.1–5, Course.1–3, Enrollment.1–4, Identity.1–7) and every UC-1–25 main flow and lettered alternate/exception flow has at least one corresponding test case somewhere in this set — see each file's closing traceability table for the exact mapping.
