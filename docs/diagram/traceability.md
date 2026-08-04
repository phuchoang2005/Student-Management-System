# Traceability Matrix — Student Management

Every operation in `docs/api-contract/` mapped to the use case, sequence diagram, application-service method, and domain rule that realises it. **23 operations, all traced.**

This is the working checklist for implementation, and the coverage baseline for `docs/plan-springBootTestPlan.prompt.md`.

Legend — UC numbers refer to `diagram/use-cases/use-cases.mmd`, which shows actors and use cases; the «include» relationships are split into `diagram/use-cases/use-cases-includes.mmd`. Where the "Sequence" column says *(pattern of X)*, no dedicated diagram exists because the flow is structurally identical to X; the named diagram is the one to follow.

---

## Students — `api.controller.StudentController`

| # | Operation | Method + path | UC | Sequence | Service method | Domain rule | Success | Errors |
|---|---|---|---|---|---|---|---|---|
| 1 | `createStudent` | `POST /students` | UC1 | `create-student.mmd` | `StudentApplicationService.createStudent` | unique `studentCode`, unique `email` | 201 + `Location` | 400, 409 |
| 2 | `getStudents` | `GET /students` | UC3 | — | `StudentApplicationService.listStudents` | — (paged read) | 200 `PageStudentResponse` | — |
| 3 | `getStudentById` | `GET /students/{studentId}` | UC2 | — | `StudentApplicationService.getStudent` | — | 200 `StudentResponse` | 404 |
| 4 | `updateStudent` | `PUT /students/{studentId}` | UC4 | *(pattern of `create-student.mmd`)* | `StudentApplicationService.updateStudent` | uniqueness checks must **exclude self** | 200 `StudentResponse` | 400, 404, 409 |
| 5 | `deleteStudent` | `DELETE /students/{studentId}` | UC5 | **`delete-student.mmd`** | `StudentApplicationService.deleteStudent` | release owned books → drop enrollments → delete | 204 | 404 |

## Books — `api.controller.BookController`

| # | Operation | Method + path | UC | Sequence | Service method | Domain rule | Success | Errors |
|---|---|---|---|---|---|---|---|---|
| 6 | `createBook` | `POST /books` | UC15 | *(pattern of `create-student.mmd`)* | `BookApplicationService.createBook` | unique `isbn` | 201 + `Location` | 400, 409 |
| 7 | `getBooks` | `GET /books` | UC17 | — | `BookApplicationService.listBooks` | — (paged read) | 200 `PageBookResponse` | — |
| 8 | `getBookById` | `GET /books/{bookId}` | UC16 | — | `BookApplicationService.getBook` | — | 200 `BookResponse` | 404 |
| 9 | `updateBook` | `PUT /books/{bookId}` | UC18 | *(pattern of `create-student.mmd`)* | `BookApplicationService.updateBook` | unique `isbn` excluding self; **must not change `ownerId`** | 200 `BookResponse` | 400, 404, 409 |
| 10 | `deleteBook` | `DELETE /books/{bookId}` | UC19 | **`delete-book.mmd`** | `BookApplicationService.deleteBook` | ownership dies with the row; owner survives | 204 | 404 |

## Courses — `api.controller.CourseController`

| # | Operation | Method + path | UC | Sequence | Service method | Domain rule | Success | Errors |
|---|---|---|---|---|---|---|---|---|
| 11 | `createCourse` | `POST /courses` | UC6 | *(pattern of `create-student.mmd`)* | `CourseApplicationService.createCourse` | unique `courseCode`; `credits > 0` | 201 + `Location` | 400, 409 |
| 12 | `getCourses` | `GET /courses` | UC8 | — | `CourseApplicationService.listCourses` | — (paged read) | 200 `PageCourseResponse` | — |
| 13 | `getCourseById` | `GET /courses/{courseId}` | UC7 | — | `CourseApplicationService.getCourse` | — | 200 `CourseResponse` | 404 |
| 14 | `updateCourse` | `PUT /courses/{courseId}` | UC9 | *(pattern of `create-student.mmd`)* | `CourseApplicationService.updateCourse` | unique `courseCode` excluding self; `credits > 0` | 200 `CourseResponse` | 400, 404, 409 |
| 15 | `deleteCourse` | `DELETE /courses/{courseId}` | UC10 | **`delete-course.mmd`** | `CourseApplicationService.deleteCourse` | drop enrollments → delete; students survive | 204 | 404 |

## Student ↔ Book ownership — `api.controller.StudentBookController`

| # | Operation | Method + path | UC | Sequence | Service method | Domain rule | Success | Errors |
|---|---|---|---|---|---|---|---|---|
| 16 | `assignBookToStudent` | `POST /students/{studentId}/books/{bookId}` | UC20 | **`assign-book.mmd`** | `BookOwnershipApplicationService.assignBook` | `OwnershipPolicy.assertAssignable` — single owner | 200 `BookResponse` | 404, 409 |
| 17 | `unassignBookFromStudent` | `DELETE /students/{studentId}/books/{bookId}` | UC21 | **`unassign-book.mmd`** | `BookOwnershipApplicationService.unassignBook` | `OwnershipPolicy.assertOwnedBy`; book survives | 204 | 404 |
| 18 | `getStudentBooks` | `GET /students/{studentId}/books` | UC22 | — | `BookOwnershipApplicationService.listBooksOf` | 404 if student missing; `[]` if none owned | 200 `BookResponse[]` | 404 |
| 19 | `getBookOwner` | `GET /books/{bookId}/owner` | UC23 | **`get-book-owner.mmd`** | `BookOwnershipApplicationService.findOwner` | 404 distinguishes *no such book* from *unowned* | 200 `StudentSummaryResponse` | 404 |

