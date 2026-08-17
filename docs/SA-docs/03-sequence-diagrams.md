# Sequence Diagrams

Solution Architecture Document — Part 3 of 6 ([System Overview](./01-system-overview.md) → [Component Diagram](./02-component-diagram.md) → Sequence Diagram → [Authentication & Authorization](./04-authentication-authorization.md) → [Database Schema](./05-database-schema.md) → [Low-Level Design](./06-low-level-design.md)).

This document shows, for every use case in [use-cases.md](../BA-docs/use-cases.md), the order of calls a request makes through the layers and modules described in the [Component Diagram](./02-component-diagram.md): Spring Security → Controller → Service → domain aggregate → repository → MySQL, plus the two synchronous cross-module lookups (`StudentLookup`, `CourseLookup`) and the two asynchronous cascade-delete events (`StudentDeleted`, `CourseDeleted`). Sections are grouped by owning module, matching the ownership table in `02-component-diagram.md` §2.4.

---

## 1. Conventions

Every diagram below reuses the same notation, defined once here so individual sections stay focused on what's different about that use case.

**Arrow styles**

| Arrow | Meaning |
| --- | --- |
| `->>` (solid) | Synchronous call — the caller blocks waiting for a result |
| `-->>` (dashed) | Return / response to a synchronous call |
| `-)` (solid, open head) | Asynchronous, fire-and-forget — used only for the two Spring Modulith domain events (`StudentDeleted`, `CourseDeleted`); the publisher does not wait |
| `alt` / `else` | A validation gate — modeled directly from the "Alt/Exception Flows" enumerated for that UC in `use-cases.md`. A UC gets an `alt` block only where `use-cases.md` actually lists an exception flow for it — nothing is invented beyond what the BA doc specifies |
| `par` / `and` | Two or more calls that are independent of each other (e.g., reading owned books and active enrollments) and don't need to happen in a fixed order |

**Standard lifeline set**

Actor → `Spring Security` → `<Module>Controller` → `<Module>Service` → `<Module>` (aggregate, shown only where a diagram needs to make a domain invariant explicit) → `Jdbc<Module>Repository` (the port and its JDBC adapter are folded into one lifeline here for readability — see `02-component-diagram.md` §3 for the port/adapter split) → `MySQL`.

**Security gate**

Every request passes through the same Spring Security filter chain before reaching a controller. The full authentication/authorization gate (`alt` block, 401/403 branch) is spelled out once, on UC-1's diagram (§2.1). Every other diagram keeps a single `Security` step with a note — *"auth gate as in §2.1"* — rather than repeating the same two branches twenty times.

A second, narrower gate sits behind it: if the authenticated principal's account has `mustChangePassword = true`, every endpoint except Change Password (UC-22) is blocked with 403. This gate is spelled out once in [04-authentication-authorization.md](./04-authentication-authorization.md) §4 rather than repeated here.

**Cross-module reads for detail/composition views (UC-16–UC-20)**

`02-component-diagram.md` §2.2 graphs only the two *write-path* validation dependencies (`book`→`StudentLookup`, `enrollment`→`StudentLookup`/`CourseLookup`). The detail and composition use cases (UC-16 "my books/courses", UC-17 student detail, UC-18 book detail, UC-19 course detail, UC-20 enrollment detail) all need additional *read-path* calls across module boundaries — e.g., `StudentService` reading a student's owned books from `book`, or `CourseService` reading a course's roster from `enrollment`. These are modeled the same way as `StudentLookup`/`CourseLookup`: a narrow, read-only call into the owning module's service. `02-component-diagram.md` §2.4 flags UC-16 explicitly as this kind of "read-side composition, not a new write dependency"; the diagrams below extend that same reasoning to UC-17–UC-20 rather than inventing a different mechanism per use case.

---

## 2. Student module

Owns UC-1, UC-2, UC-3, UC-13, UC-17.

### 2.1 UC-1: Register Student

Registrar submits a new student's code, name, email, and date of birth; the system enforces Student.1–4 before creating the record. This diagram also shows the full security gate referenced by every later diagram.

```mermaid
sequenceDiagram
    actor Registrar
    participant Sec as Spring Security
    participant Ctrl as StudentController
    participant Svc as StudentService
    participant Agg as Student (aggregate)
    participant Repo as JdbcStudentRepository
    participant IdentitySvc as IdentityService
    participant DB as MySQL

    Registrar->>Sec: POST /api/v1/students
    alt unauthenticated or missing student:write role
        Sec-->>Registrar: 401 / 403
    else authorized (Registrar)
        Sec->>Ctrl: forward request
        Ctrl->>Svc: register(command)

        Svc->>Repo: existsByCode(code)
        Repo->>DB: SELECT ... WHERE code = ?
        DB-->>Repo: result
        Repo-->>Svc: exists?
        alt code already exists (Student.1)
            Svc-->>Ctrl: DuplicateCodeException
            Ctrl-->>Registrar: 409 Conflict
        else code unique
            Svc->>Repo: existsByEmail(email)
            Repo->>DB: SELECT ... WHERE email = ?
            DB-->>Repo: result
            Repo-->>Svc: exists / malformed?
            alt email duplicate or malformed (Student.2)
                Svc-->>Ctrl: InvalidEmailException
                Ctrl-->>Registrar: 400 / 409
            else email valid & unique
                Svc->>Agg: create(code, firstName, lastName, email, dob)
                alt blank first/last name (Student.3) or invalid DOB (Student.4)
                    Agg-->>Svc: DomainValidationException
                    Svc-->>Ctrl: (propagates)
                    Ctrl-->>Registrar: 400 Bad Request
                else invariants hold
                    Agg-->>Svc: Student instance
                    Svc->>Repo: save(student)
                    Repo->>DB: INSERT INTO students ...
                    DB-->>Repo: OK
                    Repo-->>Svc: saved
                    Svc->>IdentitySvc: provisionForStudent(code, email)
                    Note over IdentitySvc,DB: full account-provisioning detail in<br/>04-authentication-authorization.md §2
                    IdentitySvc-->>Svc: username, initial password (plaintext, one-time)
                    Svc-->>Ctrl: StudentResponse + username + initial password
                    Ctrl-->>Registrar: 201 Created
                end
            end
        end
    end
```

### 2.2 UC-2: Update Student Details

Registrar updates an existing student's name, email, and/or date of birth. The student code itself never changes.

```mermaid
sequenceDiagram
    actor Registrar
    participant Sec as Spring Security
    participant Ctrl as StudentController
    participant Svc as StudentService
    participant Agg as Student (aggregate)
    participant Repo as JdbcStudentRepository
    participant DB as MySQL

    Registrar->>Sec: PUT /api/v1/students/{code}
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: update(code, command)
    Svc->>Repo: findByCode(code)
    Repo->>DB: SELECT
    DB-->>Repo: row
    Repo-->>Svc: existing Student

    opt email changed
        Svc->>Repo: existsByEmail(newEmail, excluding=code)
        Repo->>DB: SELECT
        DB-->>Repo: result
        Repo-->>Svc: exists / malformed?
        alt email collides with another student or malformed (Student.2)
            Svc-->>Ctrl: InvalidEmailException
            Ctrl-->>Registrar: 400 / 409
        end
    end

    Svc->>Agg: applyChanges(firstName, lastName, email, dob)
    alt blank name (Student.3) or invalid DOB (Student.4)
        Agg-->>Svc: DomainValidationException
        Svc-->>Ctrl: (propagates)
        Ctrl-->>Registrar: 400 Bad Request
    else invariants hold
        Agg-->>Svc: updated Student
        Svc->>Repo: save(student)
        Repo->>DB: UPDATE students SET ...
        DB-->>Repo: OK
        Repo-->>Svc: saved
        Svc-->>Ctrl: StudentResponse
        Ctrl-->>Registrar: 200 OK
    end
```

### 2.3 UC-3: Remove Student

Registrar deletes a student. Removing the student itself is synchronous; unassigning their books and ending their enrollments happens through the async cascade detailed in §6.1 — this diagram shows only `student`'s own write path plus the event publish.

```mermaid
sequenceDiagram
    actor Registrar
    participant Sec as Spring Security
    participant Ctrl as StudentController
    participant Svc as StudentService
    participant Repo as JdbcStudentRepository
    participant DB as MySQL

    Registrar->>Sec: DELETE /api/v1/students/{code}
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: remove(code)
    Svc->>Repo: deleteByCode(code)
    Repo->>DB: DELETE FROM students WHERE code = ?
    DB-->>Repo: OK
    Repo-->>Svc: deleted
    Svc-)Svc: publish StudentDeleted (async — see §6.1 for book/enrollment cleanup)
    Svc-->>Ctrl: confirmation
    Ctrl-->>Registrar: 204 No Content
    Note over Svc: HTTP response does not wait for §6.1's listeners to finish
```