## Student ↔ Course enrollment — `api.controller.StudentCourseController`

| # | Operation | Method + path | UC | Sequence | Service method | Domain rule | Success | Errors |
|---|---|---|---|---|---|---|---|---|
| 20 | `enrollStudentInCourse` | `POST /students/{studentId}/courses/{courseId}` | UC11 | **`enroll.mmd`** | `EnrollmentApplicationService.enroll` | `EnrollmentPolicy.assertNotEnrolled` | 201 `EnrollmentResponse` | 404, 409 |
| 21 | `unenrollStudentFromCourse` | `DELETE /students/{studentId}/courses/{courseId}` | UC12 | **`unenroll.mmd`** | `EnrollmentApplicationService.unenroll` | `EnrollmentPolicy.assertEnrolled`; both survive | 204 | 404 |
| 22 | `getStudentCourses` | `GET /students/{studentId}/courses` | UC13 | — | `EnrollmentApplicationService.coursesOf` | 404 if student missing; `[]` if none | 200 `CourseResponse[]` | 404 |
| 23 | `getCourseStudents` | `GET /courses/{courseId}/students` | UC14 | — | `EnrollmentApplicationService.studentsIn` | 404 if course missing; `[]` if none | 200 `StudentSummaryResponse[]` | 404 |

---

## Error mapping — `api.error.GlobalExceptionHandler`

| Thrown | HTTP | Body schema | Raised by |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `ValidationErrorResponse` (`fieldErrors`) | `@Valid` on request DTOs |
| `ResourceNotFoundException` | 404 | `ErrorResponse` | ops 3–5, 8–10, 13–23 |
| `DuplicateResourceException` | 409 | `ErrorResponse` | ops 1, 4, 6, 9, 11, 14, 20 |
| `InvalidRelationshipException` | 409 | `ErrorResponse` | op 16 (book already owned) |
| `DataIntegrityViolationException` | 409 | `ErrorResponse` | DB unique-constraint race — must **not** surface as 500 |

Suggested `code` values, so that same-status different-cause errors stay distinguishable:

`STUDENT_NOT_FOUND`, `BOOK_NOT_FOUND`, `COURSE_NOT_FOUND`, `ENROLLMENT_NOT_FOUND`, `OWNERSHIP_NOT_FOUND`, `BOOK_NOT_ASSIGNED`, `STUDENT_CODE_EXISTS`, `EMAIL_EXISTS`, `ISBN_EXISTS`, `COURSE_CODE_EXISTS`, `ALREADY_ENROLLED`, `BOOK_ALREADY_ASSIGNED`.

`BOOK_NOT_FOUND` vs `BOOK_NOT_ASSIGNED` on operation 19, and `BOOK_NOT_FOUND` vs `OWNERSHIP_NOT_FOUND` on operation 17, are the two cases where the code field is doing real work.

---

## Cross-cutting

**Pagination / sorting / search** — operations 2, 7, 12 bind `PageParam`, `SizeParam`, `SortParam`, `SearchParam` from `api-contract/components/parameters/` and return a `Page*Response` carrying `PageMetadata`. Paging is zero-based; `size` is bounded per the contract. The relationship list endpoints (18, 22, 23) return plain arrays, **not** pages.

**Auditing** — `createdAt` / `updatedAt` on `students`, `books`, `courses` are set by JPA auditing (`@EnableJpaAuditing`, `@CreatedDate`, `@LastModifiedDate`), never by hand and never accepted from a request body. `student_courses.enrolled_at` is set by `Enrollment.open()`.

**Transactions** — every write operation opens its boundary on the application-service method. Read operations use `@Transactional(readOnly = true)`. No `@Transactional` in a controller or a repository.

**Ordering constraint** — operations 5 and 15 are the only ones touching more than one aggregate. Both must release/remove dependent rows *before* deleting the parent so no FK is left dangling.

---

## Implementation gaps outside the diagrams

Found while validating this matrix. None are diagram problems, but all block a working implementation:

1. **`management/pom.xml` lacks `spring-boot-starter-validation`.** Every 400 in this matrix depends on Jakarta Bean Validation.
2. **No schema migration.** `docs/database/schema.mermaid` has no executable counterpart, and `application.properties` sets no `spring.jpa.hibernate.ddl-auto`. Add Flyway (`V1__init.sql`) so the unique constraints this matrix relies on — `students(student_code)`, `students(email)`, `books(isbn)`, `courses(course_code)`, `student_courses(student_id, course_id)` — actually exist.
3. **`application.properties` still sets `spring.security.user.*`** while the security starter is commented out in the pom. Dead config.