### 2.4 UC-13: View/Search Students

Registrar looks up a student by code, or searches by name/email.

```mermaid
sequenceDiagram
    actor Registrar
    participant Sec as Spring Security
    participant Ctrl as StudentController
    participant Svc as StudentService
    participant Repo as JdbcStudentRepository
    participant DB as MySQL

    Registrar->>Sec: GET /api/v1/students?query=...&page=0&size=20
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: search(query, pageable)
    Svc->>Repo: findByCodeOrNameOrEmail(query, pageable)
    Repo->>DB: SELECT ... WHERE ... LIMIT/OFFSET (+ COUNT for totalElements)
    DB-->>Repo: rows + count
    Repo-->>Svc: Page<Student>
    alt no match / page past the end
        Svc-->>Ctrl: empty page
        Ctrl-->>Registrar: 200 OK ({content: [], page, size, totalElements: 0, totalPages: 0})
    else match(es) found
        Svc-->>Ctrl: Page<StudentSummary>
        Ctrl-->>Registrar: 200 OK ({content: [summaries], page, size, totalElements, totalPages})
    end
    Note over Registrar: Selecting a result continues at UC-17 (§2.5)
```

### 2.5 UC-17: View Student Detail

Registrar selects one student from search results (UC-13) to see the full record, their owned books, and their active enrollments.

```mermaid
sequenceDiagram
    actor Registrar
    participant Sec as Spring Security
    participant Ctrl as StudentController
    participant Svc as StudentService
    participant Repo as JdbcStudentRepository
    participant BookSvc as BookService
    participant EnrollSvc as EnrollmentService
    participant DB as MySQL

    Registrar->>Sec: GET /api/v1/students/{code}
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: getDetail(code)
    Svc->>Repo: findByCode(code)
    Repo->>DB: SELECT
    DB-->>Repo: row
    Repo-->>Svc: result
    alt student no longer exists
        Svc-->>Ctrl: NotFoundException
        Ctrl-->>Registrar: 404 Not Found
    else student found
        par owned books
            Svc->>BookSvc: findByOwner(code)
            BookSvc-->>Svc: List<BookSummary>
        and active enrollments
            Svc->>EnrollSvc: findByStudent(code)
            EnrollSvc-->>Svc: List<CourseSummary>
        end
        Svc-->>Ctrl: StudentDetail (fields + books + courses)
        Ctrl-->>Registrar: 200 OK
    end
```

---

## 3. Book module

Owns UC-4, UC-5, UC-6, UC-7, UC-14, UC-18.

### 3.1 UC-4: Add Book

Librarian adds a book, optionally assigning it to a student on creation.

```mermaid
sequenceDiagram
    actor Librarian
    participant Sec as Spring Security
    participant Ctrl as BookController
    participant Svc as BookService
    participant Lookup as StudentLookup
    participant Agg as Book (aggregate)
    participant Repo as JdbcBookRepository
    participant DB as MySQL

    Librarian->>Sec: POST /api/v1/books
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: addBook(command)
    Svc->>Repo: existsByIsbn(isbn)
    Repo->>DB: SELECT
    DB-->>Repo: result
    Repo-->>Svc: exists?
    alt ISBN already exists (Book.1)
        Svc-->>Ctrl: DuplicateIsbnException
        Ctrl-->>Librarian: 409 Conflict
    else ISBN unique
        opt owner specified
            Svc->>Lookup: existsById(ownerId)
            Lookup-->>Svc: exists?
            alt owner does not exist (Book.4)
                Svc-->>Ctrl: UnknownStudentException
                Ctrl-->>Librarian: 400 Bad Request
            end
        end
        Svc->>Agg: create(isbn, title, author, publishedDate, ownerId?)
        Agg-->>Svc: Book instance
        Svc->>Repo: save(book)
        Repo->>DB: INSERT INTO books ...
        DB-->>Repo: OK
        Repo-->>Svc: saved
        Svc-->>Ctrl: BookResponse
        Ctrl-->>Librarian: 201 Created
    end
```

### 3.2 UC-5: Assign Book to Student

Librarian assigns (or reassigns) a book to a student. A book has at most one owner, so any prior owner is implicitly replaced.

```mermaid
sequenceDiagram
    actor Librarian
    participant Sec as Spring Security
    participant Ctrl as BookController
    participant Svc as BookService
    participant Lookup as StudentLookup
    participant Agg as Book (aggregate)
    participant Repo as JdbcBookRepository
    participant DB as MySQL

    Librarian->>Sec: PATCH /api/v1/books/{isbn}/owner
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: assign(isbn, studentId)
    Svc->>Lookup: existsById(studentId)
    Lookup-->>Svc: exists?
    alt target student does not exist (Book.4)
        Svc-->>Ctrl: UnknownStudentException
        Ctrl-->>Librarian: 400 Bad Request
    else student exists
        Svc->>Repo: findByIsbn(isbn)
        Repo->>DB: SELECT
        DB-->>Repo: row
        Repo-->>Svc: Book
        Svc->>Agg: changeOwner(studentId)
        Note over Agg: replaces any prior owner (Book.2 — at most one owner)
        Agg-->>Svc: updated Book
        Svc->>Repo: save(book)
        Repo->>DB: UPDATE books SET owner_id = ?
        DB-->>Repo: OK
        Repo-->>Svc: saved
        Svc-->>Ctrl: BookResponse
        Ctrl-->>Librarian: 200 OK
    end
```

### 3.3 UC-6: Unassign Book (End Ownership)

Librarian clears a book's ownership; the book stays in the catalog, unassigned.

```mermaid
sequenceDiagram
    actor Librarian
    participant Sec as Spring Security
    participant Ctrl as BookController
    participant Svc as BookService
    participant Agg as Book (aggregate)
    participant Repo as JdbcBookRepository
    participant DB as MySQL

    Librarian->>Sec: DELETE /api/v1/books/{isbn}/owner
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: unassign(isbn)
    Svc->>Repo: findByIsbn(isbn)
    Repo->>DB: SELECT
    DB-->>Repo: row
    Repo-->>Svc: Book
    Svc->>Agg: clearOwner()
    Note over Agg: book remains in catalog (Book.3, Book.5)
    Agg-->>Svc: updated Book
    Svc->>Repo: save(book)
    Repo->>DB: UPDATE books SET owner_id = NULL
    DB-->>Repo: OK
    Repo-->>Svc: saved
    Svc-->>Ctrl: BookResponse
    Ctrl-->>Librarian: 200 OK
```

### 3.4 UC-7: Remove Book

Librarian deletes a book outright. No other module is affected — removing a book never cascades.

```mermaid
sequenceDiagram
    actor Librarian
    participant Sec as Spring Security
    participant Ctrl as BookController
    participant Svc as BookService
    participant Repo as JdbcBookRepository
    participant DB as MySQL

    Librarian->>Sec: DELETE /api/v1/books/{isbn}
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: remove(isbn)
    Svc->>Repo: deleteByIsbn(isbn)
    Repo->>DB: DELETE FROM books WHERE isbn = ?
    DB-->>Repo: OK
    Repo-->>Svc: deleted
    Svc-->>Ctrl: confirmation
    Ctrl-->>Librarian: 204 No Content
    Note over Svc: no event published — previous owner (if any) is unaffected
```

### 3.5 UC-14: View/Search Books

Librarian looks up a book by ISBN, or searches by title/author with an optional owner filter.

```mermaid
sequenceDiagram
    actor Librarian
    participant Sec as Spring Security
    participant Ctrl as BookController
    participant Svc as BookService
    participant Repo as JdbcBookRepository
    participant DB as MySQL

    Librarian->>Sec: GET /api/v1/books?query=...&owner=...&page=0&size=20
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: search(query, ownerFilter, pageable)
    Svc->>Repo: findByIsbnOrTitleOrAuthor(query, ownerFilter, pageable)
    Repo->>DB: SELECT ... WHERE ... LIMIT/OFFSET (+ COUNT for totalElements)
    DB-->>Repo: rows + count
    Repo-->>Svc: Page<Book>
    alt no match / page past the end
        Svc-->>Ctrl: empty page
        Ctrl-->>Librarian: 200 OK ({content: [], page, size, totalElements: 0, totalPages: 0})
    else match(es) found
        Svc-->>Ctrl: Page<BookSummary>
        Ctrl-->>Librarian: 200 OK ({content: [summaries], page, size, totalElements, totalPages})
    end
    Note over Librarian: Selecting a result continues at UC-18 (§3.6)
```

### 3.6 UC-18: View Book Detail

Actor (Librarian, or a Student via UC-16) selects one book to see its full record, including the current owner's summary if assigned.

```mermaid
sequenceDiagram
    actor Caller as Librarian / Student
    participant Sec as Spring Security
    participant Ctrl as BookController
    participant Svc as BookService
    participant Repo as JdbcBookRepository
    participant Lookup as StudentLookup
    participant DB as MySQL

    Caller->>Sec: GET /api/v1/books/{isbn}
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: getDetail(isbn)
    Svc->>Repo: findByIsbn(isbn)
    Repo->>DB: SELECT
    DB-->>Repo: result
    alt book no longer exists
        Repo-->>Svc: not found
        Svc-->>Ctrl: NotFoundException
        Ctrl-->>Caller: 404 Not Found
    else book found
        Repo-->>Svc: Book
        opt book has an owner
            Svc->>Lookup: summaryOf(ownerId)
            Lookup-->>Svc: StudentSummary
        end
        Svc-->>Ctrl: BookDetail (fields + owner summary?)
        Ctrl-->>Caller: 200 OK
    end
```

---

## 4. Course module

Owns UC-8, UC-9, UC-10, UC-15, UC-19.

### 4.1 UC-8: Create Course

Course Administrator creates a new course offering.

```mermaid
sequenceDiagram
    actor CourseAdmin as Course Administrator
    participant Sec as Spring Security
    participant Ctrl as CourseController
    participant Svc as CourseService
    participant Agg as Course (aggregate)
    participant Repo as JdbcCourseRepository
    participant DB as MySQL

    CourseAdmin->>Sec: POST /api/v1/courses
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: create(command)
    Svc->>Repo: existsByCode(code)
    Repo->>DB: SELECT
    DB-->>Repo: result
    Repo-->>Svc: exists?
    alt course code already exists (Course.1)
        Svc-->>Ctrl: DuplicateCodeException
        Ctrl-->>CourseAdmin: 409 Conflict
    else code unique
        Svc->>Agg: create(code, name, description, credits)
        alt blank name (Course.2) or non-positive credits (Course.3)
            Agg-->>Svc: DomainValidationException
            Svc-->>Ctrl: (propagates)
            Ctrl-->>CourseAdmin: 400 Bad Request
        else invariants hold
            Agg-->>Svc: Course instance
            Svc->>Repo: save(course)
            Repo->>DB: INSERT INTO courses ...
            DB-->>Repo: OK
            Repo-->>Svc: saved
            Svc-->>Ctrl: CourseResponse
            Ctrl-->>CourseAdmin: 201 Created
        end
    end
```

### 4.2 UC-9: Update Course

Course Administrator updates a course's name, description, and/or credits. The course code never changes.

```mermaid
sequenceDiagram
    actor CourseAdmin as Course Administrator
    participant Sec as Spring Security
    participant Ctrl as CourseController
    participant Svc as CourseService
    participant Agg as Course (aggregate)
    participant Repo as JdbcCourseRepository
    participant DB as MySQL

    CourseAdmin->>Sec: PUT /api/v1/courses/{code}
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: update(code, command)
    Svc->>Repo: findByCode(code)
    Repo->>DB: SELECT
    DB-->>Repo: row
    Repo-->>Svc: existing Course
    Svc->>Agg: applyChanges(name, description, credits)
    alt blank name (Course.2) or non-positive credits (Course.3)
        Agg-->>Svc: DomainValidationException
        Svc-->>Ctrl: (propagates)
        Ctrl-->>CourseAdmin: 400 Bad Request
    else invariants hold
        Agg-->>Svc: updated Course
        Svc->>Repo: save(course)
        Repo->>DB: UPDATE courses SET ...
        DB-->>Repo: OK
        Repo-->>Svc: saved
        Svc-->>Ctrl: CourseResponse
        Ctrl-->>CourseAdmin: 200 OK
    end
```

### 4.3 UC-10: Remove Course

Course Administrator deletes a course. Removing the course itself is synchronous; removing every enrollment tied to it happens through the async cascade detailed in §6.2.

```mermaid
sequenceDiagram
    actor CourseAdmin as Course Administrator
    participant Sec as Spring Security
    participant Ctrl as CourseController
    participant Svc as CourseService
    participant Repo as JdbcCourseRepository
    participant DB as MySQL

    CourseAdmin->>Sec: DELETE /api/v1/courses/{code}
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: remove(code)
    Svc->>Repo: deleteByCode(code)
    Repo->>DB: DELETE FROM courses WHERE code = ?
    DB-->>Repo: OK
    Repo-->>Svc: deleted
    Svc-)Svc: publish CourseDeleted (async — see §6.2 for enrollment cleanup)
    Svc-->>Ctrl: confirmation
    Ctrl-->>CourseAdmin: 204 No Content
    Note over Svc: HTTP response does not wait for §6.2's listener to finish
```

### 4.4 UC-15: View/Search Courses

Course Administrator looks up a course by code or searches by name.

```mermaid
sequenceDiagram
    actor CourseAdmin as Course Administrator
    participant Sec as Spring Security
    participant Ctrl as CourseController
    participant Svc as CourseService
    participant Repo as JdbcCourseRepository
    participant DB as MySQL

    CourseAdmin->>Sec: GET /api/v1/courses?query=...&page=0&size=20
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: search(query, pageable)
    Svc->>Repo: findByCodeOrName(query, pageable)
    Repo->>DB: SELECT ... WHERE ... LIMIT/OFFSET (+ COUNT for totalElements)
    DB-->>Repo: rows + count
    Repo-->>Svc: Page<Course>
    alt no match / page past the end
        Svc-->>Ctrl: empty page
        Ctrl-->>CourseAdmin: 200 OK ({content: [], page, size, totalElements: 0, totalPages: 0})
    else match(es) found
        Svc-->>Ctrl: Page<CourseSummary>
        Ctrl-->>CourseAdmin: 200 OK ({content: [summaries], page, size, totalElements, totalPages})
    end
    Note over CourseAdmin: Selecting a result continues at UC-19 (§4.5)
```

### 4.5 UC-19: View Course Detail

Actor (Course Administrator, or a Student via UC-16) selects one course to see its full record and current roster.

```mermaid
sequenceDiagram
    actor Caller as Course Administrator / Student
    participant Sec as Spring Security
    participant Ctrl as CourseController
    participant Svc as CourseService
    participant Repo as JdbcCourseRepository
    participant EnrollSvc as EnrollmentService
    participant DB as MySQL

    Caller->>Sec: GET /api/v1/courses/{code}?page=0&size=20
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: getDetail(code, rosterPageable)
    Svc->>Repo: findByCode(code)
    Repo->>DB: SELECT
    DB-->>Repo: result
    alt course no longer exists
        Repo-->>Svc: not found
        Svc-->>Ctrl: NotFoundException
        Ctrl-->>Caller: 404 Not Found
    else course found
        Repo-->>Svc: Course
        Svc->>EnrollSvc: findRosterByCourse(code, rosterPageable)
        EnrollSvc-->>Svc: Page<StudentSummary>
        Svc-->>Ctrl: CourseDetail (fields + paged roster)
        Ctrl-->>Caller: 200 OK
    end
```

---

## 5. Enrollment module

Owns UC-11, UC-12, UC-20.

### 5.1 UC-11: Enroll Student in Course

Registrar enrolls a student in a course. Student self-service enrollment is out of scope. Both cross-module existence checks run before the duplicate-enrollment check.

```mermaid
sequenceDiagram
    actor Caller as Registrar
    participant Sec as Spring Security
    participant Ctrl as EnrollmentController
    participant Svc as EnrollmentService
    participant SLookup as StudentLookup
    participant CLookup as CourseLookup
    participant Agg as Enrollment (aggregate)
    participant Repo as JdbcEnrollmentRepository
    participant DB as MySQL

    Caller->>Sec: POST /api/v1/enrollments
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: enroll(studentId, courseCode)
    Svc->>SLookup: existsById(studentId)
    SLookup-->>Svc: exists?
    alt student does not exist (Enrollment.3)
        Svc-->>Ctrl: UnknownStudentException
        Ctrl-->>Caller: 400 Bad Request
    else student exists
        Svc->>CLookup: existsById(courseCode)
        CLookup-->>Svc: exists?
        alt course does not exist (Enrollment.2)
            Svc-->>Ctrl: UnknownCourseException
            Ctrl-->>Caller: 400 Bad Request
        else course exists
            Svc->>Repo: existsByStudentAndCourse(studentId, courseCode)
            Repo->>DB: SELECT
            DB-->>Repo: result
            Repo-->>Svc: exists?
            alt already enrolled (Enrollment.1)
                Svc-->>Ctrl: DuplicateEnrollmentException
                Ctrl-->>Caller: 409 Conflict
            else no existing enrollment
                Svc->>Agg: create(studentId, courseCode)
                Agg-->>Svc: Enrollment instance
                Svc->>Repo: save(enrollment)
                Repo->>DB: INSERT INTO enrollments ...
                DB-->>Repo: OK
                Repo-->>Svc: saved
                Svc-->>Ctrl: EnrollmentResponse
                Ctrl-->>Caller: 201 Created
            end
        end
    end
```

### 5.2 UC-12: End Enrollment

Registrar withdraws a student from a course. Student self-service withdrawal is out of scope. Only the enrollment link is removed — both the student and course records are unaffected.

```mermaid
sequenceDiagram
    actor Caller as Registrar
    participant Sec as Spring Security
    participant Ctrl as EnrollmentController
    participant Svc as EnrollmentService
    participant Repo as JdbcEnrollmentRepository
    participant DB as MySQL

    Caller->>Sec: DELETE /api/v1/enrollments/{studentId}/{courseCode}
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: end(studentId, courseCode)
    Svc->>Repo: deleteByStudentAndCourse(studentId, courseCode)
    Repo->>DB: DELETE FROM enrollments WHERE ...
    DB-->>Repo: OK
    Repo-->>Svc: deleted
    Svc-->>Ctrl: confirmation
    Ctrl-->>Caller: 204 No Content
    Note over Svc: Enrollment.4 — only the link is removed
```

### 5.3 UC-20: View Enrollment Detail

Actor (Registrar, Course Administrator, or Student) selects one enrollment from a student's list or a course roster to see both sides' summary info.

```mermaid
sequenceDiagram
    actor Caller as Registrar / Course Administrator / Student
    participant Sec as Spring Security
    participant Ctrl as EnrollmentController
    participant Svc as EnrollmentService
    participant Repo as JdbcEnrollmentRepository
    participant SLookup as StudentLookup
    participant CLookup as CourseLookup
    participant DB as MySQL

    Caller->>Sec: GET /api/v1/enrollments/{studentId}/{courseCode}
    Sec->>Ctrl: forward request (auth gate as in §2.1)
    Ctrl->>Svc: getDetail(studentId, courseCode)
    Svc->>Repo: findByStudentAndCourse(studentId, courseCode)
    Repo->>DB: SELECT
    DB-->>Repo: result
    alt enrollment no longer exists (ended after list was shown — Enrollment.4)
        Repo-->>Svc: not found
        Svc-->>Ctrl: NotFoundException
        Ctrl-->>Caller: 404 Not Found
    else enrollment found
        Repo-->>Svc: Enrollment
        par student summary
            Svc->>SLookup: summaryOf(studentId)
            SLookup-->>Svc: StudentSummary
        and course summary
            Svc->>CLookup: summaryOf(courseCode)
            CLookup-->>Svc: CourseSummary
        end
        Svc-->>Ctrl: EnrollmentDetail (student summary + course summary)
        Ctrl-->>Caller: 200 OK
    end
```

---

## 6. Cross-module cascades (async domain events)

Realizes the cleanup rules in `req.md` §5. Both are Spring Modulith event listeners — asynchronous, decoupled from the publisher's transaction (`02-component-diagram.md` §2.3).

### 6.1 StudentDeleted → book, enrollment cleanup

Fired by UC-3 (§2.3) after a student is deleted. `book` clears ownership on any books the student held; `enrollment` removes any enrollments the student held. Neither books nor courses are deleted — only the links to the removed student.

```mermaid
sequenceDiagram
    participant StudentSvc as StudentService
    participant BookSvc as BookService
    participant BookRepo as JdbcBookRepository
    participant EnrollSvc as EnrollmentService
    participant EnrollRepo as JdbcEnrollmentRepository
    participant DB as MySQL

    par book module listener
        StudentSvc-)BookSvc: StudentDeleted(studentId)
        BookSvc->>BookRepo: clearOwnerByStudentId(studentId)
        BookRepo->>DB: UPDATE books SET owner_id = NULL WHERE owner_id = ?
        DB-->>BookRepo: OK
    and enrollment module listener
        StudentSvc-)EnrollSvc: StudentDeleted(studentId)
        EnrollSvc->>EnrollRepo: deleteByStudentId(studentId)
        EnrollRepo->>DB: DELETE FROM enrollments WHERE student_id = ?
        DB-->>EnrollRepo: OK
    end
```

### 6.2 CourseDeleted → enrollment cleanup

Fired by UC-10 (§4.3) after a course is deleted. `enrollment` removes every enrollment tied to that course; previously enrolled students are otherwise unaffected.

```mermaid
sequenceDiagram
    participant CourseSvc as CourseService
    participant EnrollSvc as EnrollmentService
    participant EnrollRepo as JdbcEnrollmentRepository
    participant DB as MySQL

    CourseSvc-)EnrollSvc: CourseDeleted(courseCode)
    EnrollSvc->>EnrollRepo: deleteByCourseCode(courseCode)
    EnrollRepo->>DB: DELETE FROM enrollments WHERE course_code = ?
    DB-->>EnrollRepo: OK
```

---

## 7. Read-side composition

Owned by no single module (`02-component-diagram.md` §2.4).

### 7.1 UC-16: View Own Books, Courses & Enrollments

A Student views their own owned books and active enrollments in one request. The composing endpoint name below is illustrative — the SA docs describe this only as "a thin composing endpoint (or 3 scoped client calls)," without naming it — but either shape reads `book`, `enrollment`, and `student` independently, with no new write dependency.

```mermaid
sequenceDiagram
    actor Student
    participant Sec as Spring Security
    participant Ctrl as MeController (composition)
    participant BookSvc as BookService
    participant BookRepo as JdbcBookRepository
    participant EnrollSvc as EnrollmentService
    participant EnrollRepo as JdbcEnrollmentRepository
    participant DB as MySQL

    Student->>Sec: GET /api/v1/me/books-and-courses?booksPage=0&booksSize=20&coursesPage=0&coursesSize=20
    Sec->>Ctrl: forward request (auth gate as in §2.1, scoped to principal.studentId)
    par owned books
        Ctrl->>BookSvc: findByOwner(studentId, booksPageable)
        BookSvc->>BookRepo: findByOwnerId(studentId, booksPageable)
        BookRepo->>DB: SELECT ... LIMIT/OFFSET (+ COUNT)
        DB-->>BookRepo: rows + count
        BookRepo-->>BookSvc: Page<Book>
        alt no owned books / page past the end
            BookSvc-->>Ctrl: empty page
        else owned books exist
            BookSvc-->>Ctrl: Page<BookSummary>
        end
    and active enrollments
        Ctrl->>EnrollSvc: findByStudent(studentId, coursesPageable)
        EnrollSvc->>EnrollRepo: findByStudentId(studentId, coursesPageable)
        EnrollRepo->>DB: SELECT ... LIMIT/OFFSET (+ COUNT)
        DB-->>EnrollRepo: rows + count
        EnrollRepo-->>EnrollSvc: Page<Enrollment>
        alt no active enrollments / page past the end
            EnrollSvc-->>Ctrl: empty page
        else active enrollments exist
            EnrollSvc-->>Ctrl: Page<CourseSummary>
        end
    end
    Ctrl-->>Student: 200 OK (books: page, courses: page)
    Note over Student: Selecting a book → UC-18 (§3.6), selecting a course → UC-19 (§4.5)
```

---

## 8. Out of Scope (this document)

- Request/response DTO field lists and error envelope shape — tracked in the OpenAPI contract, per `02-component-diagram.md` §5.
- Database schema / column-level design — tracked separately.
- Login, the must-change-password gate, and the Change/Forgot Password and View Initial Password flows (UC-21, UC-22, UC-23) — see [04-authentication-authorization.md](./04-authentication-authorization.md).
- Transaction isolation levels, retry policy, and timing/performance characteristics of the async event listeners in §6 — an implementation concern, not an architectural one at this level.
