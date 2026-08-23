# Low-Level Design

Solution Architecture Document — Part 6 of 6 ([System Overview](./01-system-overview.md) → [Component Diagram](./02-component-diagram.md) → [Sequence Diagram](./03-sequence-diagrams.md) → [Authentication & Authorization](./04-authentication-authorization.md) → [Database Schema](./05-database-schema.md) → Low-Level Design).

This document extends [tactical-ddd-design.md](./tactical-ddd-design.md), which names the tactical DDD building blocks (Aggregates, Value Objects, Repositories, Application Services, Domain Events) and maps each to a class name already introduced in [02-component-diagram.md](./02-component-diagram.md) §3, but explicitly stops there: its own §13 Out of Scope reads *"Class-level Java signatures, package layout beyond what 02-component-diagram.md §3 already shows, or unit-test design — this document names patterns and maps them to existing class names; it does not write the code."* This document is that next layer down: concrete package layout, class/interface definitions, and method signatures (parameters, return types, thrown exceptions) for all five bounded-context modules, precise enough to type directly into `management/src/main/java`.

Nothing here is a new architectural or tactical decision — every class, method, and exception name is already implied by `02-component-diagram.md`, `03-sequence-diagrams.md`, `04-authentication-authorization.md`, `05-database-schema.md`, `tactical-ddd-design.md`, or `openapi/`. Where this document has to choose a concrete shape those documents leave implicit (e.g. an exception's exact superclass, a repository method not listed as "representative" in `tactical-ddd-design.md` §7), that choice is called out explicitly rather than presented as if it were already fixed.

---

## 1. Purpose & Scope

**In scope:**
- Package layout per module, following the `web/ → application/ → domain/ → port/ → internal/` shape `02-component-diagram.md` §3 fixes for `student` and extends to all five modules.
- Class and interface definitions: fields/attributes, constructors/factory methods, method signatures (parameter types, return types, checked/unchecked exceptions thrown).
- The shared exception hierarchy implied by the exception names already used throughout `03-sequence-diagrams.md` and `04-authentication-authorization.md`.
- Mermaid `classDiagram` views, one per module, showing how these classes connect.
- Persistence annotations on every `*Row` record and the runnable Flyway migration DDL (§9).
- Optimistic-locking strategy — which aggregates carry a `@Version`, and how a lost update surfaces as an HTTP response (§10).
- Spring Security bean wiring and `SecurityFilterChain` configuration, including the JSON login filter and the `MustChangePasswordFilter` registration (§11).
- MapStruct mapper method bodies for the domain-to-DTO conversions the annotation processor cannot infer on its own (§12).

**Out of scope** (see §15 for the full list): DTO field lists (owned by the OpenAPI contract) and unit-test design. Both are implementation-phase concerns that don't change the shape fixed here.

## 2. Conventions

### 2.1 Package root and per-module layout

Root package (from `management/pom.xml`): `org.phuchoang.management`. Each of the five modules is a direct child package, matching Spring Modulith's default module-detection (one top-level package per module):

```
org.phuchoang.management
├── shared        (cross-cutting: exceptions, global handler, security config)
├── student
├── course
├── book
├── enrollment
└── identity
```

Inside a module (`02-component-diagram.md` §3), the same five folders recur, plus a public API façade sitting beside `application/`:

```
org.phuchoang.management.<module>
├── web/            driving adapter — Controller, MapStruct Mapper, DTOs
├── application/    use-case orchestration — the Application Service, Commands
├── domain/         framework-free — Aggregate root, Value Objects
├── port/           interfaces owned by domain/application — Repository, and (identity only) PasswordHasher/PasswordCipher
├── <PublicApi>.java, <events>.java   published interfaces & domain events — module root, NOT internal/
└── internal/       driven adapter, invisible outside the module — Jdbc*Repository, *Row persistence records
```

`ApplicationModules.verify()` (Spring Modulith, run as a test) enforces that nothing outside a module imports from that module's `internal/` package — this is the mechanical backstop for the "public API, not a port" rule `02-component-diagram.md` §2.5 already argues from first principles.

### 2.2 Class-shape conventions

| Element | Java shape | Why |
| --- | --- | --- |
| Value Object | `record` (e.g. `record Email(String value)`), validating invariants in a compact constructor | Immutable, equality-by-value for free — exactly what §4 of `tactical-ddd-design.md` calls for |
| Aggregate root | Plain class, private mutable fields, package-visible/`@Getter` accessors, a static factory method, behavior methods that mutate `this` | Spring Data JDBC aggregates are conventionally mutable POJOs; `Lombok` (already a `pom.xml` dependency) supplies `@Getter`/`@RequiredArgsConstructor` boilerplate without adding a runtime framework dependency to `domain/` |
| Repository port | Interface in `port/`, only the methods the Application Service actually calls (never a bare `CrudRepository<T, ID>` leaked outward, per `02-component-diagram.md` §3) | Dependency-inversion boundary |
| Repository adapter | Class in `internal/`, implements the port, delegates to an injected Spring Data JDBC `CrudRepository` for CRUD and `@Query`/`JdbcAggregateOperations` for the rest | Keeps Spring Data JDBC entirely inside `internal/` |
| Application Service | Concrete class (no `Impl` suffix, no interface) in `application/` | Single implementation, thin orchestrator — an interface would add indirection with no second implementation to justify it |
| Command | `record` in `application/`, one per write use case, primitive/`String`-typed fields (VO construction happens inside the service/aggregate, not the command) | Matches the `command` parameter already named generically in every `03-sequence-diagrams.md` diagram (`Svc->>Svc: register(command)`) |
| Domain Event | `record`, placed at the *publishing* module's root (not `internal/`) since consuming modules need the type on their classpath | Same published-language reasoning `tactical-ddd-design.md` §10 applies to `StudentLookup` et al. |
| Exception | Unchecked (`extends RuntimeException`), under `shared.exception` | Spring's `@ControllerAdvice` idiom — see §3 |

### 2.3 Method-signature table format

Every method table below uses this column order: **Method**, **Parameters**, **Returns**, **Throws**. `Throws` lists only checked-equivalent *domain* exceptions callers must expect (unchecked, but load-bearing) — `RuntimeException`s a caller can't reasonably anticipate (e.g. a bug) are omitted.

## 3. Shared Exceptions & Kernel Types (`shared`)

`shared` owns the mechanism (`SecurityFilterChain`, `PasswordEncoder` bean, global exception handler — `04-authentication-authorization.md` §2.1) but no domain vocabulary of its own (`tactical-ddd-design.md` §2). Its one piece of shared *type* vocabulary is the exception hierarchy every module's `application/` throws, consolidating every exception name already used across `03-sequence-diagrams.md` and `04-authentication-authorization.md`:

```mermaid
classDiagram
    class ApiException {
        <<abstract>>
        #ApiException(String message)
    }
    class DomainValidationException
    class NotFoundException
    class ConflictException {
        <<abstract>>
    }
    class DuplicateCodeException
    class DuplicateIsbnException
    class DuplicateEmailException
    class DuplicateEnrollmentException
    class StaleWriteException
    class UnauthorizedException {
        <<abstract>>
    }
    class InvalidCredentialsException
    class InvalidEmailException
    class UnknownReferenceException {
        <<abstract>>
    }
    class UnknownStudentException
    class UnknownCourseException
    class PasswordNoLongerAvailableException

    ApiException <|-- DomainValidationException
    ApiException <|-- NotFoundException
    ApiException <|-- ConflictException
    ApiException <|-- UnauthorizedException
    ConflictException <|-- DuplicateCodeException
    ConflictException <|-- DuplicateIsbnException
    ConflictException <|-- DuplicateEmailException
    ConflictException <|-- DuplicateEnrollmentException
    ConflictException <|-- StaleWriteException
    UnauthorizedException <|-- InvalidCredentialsException
    DomainValidationException <|-- InvalidEmailException
    DomainValidationException <|-- UnknownReferenceException
    UnknownReferenceException <|-- UnknownStudentException
    UnknownReferenceException <|-- UnknownCourseException
    NotFoundException <|-- PasswordNoLongerAvailableException
```

| Exception | HTTP status | Raised where | Traces to |
| --- | --- | --- | --- |
| `DomainValidationException` | 400 | VO constructors (`StudentCode`, `DateOfBirth`, `Credits`, …), aggregate factory methods | Student.3/4, Course.2/3 |
| `InvalidEmailException` | 400 | `Email` VO constructor — malformed format only (duplicate is `DuplicateEmailException`, per `api-specification.md` §5's malformed-vs-duplicate split) | Student.2 |
| `NotFoundException` | 404 | `*Service.getDetail(...)` when the requested aggregate no longer exists | UC-17/18/19/20 "no longer exists" branches |
| `DuplicateCodeException` | 409 | `StudentService.register`, `CourseService.create` | Student.1, Course.1 |
| `DuplicateIsbnException` | 409 | `BookService.addBook` | Book.1 |
| `DuplicateEmailException` | 409 | `StudentService.register`/`update` | Student.2 |
| `DuplicateEnrollmentException` | 409 | `EnrollmentService.enroll` | Enrollment.1 |
| `StaleWriteException` | 409 | Any `*Service` write method (`update`, `assign`/`unassign`, `changePassword`) that calls `repository.save(...)` on a `Student`/`Course`/`Book`/`User` loaded with a `version` that no longer matches the row — see §10 | New in this document — closes the optimistic-locking gap `tactical-ddd-design.md` §13 left open |
| `UnknownStudentException` | 400 | `BookService` (owner ref), `EnrollmentService` (student ref) via `StudentLookup.idOf` returning empty | Book.4, Enrollment.3 |
| `UnknownCourseException` | 400 | `EnrollmentService` (course ref) via `CourseLookup.existsByCode` | Enrollment.2 |
| `InvalidCredentialsException` | 401 | Login (Spring Security `AuthenticationFailureHandler`), `IdentityService.changePassword` (current-password mismatch) | UC-21, UC-22 |
| `PasswordNoLongerAvailableException` | 404 | `IdentityService.viewInitialPassword` when `mustChangePassword = false` | Identity.4/5, UC-23 |
| `DuplicateUsernameException` | 409 | `IdentityService.provisionStaff` | Identity.2, Identity.6, UC-24 |
| `UserNotFoundException` | 404 | `IdentityService.setAccountEnabled` | UC-25 |
| Spring Security `DisabledException` (not an `ApiException` subtype — handled entirely inside the auth filter chain, same as `InvalidCredentialsException` at login) | 401 | `AppUserDetailsService.loadUserByUsername` when `!user.enabled()` | Identity.7, UC-21, UC-25 |

`shared.web.GlobalExceptionHandler` (`@RestControllerAdvice`) maps each branch of this hierarchy to the `Error`/`ValidationError` envelope already fixed in `api-specification.md` §3 (`timestamp`, `status`, `error`, `message`, `path`) — one `@ExceptionHandler(ApiException.class)` reading `getStatus()` off the exception, no per-exception-type handler methods needed.

## 4. Reference module: `student`

`student` is elaborated in full below; `course`, `book`, `enrollment`, and `identity` (§§5–8) reuse this exact shape and are described only where they differ, per `02-component-diagram.md` §3's own framing ("`course` is its twin; `book` and `enrollment` add one/two outbound API calls on top of the same shape").

### 4.1 Package layout

```
org.phuchoang.management.student
├── web
│   ├── StudentController.java
│   ├── StudentMapper.java              (MapStruct: DTO ⇄ domain)
│   └── dto/
│       ├── RegisterStudentRequest.java, UpdateStudentRequest.java
│       └── StudentResponse.java, StudentRegistrationResponse.java, StudentSummaryDto.java, StudentDetailDto.java
├── application/
│   ├── StudentService.java
│   └── command/
│       ├── RegisterStudentCommand.java
│       └── UpdateStudentCommand.java
├── domain/
│   ├── Student.java
│   ├── StudentId.java, StudentCode.java, Email.java, DateOfBirth.java
├── port/
│   └── StudentRepository.java
├── StudentLookup.java                  (public API)
├── StudentSummary.java                 (public API read model, also reused by web/ mapping)
├── StudentDeleted.java                 (domain event)
└── internal/
    ├── JdbcStudentRepository.java
    ├── SpringDataStudentRepository.java   (Spring Data JDBC CrudRepository<StudentRow, Long>)
    └── StudentRow.java                    (@Table("students") persistence record)
```

`StudentSummaryDto` (web) and `StudentSummary` (public API) hold the same fields by design — the web layer's `StudentMapper` maps the domain `Student` straight into both, and `book`/`enrollment` consume the public-API record directly. They stay two classes in two packages because `web/dto` is JSON/OpenAPI-contract-owned while the module root is Java-API-owned; a change to one must not force a change to the other's callers.

### 4.2 Class diagram

```mermaid
classDiagram
    class StudentController {
        -StudentService studentService
        -StudentMapper mapper
        +searchStudents(String query, Pageable pageable) Page~StudentSummaryDto~
        +registerStudent(RegisterStudentRequest request) StudentRegistrationResponse
        +getStudent(String code) StudentDetailDto
        +updateStudent(String code, UpdateStudentRequest request) StudentResponse
        +removeStudent(String code) void
    }
    class StudentService {
        -StudentRepository repository
        -AccountProvisioning accountProvisioning
        -BookService bookService
        -EnrollmentService enrollmentService
        -ApplicationEventPublisher events
        +register(RegisterStudentCommand command) ProvisionedStudent
        +update(StudentCode code, UpdateStudentCommand command) Student
        +remove(StudentCode code) void
        +search(String query, Pageable pageable) Page~Student~
        +getDetail(StudentCode code) StudentDetailView
    }
    class Student {
        -StudentId id
        -StudentCode code
        -String firstName
        -String lastName
        -Email email
        -DateOfBirth dateOfBirth
        -Instant createdAt
        -Instant updatedAt
        -long version
        +register(StudentCode code, String firstName, String lastName, Email email, DateOfBirth dob)$ Student
        +applyChanges(String firstName, String lastName, Email email, DateOfBirth dob) void
    }
    class StudentId
    class StudentCode
    class Email
    class DateOfBirth
    class StudentRepository {
        <<interface>>
        +findByCode(StudentCode code) Optional~Student~
        +existsByCode(StudentCode code) boolean
        +existsByEmail(Email email) boolean
        +existsByEmailExcludingCode(Email email, StudentCode excluding) boolean
        +search(String query, Pageable pageable) Page~Student~
        +save(Student student) Student
        +deleteByCode(StudentCode code) void
    }
    class JdbcStudentRepository {
        -SpringDataStudentRepository springRepo
        -JdbcAggregateOperations jdbcOps
    }
    class StudentLookup {
        <<interface>>
        +idOf(StudentCode code) Optional~StudentId~
        +summaryOf(StudentId id) StudentSummary
    }

    StudentController --> StudentService
    StudentController --> StudentMapper
    StudentService --> Student
    StudentService --> StudentRepository
    StudentService ..> StudentLookup : backs
    JdbcStudentRepository ..|> StudentRepository
    Student *-- StudentId
    Student *-- StudentCode
    Student *-- Email
    Student *-- DateOfBirth
```

### 4.3 Value Objects

| VO | Field | Compact-constructor invariant | Throws | Rule |
| --- | --- | --- | --- | --- |
| `StudentId(Long value)` | `value` | none — `null` before first `save()` (Spring Data JDBC's new-vs-existing detection via `@Id`), assigned by the DB on insert | — | — |
| `StudentCode(String value)` | `value` | non-blank, matches the registrar-supplied code format | `DomainValidationException` | Student.1 (format; uniqueness is `StudentRepository.existsByCode`) |
| `Email(String value)` | `value` | non-blank, RFC-5322-shaped (Jakarta `@Email`-equivalent regex) | `InvalidEmailException` | Student.2 (format; uniqueness is `StudentRepository.existsByEmail`) |
| `DateOfBirth(LocalDate value)` | `value` | non-null, not in the future, within a plausible human age range | `DomainValidationException` | Student.4 |

**`StudentCode` lives at the module root, not in `domain/`**, unlike the three VOs beside it in this
table. It is the Student's *published language* — the same status `course.CourseCode` already has —
because it is the only student identifier that crosses a module or process boundary: `book` and
`enrollment` both key their APIs on it, and every `web/` DTO carries it. §2.1's layering rule
forbids the Web layer from touching a Domain-layer type, so a `domain/` placement would have made
that impossible. `StudentId` sits at the root for the same reason and has since the start.

### 4.4 `Student` aggregate

| Method | Parameters | Returns | Throws |
| --- | --- | --- | --- |
| `register(...)` *(static factory)* | `StudentCode code, String firstName, String lastName, Email email, DateOfBirth dob` | `Student` | `DomainValidationException` (blank `firstName`/`lastName` — Student.3) |
| `applyChanges(...)` | `String firstName, String lastName, Email email, DateOfBirth dob` | `void` | `DomainValidationException` (blank name) |
| `id()`, `code()`, `firstName()`, `lastName()`, `email()`, `dateOfBirth()`, `createdAt()`, `updatedAt()`, `version()` | — | respective field types | — |

Uniqueness (Student.1/2) is deliberately **not** checked here — `StudentService` checks it via `StudentRepository` before calling `register(...)`, since the aggregate must not depend on the repository (`tactical-ddd-design.md` §8).

**`createdAt`/`updatedAt`/`version` — an addition beyond `tactical-ddd-design.md`'s field list, needed to close a gap this document found:** `StudentResponse` (`openapi/components/schemas/student.yaml`) requires `createdAt`/`updatedAt`, and `students.created_at`/`updated_at` exist in `05-database-schema.md` §3.1, but neither timestamp had a home on the aggregate for a mapper to read. `register(...)` sets both to `Instant.now()`; `applyChanges(...)` refreshes `updatedAt` only. These are set by the **application**, not read back from MySQL's `DEFAULT CURRENT_TIMESTAMP`/`ON UPDATE CURRENT_TIMESTAMP` — Spring Data JDBC doesn't re-fetch DB-computed column values after `save()` without an extra round-trip, so the DB-side defaults in §9's DDL are a safety net only, not the source of truth (the same "safety net, not replacement" framing `05-database-schema.md` §5 already uses for cascade deletes). `version` backs the optimistic-locking decision in §10; it starts at `0` in `register(...)` and is never touched directly by domain code — Spring Data JDBC increments it on every `save()`.

### 4.5 `StudentRepository` port / `JdbcStudentRepository` adapter

| Method | Parameters | Returns | Notes |
| --- | --- | --- | --- |
| `findByCode` | `StudentCode code` | `Optional<Student>` | Backs UC-2, UC-17 |
| `existsByCode` | `StudentCode code` | `boolean` | UC-1 |
| `existsByEmail` | `Email email` | `boolean` | UC-1 |
| `existsByEmailExcludingCode` | `Email email, StudentCode excluding` | `boolean` | UC-2 — "email changed" branch, `03-sequence-diagrams.md` §2.2 |
| `search` | `String query, Pageable pageable` | `Page<Student>` | UC-13 — matches code/name/email, paged |
| `save` | `Student student` | `Student` | Insert or update, decided by `id() == null` |
| `deleteByCode` | `StudentCode code` | `void` | UC-3 |

`JdbcStudentRepository` implements the port by delegating to an injected `SpringDataStudentRepository extends CrudRepository<StudentRow, Long>` (generated `findById`/`save`/`deleteById`) plus `@Query`-annotated finder methods for `existsByCode`/`existsByEmail...`/`search`, converting `StudentRow ⇄ Student` inline (a private mapping method, not MapStruct — `internal/` never imports `web/`).

### 4.6 `StudentService`

| Method | Parameters | Returns | Throws | Orchestration (`03-sequence-diagrams.md` ref) |
| --- | --- | --- | --- | --- |
| `register` | `RegisterStudentCommand command` | `ProvisionedStudent` (`record ProvisionedStudent(Student student, String username, String initialPassword)`) | `DuplicateCodeException`, `DuplicateEmailException`, `InvalidEmailException`, `DomainValidationException` | §2.1: existsByCode → existsByEmail → `Student.register(...)` → save → `AccountProvisioning.provisionForStudent(...)` (same transaction) |
| `update` | `StudentCode code, UpdateStudentCommand command` | `Student` | `DuplicateEmailException`, `InvalidEmailException`, `DomainValidationException` | §2.2: findByCode → (if email changed) existsByEmailExcludingCode → `applyChanges(...)` → save |
| `remove` | `StudentCode code` | `void` | — | §2.3: deleteByCode → publish `StudentDeleted` (async, after commit) |
| `search` | `String query, Pageable pageable` | `Page<Student>` | — | §2.4 |
| `getDetail` | `StudentCode code` | `StudentDetailView` — the record alone, nothing composed | `NotFoundException` | findByCode. Owned books and enrolled courses are **not** embedded: each is a separately paged, separately authorized read (`GET /api/v1/books?ownerStudentCode=` for the Librarian, `GET /api/v1/enrollments?studentCode=` for the Registrar and Course Administrator), and folding either in would hand every reader of a student record data their role may not see (§11.1) |

`viewStudentInitialPassword` (`GET /api/v1/students/{code}/initial-password`, UC-23) is **not** a `StudentController`/`StudentService` method despite its URL path — `04-authentication-authorization.md` §5.3's sequence lifeline shows it handled by `AuthController` → `IdentityService.viewInitialPassword`. The path lives under `/students` for REST-resource readability, but the handling class belongs to `identity` (§8).

### 4.7 `StudentController`

REST mapping `/api/v1/students`; method names match the OpenAPI `operationId`s in `openapi/paths/students.yaml` exactly, for direct traceability:

| Method | HTTP | Parameters | Returns |
| --- | --- | --- | --- |
| `searchStudents` | `GET /` | `String query` (optional), `Pageable pageable` (`page`/`size`) | `Page<StudentSummaryDto>` (200, empty `content` if none or past the last page) |
| `registerStudent` | `POST /` | `RegisterStudentRequest request` | `StudentRegistrationResponse` (201) |
| `getStudent` | `GET /{code}` | `String code` | `StudentDetailDto` (200) |
| `updateStudent` | `PUT /{code}` | `String code, UpdateStudentRequest request` | `StudentResponse` (200) |
| `removeStudent` | `DELETE /{code}` | `String code` | `void` (204) |

Each method: `StudentMapper` converts the incoming DTO to a `*Command`, calls `StudentService`, converts the result back to a response DTO. `GlobalExceptionHandler` (§3) turns thrown `ApiException`s into the 400/403/404/409 responses `books.yaml`/`students.yaml` document — no per-method `try/catch`.

### 4.8 `StudentLookup` (public API)

| Method | Parameters | Returns | Consumed by |
| --- | --- | --- | --- |
| `idOf` | `StudentCode code` | `Optional<StudentId>` | `BookService` (Book.4), `EnrollmentService` (Enrollment.3) |
| `summaryOf` | `StudentId id` | `StudentSummary` | `BookService.getDetail`, `EnrollmentService.getDetail`/`search` |
| `profileOf` | `StudentId id` | `Optional<StudentProfile>` | `MeController` (US-5.4, `GET /api/v1/me/profile`) |

`idOf` is **the single `StudentCode` → `StudentId` translation point in the system**. Every API input
names a student by its business key, while `books.owner_id` and `enrollments.student_id` are keyed on
the surrogate id — `book` and `enrollment` resolve one to the other here rather than reaching into
`student`'s repository. It replaces the `existsById` check those modules used while the API was
still keyed on ids, folding the existence check and the resolution into one call: an empty result
*is* "no such student", which both callers turn into `UnknownStudentException`.

`profileOf` returns `Optional` rather than throwing, unlike `summaryOf`: `me` resolves its id from
the session principal, which outlives a student row a Registrar deleted mid-session, and rendering
that case is the caller's job. `StudentProfile` is a root-level read-model carrying
`(studentCode, firstName, lastName, email, dateOfBirth)` — distinct from `StudentSummary` because
`/me/profile` shows the full record, where a summary is only ever an embedded reference.

`StudentService` implements `StudentLookup` directly (`02-component-diagram.md` §2.1: "exposes"); no separate façade class, since the read path needs no logic beyond delegating to `StudentRepository`.

---

## 5. `course` module

Identical shape to `student` — no outbound cross-module calls, `CourseService` implements `CourseLookup` the same way `StudentService` implements `StudentLookup`, `Course` has one behavior method beyond its factory (`applyChanges`).

**`enrolledCount` (`api-specification.md` §5 decision #11) does not change that "no outbound calls" statement**, because it is not fetched by one. `CourseRepository` gains two methods, and `JdbcCourseRepository` answers both with SQL against the `enrollments` table:

| Port method | Backing query | Used by |
| --- | --- | --- |
| `enrollmentCountsFor(Collection<String> courseCodes) : Map<String, Long>` | `SELECT c.course_code, COUNT(e.id) FROM courses c LEFT JOIN enrollments e ON e.course_id = c.id WHERE c.course_code IN (:courseCodes) GROUP BY c.course_code` | `CourseService.search` — one query for the whole page, not one per row |
| `enrollmentCountOf(CourseCode code) : long` | The same shape as `SpringDataEnrollmentRepository.countByCourseCode` | `CourseService.getDetail` |

Four points of care:

- **`LEFT JOIN`, not `JOIN`.** A course nobody is enrolled in must return `0`, not drop out of the result — otherwise the caller cannot distinguish "no enrollments" from "no such course".
- **The empty-collection guard is required, not an optimisation.** `IN ()` is a syntax error in MySQL, and an empty page is an ordinary search result, so `enrollmentCountsFor` returns `Map.of()` without querying when handed nothing.
- **`search` and `getDetail` become `@Transactional(readOnly = true)`.** They previously carried no transaction at all, which was fine for a single statement; each is now two, and they should see one snapshot.
- **The join result needs its own Row type.** `CourseEnrollmentCountRow(String courseCode, long enrolledCount)` — a plain record in `internal/`, materialised by Spring Data JDBC as an ad-hoc entity, exactly as `EnrollmentCourseRow` already is (§9.1). It is not folded into `CourseRow`, whose shape belongs to the aggregate that gets saved.

`CourseSummaryView`/`CourseDetailView` and `CourseSummaryDto`/`CourseDetailDto` each gain the field; MapStruct maps it by name, so `CourseMapper` (§12) is unchanged. `CourseResponse` deliberately does not — see the schema note in `openapi/components/schemas/course.yaml`.

The module-cycle reasoning for reading another module's table in SQL is the same one §9.1 already records for `enrollment` reading `courses`, now applied in both directions; `02-component-diagram.md` §2.2 states the consequence.

```mermaid
classDiagram
    class CourseController {
        +searchCourses(String query, Pageable pageable) Page~CourseSummaryDto~
        +createCourse(CourseCreateRequest request) CourseResponse
        +getCourse(String code) CourseDetailDto
        +updateCourse(String code, CourseUpdateRequest request) CourseResponse
        +removeCourse(String code) void
    }
    class CourseService {
        -CourseRepository repository
        -EnrollmentService enrollmentService
        -ApplicationEventPublisher events
        +create(CreateCourseCommand command) Course
        +update(CourseCode code, UpdateCourseCommand command) Course
        +remove(CourseCode code) void
        +search(String query, Pageable pageable) Page~Course~
        +getDetail(CourseCode code) CourseDetailView
    }
    class Course {
        -CourseId id
        -CourseCode code
        -String name
        -String description
        -Credits credits
        -Instant createdAt
        -Instant updatedAt
        -long version
        +create(CourseCode code, String name, String description, Credits credits)$ Course
        +applyChanges(String name, String description, Credits credits) void
    }
    class CourseRepository {
        <<interface>>
        +findByCode(CourseCode code) Optional~Course~
        +existsByCode(CourseCode code) boolean
        +search(String query, Pageable pageable) Page~Course~
        +save(Course course) Course
        +deleteByCode(CourseCode code) void
    }
    class CourseLookup {
        <<interface>>
        +existsByCode(CourseCode code) boolean
        +summaryOf(CourseCode code) CourseSummary
    }
    CourseController --> CourseService
    CourseService --> Course
    CourseService --> CourseRepository
    CourseService ..> CourseLookup : backs
    Course *-- CourseId
    Course *-- CourseCode
    Course *-- Credits
```

| Difference from `student` | Detail |
| --- | --- |
| Value Objects | `CourseId`, `CourseCode` (mirror `StudentId`/`StudentCode`); `Credits(int value)` — compact constructor throws `DomainValidationException` if `value <= 0` (Course.3). `name`/`description` stay plain `String` fields — no VO, since neither has a format invariant beyond `name`'s non-blank check, which lives in `Course.create`/`applyChanges` directly |
| `createdAt`/`updatedAt`/`version` | Same addition and same reasoning as `Student` (§4.4) — `CourseResponse` requires the timestamps, `courses` carries `version` per §10 |
| No read composition | `CourseService.getDetail` returns the course record alone. The enrolled-student roster is its own read — `GET /api/v1/enrollments?courseCode=`, open to the Registrar and Course Administrator but not to a Student browsing the catalogue (§11.1) — so a Student never receives the names and email addresses of everyone else taking a course as a side effect of opening it. This also removes `course`'s only outbound dependency on `enrollment`, which was the one edge that would have inverted the module graph |
| No `initial-password`-style routing quirk | `CourseController` owns all 5 of its endpoints directly, unlike `student`'s UC-23 |
| Event published | `CourseDeleted(CourseCode courseCode)`, published from `remove(...)` the same way `StudentDeleted` is |

---

## 6. `book` module

No public API of its own (nothing depends on `book`); it is a **consumer** of `student.StudentLookup`.

```mermaid
classDiagram
    class BookController {
        +searchBooks(String query, String ownerStudentCode, Pageable pageable) Page~BookSummaryDto~
        +addBook(BookCreateRequest request) BookResponse
        +getBook(String isbn) BookDetailDto
        +assignBookOwner(String isbn, BookOwnerRequest request) BookResponse
        +clearBookOwner(String isbn) BookResponse
        +removeBook(String isbn) void
    }
    class BookService {
        -BookRepository repository
        -StudentLookup studentLookup
        +addBook(AddBookCommand command) Book
        +assignOwner(String isbn, AssignBookOwnerCommand command) AssignedBook
        +unassign(Isbn isbn) Book
        +remove(Isbn isbn) void
        +search(String query, StudentId ownerFilter, Pageable pageable) Page~Book~
        +getDetail(Isbn isbn) BookDetailView
        +findByOwner(StudentId ownerId, Pageable pageable) Page~BookSummary~
    }
    class Book {
        -BookId id
        -Isbn isbn
        -String title
        -String author
        -LocalDate publishedDate
        -StudentId ownerId
        -Instant createdAt
        -Instant updatedAt
        -long version
        +create(Isbn isbn, String title, String author, LocalDate publishedDate, StudentId ownerId)$ Book
        +changeOwner(StudentId newOwnerId) void
        +clearOwner() void
    }
    class BookRepository {
        <<interface>>
        +findByIsbn(Isbn isbn) Optional~Book~
        +existsByIsbn(Isbn isbn) boolean
        +findByOwnerId(StudentId ownerId, Pageable pageable) Page~Book~
        +clearOwnerByStudentId(StudentId studentId) void
        +search(String query, StudentId ownerFilter, Pageable pageable) Page~Book~
        +save(Book book) Book
        +deleteByIsbn(Isbn isbn) void
    }
    BookController --> BookService
    BookService --> Book
    BookService --> BookRepository
    BookService --> StudentLookup : validates owner
    Book *-- BookId
    Book *-- Isbn
    Book o-- StudentId : ownerId (nullable)
```

| Difference from `student` | Detail |
| --- | --- |
| Value Objects | `BookId`, `Isbn(String value)` — compact constructor throws `DomainValidationException` on malformed ISBN-10/13 (Book.1 format; uniqueness is `existsByIsbn`). `ownerId` is typed `StudentId`, taken from `student`'s module root (see the note below); the *code* the API accepts is `student.StudentCode`, resolved to that id in `BookService` |
| `createdAt`/`updatedAt`/`version` | Same addition and same reasoning as `Student` (§4.4) — `BookResponse` requires the timestamps, `books` carries `version` per §10 (owner reassignment is exactly the kind of concurrent-write race optimistic locking guards against — two librarians assigning the same book to different students at once) |
| Cross-module dependency | `BookService` constructor-injects `StudentLookup`; `addBook` and `assignOwner` both call `idOf(ownerStudentCode)` before touching the aggregate (Book.4) — an empty result throws `UnknownStudentException`. The owner is named by `StudentCode` on the way in and rendered as `ownerStudentCode` on the way out; `books.owner_id` never appears in a request or a response (api-specification.md §5 decision #9) |
| No update method | Book has no `update` use case — only `assign`/`unassign`/`remove`; `title`/`author`/`publishedDate` are set once at `create` and never changed by any UC |
| No `BookLookup` public API | `book` is a pure consumer in this design — nothing currently needs to ask `book` a question the way `book` asks `student`/`course` |
| One `findByOwner` | `findByOwner(StudentId, Pageable)`, backing `GET /api/v1/me/books` (UC-16). The unpaginated overload that fed `StudentService.getDetail`'s embedded "owned books" list is gone with that list: a Librarian reads a student's loans through `GET /api/v1/books?ownerStudentCode=`, which is paged like every other search. Same simplification on `BookRepository.findByOwnerId` |
| No event published | `remove(Isbn isbn)` does **not** publish an event — removing a book never cascades (`03-sequence-diagrams.md` §3.4) |
| Event listener | `onStudentDeleted(StudentDeleted event)` → `repository.clearOwnerByStudentId(event.studentId())` — see §13 |

**Note on `StudentId` reuse across modules:** `tactical-ddd-design.md` §4 lists `StudentId` as used by `Book.ownerId`, `Enrollment.studentId`, and `User.studentId` — i.e. it is conceptually a *shared* value type, not `student`-internal. This document places the canonical `StudentId` definition at `student`'s module root (public, not `internal/`) precisely so `book`, `enrollment`, and `identity` can depend on the type itself while still only calling `student`'s behavior through `StudentLookup` — the type crossing the boundary is data, not logic, the same exemption domain events get in §2.2's table. The equivalent applies to `course.CourseCode` used by `Enrollment.courseCode`, and to `student.StudentCode`, which sits at the same root for the same reason (§4.3) — it is what `book` and `enrollment` accept from callers before resolving it to an id.

---

## 7. `enrollment` module

Consumes **both** `StudentLookup` and `CourseLookup`; owns no update use case (only create/end).

```mermaid
classDiagram
    class EnrollmentController {
        +searchEnrollments(String studentCode, String courseCode, Pageable pageable) PageResponse~EnrollmentDetailDto~
        +createEnrollment(EnrollmentCreateRequest request) EnrollmentResponse
        +getEnrollment(String studentCode, String courseCode) EnrollmentDetailDto
        +endEnrollment(String studentCode, String courseCode) void
    }
    class EnrollmentService {
        -EnrollmentRepository repository
        -StudentLookup studentLookup
        -CourseLookup courseLookup
        +enroll(EnrollStudentCommand command) CreatedEnrollment
        +end(String studentCode, String courseCode) void
        +getDetail(String studentCode, String courseCode) EnrollmentDetailView
        +search(String studentCode, String courseCode, Pageable pageable) Page~EnrollmentDetailView~
        +findByStudent(StudentId studentId, Pageable pageable) Page~CourseSummary~
    }
    class Enrollment {
        -EnrollmentId id
        -StudentId studentId
        -CourseCode courseCode
        -Instant enrolledAt
        +create(StudentId studentId, CourseCode courseCode)$ Enrollment
    }
    class EnrollmentRepository {
        <<interface>>
        +existsByStudentAndCourse(StudentId studentId, CourseCode courseCode) boolean
        +findByStudentAndCourse(StudentId studentId, CourseCode courseCode) Optional~Enrollment~
        +findByStudentId(StudentId studentId, Pageable pageable) Page~Enrollment~
        +findByCourseCode(CourseCode courseCode, Pageable pageable) Page~Enrollment~
        +save(Enrollment enrollment) Enrollment
        +deleteByStudentAndCourse(StudentId studentId, CourseCode courseCode) void
        +deleteByStudentId(StudentId studentId) void
        +deleteByCourseCode(CourseCode courseCode) void
    }
    EnrollmentController --> EnrollmentService
    EnrollmentService --> Enrollment
    EnrollmentService --> EnrollmentRepository
    EnrollmentService --> StudentLookup : validates student
    EnrollmentService --> CourseLookup : validates course
    Enrollment *-- EnrollmentId
    Enrollment o-- StudentId
    Enrollment o-- CourseCode
```

| Difference from `student` | Detail |
| --- | --- |
| Value Objects | `EnrollmentId` only — `studentId`/`courseCode` reuse `student.StudentId`/`course.CourseCode` directly (§6's reuse note); no `Enrollment`-owned VO wraps a format invariant of its own, since Enrollment.1–4 are all cross-aggregate existence/uniqueness rules, not format rules |
| `enrolledAt` | Same class of gap as `Student`/`Course`/`Book`'s `createdAt`/`updatedAt` (§4.4): `EnrollmentResponse`/`EnrollmentDetail` require it, `enrollments.enrolled_at` exists in `05-database-schema.md` §3.4, but the aggregate had no field for it. `create(...)` sets it once to `Instant.now()`; there is no `updatedAt` counterpart because, per the "no update" row below, nothing about an `Enrollment` ever changes after creation |
| Two published-interface dependencies | `enroll(...)` calls `studentLookup.idOf` **then** `courseLookup.existsByCode` **then** `repository.existsByStudentAndCourse` — that exact order, per `03-sequence-diagrams.md` §5.1. `idOf` does double duty: it is the Enrollment.3 student-exists check *and* the `StudentCode` → `StudentId` resolution the FK needs, so the ordering is unchanged from when this step was a bare `existsById` |
| Addressed by business key | Every method on the controller and the service names a student by `StudentCode`, never `StudentId` (api-specification.md §5 decision #9). The surrogate id is resolved once per call and never leaves `EnrollmentService`; the repository port stays typed in `StudentId`, because `enrollments.student_id` is the column it actually matches on |
| `search` | UC-11/UC-20's list view: every enrollment of one student, or every enrollment in one course, paged. Exactly one filter is required — with neither this would enumerate every enrollment in the system, which no use case asks for; with both, the answer is a single enrollment that `getDetail` already addresses. Both directions return the same `EnrollmentDetailView` shape, so one client renders "this student's courses" and another "this course's roster" off one schema. The **constant** side of a page is resolved once, outside the per-row mapping, so a page of 20 costs one lookup for that side rather than 20 identical ones |
| `findByStudent` | The one `EnrollmentLookup` method, backing `GET /api/v1/me/courses` (US-5.4). Takes a `StudentId` rather than a code, because its caller already holds one from the session principal and needs no resolution |
| No `update` | Enrollment.4 — "end removes only the link" — there is no field on `Enrollment` any UC ever changes after creation |
| No caller-scoping parameter | `getDetail` used to take a `callerStudentId` and 403 a Student reading someone else's enrollment. `SecurityConfig` now keeps Students off `/api/v1/enrollments/**` entirely (§11.1), so that branch is unreachable and has been removed — withdrawing the grant is strictly safer than scoping it, because there is no comparison left to get wrong |
| Two error vocabularies for one missing code | An unresolvable `studentCode` means different things depending on what the caller is addressing, and the two produce different statuses on purpose. Supplied as a **reference** (`enroll`, `search`) it is malformed input — `400`, matching every other unknown-FK case (api-specification.md §5 decision #2). Supplied as part of the **address** of one enrollment (`getDetail`, `end`) it makes that enrollment unaddressable — `404`, identical to a valid student with no such enrollment, so no student-existence signal leaks through a differently-shaped error |
| Two event listeners | `onStudentDeleted` → `deleteByStudentId`; `onCourseDeleted` → `deleteByCourseCode` — see §13 |

---

### 7.1 `EnrollmentBatchService` (UC-26)

A second `@Service` in `enrollment/application/`, holding the bulk-enrollment endpoint's logic. Two design points, both load-bearing:

**It is a separate bean, not a method on `EnrollmentService`.** `@Transactional` is honoured by a proxy, so a loop inside `EnrollmentService` calling `this.enroll(...)` is self-invocation: the proxy is bypassed, `enroll`'s annotation never applies, and every course shares one transaction — the exact all-or-nothing behaviour the use case exists to avoid. Injecting `EnrollmentService` into a distinct bean crosses the proxy, so each `enroll` call opens and commits its own transaction (propagation `REQUIRED` with no outer transaction present).

**It is itself not `@Transactional`.** Annotating it would reintroduce the outer transaction the separate bean was created to avoid.

```java
package org.phuchoang.management.enrollment.application;

@Service
public class EnrollmentBatchService {

  private final EnrollmentService enrollmentService;   // injected -> proxied
  private final StudentLookup studentLookup;

  public BatchEnrollmentResult enrollAll(BatchEnrollStudentCommand command) {
    StudentCode studentCode = new StudentCode(command.studentCode());
    studentLookup.idOf(studentCode)
        .orElseThrow(() -> new UnknownStudentException(...));   // whole-request 400

    List<String> courseCodes = List.copyOf(new LinkedHashSet<>(command.courseCodes()));
    List<BatchEnrollmentOutcome> outcomes = new ArrayList<>(courseCodes.size());
    for (String courseCode : courseCodes) {
      outcomes.add(enrollOne(studentCode.value(), courseCode));
    }
    return new BatchEnrollmentResult(studentCode.value(), outcomes);
  }
}
```

`enrollOne` catches `UnknownCourseException`, `DuplicateEnrollmentException` and `DomainValidationException` per course and converts each into an `Outcome`. It deliberately does **not** catch `UnknownStudentException`: reaching it means the student was deleted between the check above and this row, which invalidates the request's precondition rather than this one course, so it propagates as the same 400.

Result records: `Outcome` (`ENROLLED`, `UNKNOWN_COURSE`, `ALREADY_ENROLLED`, `INVALID_COURSE_CODE`), `BatchEnrollmentOutcome(courseCode, outcome, enrolledAt, message)`, and `BatchEnrollmentResult(studentCode, outcomes)` with derived `enrolledCount()`/`failedCount()`. Those two are mapped by `@Mapping(expression = ...)` rather than `source`, because MapStruct treats only a record's *components* as properties (§12).

Validation lives on the request DTO — `@NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 20) String> courseCodes`. The container-element constraints report paths like `courseCodes[2]`, which `GlobalExceptionHandler` already folds into the standard `ValidationError`.

**No `SecurityConfig` change is needed:** `POST /api/v1/enrollments/**` is already `hasRole("REGISTRAR")` and covers `/batch` (§11.1).

## 8. `identity` module

The one module whose domain layer needs infrastructure collaborators (hashing, encryption, password generation) passed in rather than field-injected, to keep `domain/` framework-free per `02-component-diagram.md` §3.

```mermaid
classDiagram
    class AuthController {
        +changePassword(ChangePasswordRequest request) void
        +viewStudentInitialPassword(String studentCode) InitialPasswordResponse
        +listDemoAccounts() List~DemoAccountResponse~
    }
    class StaffAccountController {
        +createStaffAccount(CreateStaffAccountRequest request) StaffAccountResponse
        +setStatus(Long userId, SetStatusRequest request) StaffAccountResponse
    }
    class AppUserDetailsService {
        <<Spring Security UserDetailsService>>
        -UserRepository repository
        +loadUserByUsername(String username) UserDetails
    }
    class MustChangePasswordFilter {
        <<OncePerRequestFilter>>
        +doFilterInternal(request, response, chain) void
    }
    class IdentityService {
        -UserRepository repository
        -PasswordHasher hasher
        -PasswordCipher cipher
        -InitialPasswordGenerator passwordGenerator
        +provisionForStudent(StudentId studentId, Email email) ProvisionedAccount
        +provisionStaff(ProvisionStaffCommand command) ProvisionedAccount
        +setAccountEnabled(UserId userId, boolean enabled) User
        +changePassword(ChangePasswordCommand command) void
        +viewInitialPassword(StudentCode studentCode) InitialPasswordView
        +studentIdOf(Authentication authentication) Optional~StudentId~
        +listDemoAccounts() List~DemoAccount~
    }
    class User {
        -UserId id
        -Username username
        -PasswordHash passwordHash
        -EncryptedInitialPassword initialPasswordEncrypted
        -Role role
        -StudentId studentId
        -boolean mustChangePassword
        -boolean enabled
        -long version
        +provisionForStudent(Username username, StudentId studentId, String plaintextPassword, PasswordHasher hasher, PasswordCipher cipher)$ User
        +provisionStaff(Username username, Role role, String plaintextPassword, PasswordHasher hasher, PasswordCipher cipher)$ User
        +changePassword(String newPlaintext, PasswordHasher hasher) void
        +setEnabled(boolean enabled) void
    }
    class PasswordHasher {
        <<interface>>
        +hash(String plaintext) PasswordHash
        +matches(String plaintext, PasswordHash hash) boolean
    }
    class PasswordCipher {
        <<interface>>
        +encrypt(String plaintext) EncryptedInitialPassword
        +decrypt(EncryptedInitialPassword ciphertext) String
    }
    class InitialPasswordGenerator {
        <<interface>>
        +generate() String
    }
    class UserRepository {
        <<interface>>
        +findByUsername(Username username) Optional~User~
        +findByStudentCode(StudentCode studentCode) Optional~User~
        +existsByUsername(Username username) boolean
        +save(User user) User
        +deleteByStudentId(StudentId studentId) void
    }
    class AccountProvisioning {
        <<interface>>
        +provisionForStudent(StudentId studentId, Email email) ProvisionedAccount
    }
    class PrincipalStudentResolver {
        <<interface>>
        +studentIdOf(Authentication authentication) Optional~StudentId~
    }

    AuthController --> IdentityService
    StaffAccountController --> IdentityService
    AppUserDetailsService --> UserRepository
    IdentityService --> User
    IdentityService --> UserRepository
    IdentityService --> PasswordHasher
    IdentityService --> PasswordCipher
    IdentityService --> InitialPasswordGenerator
    IdentityService ..|> AccountProvisioning
    IdentityService ..|> PrincipalStudentResolver
    User *-- UserId
    User *-- Username
    User *-- PasswordHash
    User *-- EncryptedInitialPassword
    User o-- StudentId
```

### 8.1 Value Objects & the password-handling ports

| Type | Notes |
| --- | --- |
| `UserId(Long value)`, `Username(String value)` | Mirror `StudentId`/`StudentCode`'s nullable-before-save / non-blank pattern |
| `PasswordHash(String value)` | Wraps a 60-char BCrypt digest; no public constructor validation beyond non-blank — the *policy* (min length, etc.) is checked on the plaintext, before hashing, in `IdentityService.changePassword` |
| `EncryptedInitialPassword(String cipherText)` — nullable field on `User`, not a null-safe VO itself | Represents the AES ciphertext; `User` holds `EncryptedInitialPassword` or `null`, matching `initial_password_encrypted`'s nullable column (`05-database-schema.md` §3.5) |
| `Role` | `enum { SYSTEM_ADMINISTRATOR, REGISTRAR, LIBRARIAN, COURSE_ADMINISTRATOR, STUDENT }`. `Role.STAFF_ROLES = {REGISTRAR, LIBRARIAN, COURSE_ADMINISTRATOR}` — the constrained subset `User.provisionStaff(...)` accepts (`04-auth.md` §3a); `SYSTEM_ADMINISTRATOR` and `STUDENT` are never valid arguments to it. |
| `PasswordHasher` (`port/`) | Domain-owned interface — **not** Spring Security's `PasswordEncoder` directly, so `domain/User` stays framework-free while still calling `hasher.hash(...)`/`hasher.matches(...)`. `internal/BCryptPasswordHasher` implements it, wrapping the `PasswordEncoder` bean `shared` configures |
| `PasswordCipher` (`port/`) | Same reasoning for the reversible AES step. `internal/AesPasswordCipher` implements it |
| `InitialPasswordGenerator` (`port/`, or `application/` — either is defensible since it has no infrastructure dependency beyond `SecureRandom`) | The one genuine Domain Service candidate identified in `tactical-ddd-design.md` §5; `internal/SecureRandomInitialPasswordGenerator implements InitialPasswordGenerator`, `generate(): String` returns an 8-char alphanumeric string |

### 8.2 `User` aggregate

| Method | Parameters | Returns | Throws | Rule |
| --- | --- | --- | --- | --- |
| `provisionForStudent(...)` *(static factory)* | `Username username, StudentId studentId, String plaintextPassword, PasswordHasher hasher, PasswordCipher cipher` | `User` | — | Sets `role = STUDENT`, `mustChangePassword = true`, `enabled = true` (Identity.3); `passwordHash = hasher.hash(plaintextPassword)`, `initialPasswordEncrypted = cipher.encrypt(plaintextPassword)` |
| `provisionStaff(...)` *(static factory)* | `Username username, Role role, String plaintextPassword, PasswordHasher hasher, PasswordCipher cipher` | `User` | — (caller, `IdentityService.provisionStaff`, already validated `role ∈ Role.STAFF_ROLES` before calling) | Sets `studentId = null`, `mustChangePassword = true`, `enabled = true` (Identity.3, Identity.6); hashing/encryption identical to `provisionForStudent` |
| `changePassword(...)` | `String newPlaintext, PasswordHasher hasher` | `void` | — | `passwordHash = hasher.hash(newPlaintext)`; `initialPasswordEncrypted = null`; `mustChangePassword = false` (Identity.4/5) |
| `setEnabled(...)` | `boolean enabled` | `void` | — | Sets `this.enabled = enabled` (Identity.7); no other field changes — deactivation is not a soft-delete of any other state |
| `matchesCurrentPassword` | `String plaintext, PasswordHasher hasher` | `boolean` | — | Thin delegate to `hasher.matches(plaintext, this.passwordHash)`, kept on the aggregate so `IdentityService` never reaches into `passwordHash` directly |

Collaborators (`PasswordHasher`, `PasswordCipher`) are passed as **method parameters**, not constructor-injected fields — the standard DDD answer for "an aggregate needs a stateless policy it must not own a live dependency on" (Evans' "Domain Service passed as a parameter"), which is what keeps `User` itself free of any Spring import.

`User` carries `version` (no `createdAt`/`updatedAt` — no DTO in `openapi/components/schemas/` exposes a `User`/`account` response, so there's no gap to close the way there was for `Student`/`Course`/`Book`) per the optimistic-locking decision in §10: `provisionForStudent(...)` starts it at `0`; `changePassword(...)` doesn't touch it directly, Spring Data JDBC increments it on `save()`. It guards the one genuine concurrent-write race in this module — a `changePassword` racing a `provisionForStudent`-triggered re-save is not possible (provisioning only happens once, at registration), but `changePassword` racing a second, stale `changePassword` request from a replayed/duplicate client submission is exactly what it catches.

### 8.3 `UserRepository` port

| Method | Parameters | Returns | Notes |
| --- | --- | --- | --- |
| `findByUsername` | `Username username` | `Optional<User>` | `AppUserDetailsService.loadUserByUsername`, `IdentityService.changePassword` |
| `findByStudentCode` | `StudentCode studentCode` | `Optional<User>` | `IdentityService.viewInitialPassword` (UC-23) |
| `existsByUsername` | `Username username` | `boolean` | `IdentityService.provisionStaff` (§3a of `04-authentication-authorization.md`) — never called for Student accounts, since `Email` is already Student.2-unique |
| `findById` | `UserId userId` | `Optional<User>` | `IdentityService.setAccountEnabled` (UC-25) |
| `save` | `User user` | `User` | Provisioning, change-password, enable/disable |
| `deleteByStudentId` | `StudentId studentId` | `void` | **Addition beyond `tactical-ddd-design.md` §7's "representative methods" list** — needed by the `StudentDeleted` listener (§13) so account removal goes through the application layer rather than relying solely on `users.student_id ON DELETE CASCADE` as the *only* mechanism, consistent with `05-database-schema.md` §5's "DB-level is a safety net, not a replacement" |

### 8.4 `IdentityService`

| Method | Parameters | Returns | Throws | Orchestration |
| --- | --- | --- | --- | --- |
| `provisionForStudent` | `StudentId studentId, Email email` | `ProvisionedAccount` (`record ProvisionedAccount(String username, String plaintextPassword)`) | — | `04-auth.md` §3: generate plaintext via `InitialPasswordGenerator` → `User.provisionForStudent(...)` → `repository.save(user)`. This is `AccountProvisioning.provisionForStudent`; called synchronously by `StudentService.register`, same transaction |
| `provisionStaff` | `ProvisionStaffCommand command` (`record ProvisionStaffCommand(String username, Role role)`) | `ProvisionedAccount` | `DomainValidationException` (`role` not in `Role.STAFF_ROLES`), `DuplicateUsernameException` (`existsByUsername`) | `04-auth.md` §3a: validate role → `existsByUsername` → generate plaintext via `InitialPasswordGenerator` → `User.provisionStaff(...)` → `repository.save(user)`. Called only from `StaffAccountController`, never from another module |
| `setAccountEnabled` | `UserId userId, boolean enabled` | `User` | `UserNotFoundException` | `04-auth.md` §3b: `findById` → `agg.setEnabled(enabled)` → `repository.save(user)` |
| `changePassword` | `ChangePasswordCommand command` (`record ChangePasswordCommand(String currentPassword, String newPassword, String retypeNewPassword)`, principal resolved from `SecurityContext`) | `void` | `DomainValidationException` (retype mismatch or policy failure — §5.2 of `04-auth.md`: min 8 / max 72 chars, must differ from current), `InvalidCredentialsException` (current-password mismatch) | §5.1: retype check → `findByUsername` → `matchesCurrentPassword` → policy check → `agg.changePassword(...)` → save |
| `viewInitialPassword` | `StudentCode studentCode` | `InitialPasswordView` (`record InitialPasswordView(String username, String initialPassword)`) | `PasswordNoLongerAvailableException` | §5.3: `findByStudentCode` → if `!mustChangePassword` throw, else `cipher.decrypt(initialPasswordEncrypted)` |
| `studentIdOf` (`PrincipalStudentResolver`) | `Authentication authentication` | `Optional<StudentId>` | — | Used by `student`/`book`/`enrollment` controllers or services to scope a `STUDENT`-role caller to `principal.studentId` (`02-component-diagram.md` §4's "own records only" row) |
| `listDemoAccounts` | — | `List<DemoAccount>` (`record DemoAccount(Role role, String username, String password)`) | — | §8 of `04-auth.md`: returns the 5 fixed, hardcoded demo identities — not read from `UserRepository`, since the demo password must be returned in plaintext, which `User`/`password_hash` can never do. Only wired up when `app.demo-accounts.enabled=true` (§11.4) |

`IdentityService` implements both `AccountProvisioning` and `PrincipalStudentResolver` — no separate façade class, mirroring `StudentService`/`StudentLookup` in §4.8.

### 8.5 Web-layer / security-adapter classes

Four classes sit in `identity/web/` alongside `AuthController`, none of them owning business rules — two exist because Spring Security's contracts (`UserDetailsService`, `OncePerRequestFilter`) have to be implemented *somewhere*, and `identity` is the module that owns `User`:

| Class | Role |
| --- | --- |
| `AppUserDetailsService implements UserDetailsService` | `loadUserByUsername(String username): UserDetails` — delegates to `UserRepository.findByUsername`, throws Spring Security's own `UsernameNotFoundException` if absent (§4.1 of `04-auth.md`); wraps the found `User` in a small `AuthenticatedPrincipal` (custom `UserDetails` implementation carrying `role`, `studentId`, `mustChangePassword`) |
| `MustChangePasswordFilter extends OncePerRequestFilter` | `doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain): void` — the gate in `04-auth.md` §4.2: 403 if `principal.mustChangePassword && path != "/api/v1/auth/password"` |
| `AuthController` | Hosts only `changePassword`, `viewStudentInitialPassword`, and `listDemoAccounts` (`operationId`s `changePassword`, `viewStudentInitialPassword`, `listDemoAccounts`) — **not** `login`. `POST /api/v1/auth/login` is handled entirely inside Spring Security's authentication filter chain (a `UsernamePasswordAuthenticationFilter`-family filter configured with a JSON `AuthenticationSuccessHandler`/`AuthenticationFailureHandler`), per `04-auth.md` §4.1's sequence lifeline (`User->>Sec`, never reaching a `@Controller`). This filter and its handlers are `shared`/`identity` Spring-config, designed in full in §11 |
| `StaffAccountController` | Hosts `createStaffAccount` and `setStatus` (UC-24/25) — a separate controller class from `AuthController`, not just separate methods, because every one of its endpoints requires `hasRole("SYSTEM_ADMINISTRATOR")` (§11.1) while `AuthController`'s endpoints don't share a single uniform role rule; keeping them apart keeps the `SecurityFilterChain`'s per-controller-package matchers simple |

### 8.6 `AuthController`

| Method | HTTP | Parameters | Returns |
| --- | --- | --- | --- |
| `changePassword` | `POST /api/v1/auth/password` | `ChangePasswordRequest request` | `void` (200) |
| `viewStudentInitialPassword` | `GET /api/v1/students/{code}/initial-password` | `String code` | `InitialPasswordResponse` (200) |
| `listDemoAccounts` | `GET /api/v1/auth/demo-accounts` | — | `List<DemoAccountResponse>` (200) — bean only registered when `app.demo-accounts.enabled=true` (§11.4); the route doesn't exist otherwise |

### 8.7 `StaffAccountController`

| Method | HTTP | Parameters | Returns |
| --- | --- | --- | --- |
| `createStaffAccount` | `POST /api/v1/staff-accounts` | `CreateStaffAccountRequest request` (`{username, role}`) | `StaffAccountResponse` (201) — `{username, role, initialPassword}` |
| `setStatus` | `PATCH /api/v1/staff-accounts/{id}/status` | `Long id, SetStatusRequest request` (`{enabled}`) | `StaffAccountResponse` (200) — `{username, enabled}` |

Both delegate straight to `IdentityService.provisionStaff`/`setAccountEnabled` (§8.4) — no additional orchestration at the web layer, matching every other controller in this document.

---

### 8.8 `SessionService` and `SessionController` (UC-27, UC-28)

Placed in `identity`, not `shared`: `SessionRegistry` is a framework type (`org.springframework.security.core.session`), invisible to Spring Modulith's dependency analysis, so injecting it creates no module edge. `identity` already depends on `shared.security` for `AuthenticatedPrincipal`, which is the only project type crossing here. `shared` would be the wrong home anyway — it has no `application/` package, and `NamingConventionsTest` requires a `@Service` to live in one.

| Class | Package | Responsibility |
| --- | --- | --- |
| `SessionService` | `identity/application/` | Reads `SessionRegistry`; digests session ids into handles; `expireNow()` on revoke |
| `SessionController` | `identity/web/` | `GET /api/v1/sessions`, `DELETE /api/v1/sessions/{handle}` |
| `SessionMapper` | `identity/web/` | `ActiveSessionView` → `ActiveSessionDto` |
| `ActiveSessionDto` | `identity/web/dto/` | `(handle, username, role, lastRequest, current)` |

**The one trap worth stating in code as well as here.** `AuthenticatedPrincipal` is a `record`, so it has value-based `equals`/`hashCode`, and `SessionRegistryImpl.principals` is a `ConcurrentMap` keyed on the principal *object*. `AuthController.changePassword` (§8.5) installs `principal.withPasswordChanged()` into the session without informing the registry, so a principal reconstructed in order to look one up can differ from the stored key by one field and match nothing — `getAllSessions(rebuilt, false)` returns empty, silently. Every method in `SessionService` therefore iterates `getAllPrincipals()` and never constructs a principal to key by.

Handle derivation uses a fresh `MessageDigest.getInstance("SHA-256")` per call (the class is not thread-safe) and `HexFormat.of().formatHex(...)`. Revocation resolves a handle by scanning principals × sessions and digesting each — `O(P × S)` with one digest apiece, which for an in-memory registry on a single-process deployment is not worth an index. A cached `handle → sessionId` map is deliberately avoided: it would be a second source of truth requiring its own pruning on every `SessionDestroyedEvent`.

`SessionController` is **not paged**, unlike every other list in this API — the registry is an in-memory snapshot with no stable ordering to offset into. It reads its own session id with `request.getSession(false)`, since asking for the caller's session must never create one.

## 9. Persistence Mapping & Flyway Migration

### 9.1 Annotation conventions

Spring Data JDBC's default `NamingStrategy` converts a camelCase property to snake_case automatically (`studentCode` → `student_code`, `dateOfBirth` → `date_of_birth`, `ownerId` → `owner_id`), which is exactly `05-database-schema.md` §6's naming rule — every `*Row` field below already lands on the right column with **zero** explicit `@Column` annotations; adding them anyway would be redundant, not defensive, so this document doesn't. Each `*Row` is a `record`: Spring Data JDBC creates it via its canonical (all-args) constructor on read, and — since records have no setters — assigns a DB-generated `id` and an incremented `@Version` back by building a *new* record instance through that same constructor after `save()` (its documented immutable-entity path), not by mutating the original.

| Module | Row class | `@Table` | `@Id` | `@Version` | Notes |
| --- | --- | --- | --- | --- | --- |
| `student` | `StudentRow` | `"students"` | `id` | `version` | — |
| `course` | `CourseRow` | `"courses"` | `id` | `version` | `description` maps `NULL` ⇄ `null` directly, no wrapper type needed |
| `book` | `BookRow` | `"books"` | `id` | `version` | `ownerId` is a nullable `Long` — Spring Data JDBC maps SQL `NULL` to Java `null` for a boxed type with no extra annotation |
| `enrollment` | `EnrollmentRow` | `"enrollments"` | `id` | *(none — §10)* | Stores `courseId` (`Long`), **not** `courseCode` — see translation note below |
| `identity` | `UserRow` | `"users"` | `id` | `version` | `role` maps to the MySQL `ENUM(...)` via Spring Data JDBC's default `Enum.name()` conversion, whose values already match `Role`'s constants exactly |

Two representative `*Row` records:

```java
package org.phuchoang.management.student.internal;

@Table("students")
public record StudentRow(
    @Id Long id,
    String studentCode,
    String firstName,
    String lastName,
    String email,
    LocalDate dateOfBirth,
    Instant createdAt,
    Instant updatedAt,
    @Version long version
) {}
```

```java
package org.phuchoang.management.enrollment.internal;

@Table("enrollments")
public record EnrollmentRow(
    @Id Long id,
    Long studentId,
    Long courseId,
    Instant enrolledAt
) {}
```

**`EnrollmentRow.courseId` vs. `Enrollment.courseCode` — a translation the repository port already implied but never spelled out:** `enrollments.course_id` is the DB's surrogate FK (`05-database-schema.md` §3.4), but `EnrollmentRepository`'s port methods (§7) are typed in `CourseCode`, matching the aggregate. `JdbcEnrollmentRepository` (`internal/`) resolves the difference with a SQL join against `courses` in its `@Query` methods — e.g. `findByCourseCode` becomes `SELECT e.* FROM enrollments e JOIN courses c ON c.id = e.course_id WHERE c.course_code = :courseCode`, and `save` first resolves `courseCode` to `courseId` (a one-row `SELECT id FROM courses WHERE course_code = :courseCode`, already guaranteed to exist because `EnrollmentService.enroll` validated it via `CourseLookup.existsByCode` beforehand) before writing the `EnrollmentRow`. This is a plain SQL join, not a Java import across the module boundary — `ApplicationModules.verify()` (§2.1) only forbids the latter — so it doesn't violate the module boundary it looks like it might cross.

### 9.1a Date/time conversions — `JdbcConversionsConfig`

MySQL's `DATETIME` carries a wall clock with no zone, and by default the two halves of the round trip disagree about which zone that is.

| Half | What happens by default | Correct? |
| --- | --- | --- |
| Write | `JdbcColumnTypes` resolves an `Instant` property to `java.sql.Timestamp` (via its `Temporal → Timestamp` entry); the store's `InstantToTimestampConverter` is `Timestamp.from(instant)`, and Connector/J renders it into the *connection* time zone, which `application.properties` pins to `serverTimezone=UTC`. | Yes |
| Read | `rs.getObject` on a `DATETIME` returns `java.time.LocalDateTime` (`MysqlType.DATETIME`'s default Java class), and Spring's stock `Jsr310Converters.LocalDateTimeToInstantConverter` interprets it at **`ZoneId.systemDefault()`**. | **No** — off by the JVM's offset from UTC |

The read defect compounds, because `JdbcStudentRepository.toRow`/`JdbcCourseRepository.toRow` write the `createdAt` they last read back into every `UPDATE`: the error accumulates once per `version`.

`LocalDate` has the mirror-image defect on the *write* side. Its store converter is `Timestamp.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())`, so at a positive UTC offset it sends the previous evening; MySQL truncates that `DATETIME` into the `DATE` column and `students.date_of_birth` lands a whole day early. Reads need no converter — `rs.getObject` on a `DATE` returns a `LocalDate` with no zone applied.

`shared/persistence/JdbcConversionsConfig` registers exactly the two halves that are wrong:

```java
@Configuration
public class JdbcConversionsConfig {
  @Bean
  public JdbcCustomConversions jdbcCustomConversions(JdbcDialect dialect) {
    return JdbcConfiguration.createCustomConversions(
        dialect, List.of(UtcLocalDateTimeToInstant.INSTANCE, UtcLocalDateToTimestamp.INSTANCE));
  }
  // @ReadingConverter LocalDateTime -> Instant : source.toInstant(ZoneOffset.UTC)
  // @WritingConverter LocalDate -> Timestamp   : Timestamp.from(source.atStartOfDay(UTC).toInstant())
}
```

Three implementation notes that are easy to get wrong:

- **No `Instant → LocalDateTime` writing converter.** It would be dead code. `MappingRelationalConverter.determineCustomWriteTarget` asks `CustomConversions` for the exact pair `(Instant, Timestamp)` first, and the store converter already claims it; a `(Instant, LocalDateTime)` pair is never consulted.
- **A plain `JdbcCustomConversions` bean, not an `AbstractJdbcConfiguration` subclass.** Boot's `DataJdbcRepositoriesAutoConfiguration$SpringBootJdbcConfiguration.jdbcCustomConversions()` is `@ConditionalOnMissingBean`, so declaring this bean replaces that one and leaves the mapping context, converter, dialect, aggregate template and repository registration auto-configured.
- **Built through `JdbcConfiguration.createCustomConversions`,** the same call `AbstractJdbcConfiguration` makes — `new JdbcCustomConversions(List.of(...))` would silently drop the dialect's own converters and simple types.

User converters win over the store's: `CustomConversions` reverses the `[user, store, default]` list before registering it, and `GenericConversionService` registers each pair with `addFirst`.

**Test fixtures had been masking this.** Every integration test bound `MySQLContainer.getJdbcUrl()`, which carries no time-zone parameter, so Connector/J fell back to `connectionTimeZone=LOCAL` — the JVM's zone on both halves — and the round trip was accidentally self-consistent while production was wrong. `TestDatasource.bind` is now the single place a container is bound to `spring.datasource.*`, and it appends `serverTimezone=UTC`; a test class added later inherits the parameter rather than silently reintroducing the blind spot.

### 9.2 Flyway migration DDL

`management/src/main/resources/db/migration/V1__init_schema.sql` (the location `application.properties`'s `spring.flyway.locations=classpath:db/migration` already points at). Transcribed table-by-table from `05-database-schema.md` §3, in FK dependency order (`students`, `courses` before `books`, `enrollments`, `users`), **with one addition beyond that document's column list**: a `version BIGINT NOT NULL DEFAULT 0` column on `students`, `courses`, `books`, and `users` — not `enrollments` — backing the optimistic-locking decision in §10; `05-database-schema.md` doesn't mention it because that document explicitly left versioning undecided (`tactical-ddd-design.md` §13), and this is where it gets decided.

```sql
-- V1__init_schema.sql
-- Charset utf8mb4, engine InnoDB throughout (05-database-schema.md §6).

CREATE TABLE students (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_code   VARCHAR(20)  NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    date_of_birth  DATE         NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_students_student_code UNIQUE (student_code),
    CONSTRAINT uq_students_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE courses (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_code   VARCHAR(20)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   TEXT         NULL,
    credits       SMALLINT     NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_courses_course_code UNIQUE (course_code),
    CONSTRAINT chk_courses_credits CHECK (credits > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE books (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    isbn            VARCHAR(20)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    author          VARCHAR(255) NOT NULL,
    published_date  DATE         NULL,
    owner_id        BIGINT       NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_books_isbn UNIQUE (isbn),
    CONSTRAINT fk_books_owner FOREIGN KEY (owner_id) REFERENCES students (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE enrollments (
    id           BIGINT   AUTO_INCREMENT PRIMARY KEY,
    student_id   BIGINT   NOT NULL,
    course_id    BIGINT   NOT NULL,
    enrolled_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_enrollments_student_course UNIQUE (student_id, course_id),
    CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username                    VARCHAR(255) NOT NULL,
    password_hash               CHAR(60)     NOT NULL,
    initial_password_encrypted  VARCHAR(255) NULL,
    role                        ENUM('REGISTRAR','LIBRARIAN','COURSE_ADMINISTRATOR','STUDENT') NOT NULL,
    student_id                  BIGINT       NULL,
    must_change_password        BOOLEAN      NOT NULL DEFAULT FALSE,
    version                     BIGINT       NOT NULL DEFAULT 0,
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_student_id UNIQUE (student_id),
    CONSTRAINT fk_users_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT chk_users_student_role CHECK (
        (role = 'STUDENT' AND student_id IS NOT NULL) OR (role <> 'STUDENT' AND student_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

The `ON DELETE` clauses above are the same DB-level safety net `05-database-schema.md` §5 already documents (real deletes route through the Spring Modulith event listeners in §13) — nothing about that decision changes here; this is the first time it's written as runnable SQL.

## 10. Optimistic Locking

`tactical-ddd-design.md` §13 left this "an implementation concern, not fixed at this level" — deferred, not forbidden — so this document decides it: **`Student`, `Course`, `Book`, and `User` each carry `@Version long version`; `Enrollment` does not.**

The line is drawn at "has an update-after-create lifecycle": `Student.applyChanges`, `Course.applyChanges`, `Book.changeOwner`/`clearOwner`, and `User.changePassword` all mutate an aggregate that was loaded earlier in the same request — exactly the window in which a second, concurrent write can silently overwrite the first (e.g. a Librarian assigning a book to one student while a second request reassigns it to another, both reading the same pre-assignment row). `Enrollment` has no such window: §7 already establishes "no update... there is no field on `Enrollment` any UC ever changes after creation," so there's no lost update for a `version` column to prevent — adding one would be a field with no reader.

**Failure path:** `Jdbc*Repository.save(...)` calls the injected `CrudRepository.save(row)`; when the `version` it's writing no longer matches the row in the database, Spring Data JDBC throws `org.springframework.dao.OptimisticLockingFailureException`. Each adapter catches this at the port boundary and rethrows the module's own `StaleWriteException` (§3, `ConflictException` subtype, 409):

```java
@Override
public Student save(Student student) {
    StudentRow row = toRow(student);
    try {
        return toDomain(springRepo.save(row));
    } catch (OptimisticLockingFailureException e) {
        throw new StaleWriteException("Student " + student.code().value() + " was modified concurrently");
    }
}
```

This keeps the translation at the `internal/` boundary rather than in `GlobalExceptionHandler` — `OptimisticLockingFailureException` is a Spring Data infrastructure type, and letting it escape `internal/` unwrapped would be the same layering violation `port/` interfaces exist to prevent (§2.2). `GlobalExceptionHandler` needs no new `@ExceptionHandler` method: `StaleWriteException` already flows through the single `ApiException`-typed handler §3 describes.

## 11. Spring Security Configuration

`shared.security.SecurityConfig` (`@Configuration` + `@EnableWebSecurity`) is the one class implementing what `02-component-diagram.md` §4 and `04-authentication-authorization.md` §1/§6 already fixed as decisions — the RBAC matrix, session-based auth, and the login/change-password/view-initial-password endpoint rules — as an actual `SecurityFilterChain`.

### 11.1 RBAC → `authorizeHttpRequests`

Directly off `02-component-diagram.md` §4's table (Registrar writes `student`+`enrollment`; Librarian writes `book`; Course Administrator writes `course`; Student writes nothing; every role reads everything, with the `STUDENT`-role "own records only" narrowing done inside each Application Service, per that section's own note — not expressible as a filter-chain rule):

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager,
                                        MustChangePasswordFilter mustChangePasswordFilter) throws Exception {
    JsonUsernamePasswordAuthenticationFilter loginFilter =
        new JsonUsernamePasswordAuthenticationFilter(authenticationManager);
    loginFilter.setFilterProcessesUrl("/api/v1/auth/login");
    loginFilter.setAuthenticationSuccessHandler(this::onLoginSuccess);
    loginFilter.setAuthenticationFailureHandler(this::onLoginFailure);
    loginFilter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
    // Required, not optional -- see the note under this snippet.
    loginFilter.setSessionAuthenticationStrategy(
        new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(),
            new RegisterSessionAuthenticationStrategy(sessionRegistry))));

    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            // Installs ConcurrentSessionFilter, which *is* the revocation mechanism. The limit is
            // unlimited: concurrent logins are not being capped, the machinery is being switched on.
            .sessionConcurrency(concurrency -> concurrency
                .maximumSessions(SessionLimit.UNLIMITED)
                .sessionRegistry(sessionRegistry)
                .expiredSessionStrategy(new SessionRevokedExpiredStrategy(objectMapper))))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/auth/demo-accounts").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/password").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/v1/students/*/initial-password").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.POST, "/api/v1/staff-accounts/**").hasRole("SYSTEM_ADMINISTRATOR")
            .requestMatchers(HttpMethod.PATCH, "/api/v1/staff-accounts/**").hasRole("SYSTEM_ADMINISTRATOR")
            .requestMatchers(HttpMethod.GET, "/api/v1/staff-accounts/**").hasRole("SYSTEM_ADMINISTRATOR")
            // Same reasoning as the /staff-accounts GET: the per-resource read allow-list below
            // does not cover /sessions, so without these a GET falls through to
            // .anyRequest().authenticated() and every signed-in role could enumerate -- and end --
            // everyone else's sessions.
            .requestMatchers(HttpMethod.GET, "/api/v1/sessions").hasRole("SYSTEM_ADMINISTRATOR")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/sessions/*").hasRole("SYSTEM_ADMINISTRATOR")
            .requestMatchers(HttpMethod.POST, "/api/v1/students/**").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.PUT, "/api/v1/students/**").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/students/**").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/**").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/enrollments/**").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.POST, "/api/v1/courses/**").hasRole("COURSE_ADMINISTRATOR")
            .requestMatchers(HttpMethod.PUT, "/api/v1/courses/**").hasRole("COURSE_ADMINISTRATOR")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/courses/**").hasRole("COURSE_ADMINISTRATOR")
            .requestMatchers(HttpMethod.POST, "/api/v1/books/**").hasRole("LIBRARIAN")
            .requestMatchers(HttpMethod.PUT, "/api/v1/books/**").hasRole("LIBRARIAN")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/books/**").hasRole("LIBRARIAN")
            .requestMatchers(HttpMethod.GET, "/api/v1/me/**").hasRole("STUDENT")
            .requestMatchers(HttpMethod.GET, "/api/v1/students/**")
                .hasAnyRole("REGISTRAR", "LIBRARIAN", "COURSE_ADMINISTRATOR", "STUDENT")
            .requestMatchers(HttpMethod.GET, "/api/v1/books/**").hasAnyRole("LIBRARIAN", "STUDENT")
            .requestMatchers(HttpMethod.GET, "/api/v1/courses/**")
                .hasAnyRole("REGISTRAR", "COURSE_ADMINISTRATOR", "STUDENT")
            .requestMatchers(HttpMethod.GET, "/api/v1/enrollments/**")
                .hasAnyRole("REGISTRAR", "COURSE_ADMINISTRATOR")
            .anyRequest().authenticated())
        .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(mustChangePasswordFilter, AuthorizationFilter.class);

    return http.build();
}

@Bean
public SessionRegistry sessionRegistry() {
    // A bean, not a DSL-created instance: SessionRegistryImpl is an ApplicationListener, and only a
    // registry that is a bean receives SessionDestroyedEvent/SessionIdChangedEvent and prunes itself.
    return new SessionRegistryImpl();
}

@Bean
public HttpSessionEventPublisher httpSessionEventPublisher() {
    // Bridges the container's HttpSessionEvents into the application context. Without it nothing
    // tells the registry a session was logged out or timed out, and it leaks dead ids forever.
    return new HttpSessionEventPublisher();
}

@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Summarized, the same rules read as:

| Verb + path | Role required |
| --- | --- |
| `POST/PUT/DELETE /api/v1/students/**` | `REGISTRAR` |
| `POST/DELETE /api/v1/enrollments/**` | `REGISTRAR` (no `PUT` — Enrollment has no update, §7) |
| `POST/PUT/DELETE /api/v1/courses/**` | `COURSE_ADMINISTRATOR` |
| `POST/PUT/DELETE /api/v1/books/**` | `LIBRARIAN` |
| `POST/PATCH/GET /api/v1/staff-accounts/**` | `SYSTEM_ADMINISTRATOR` |
| `GET /api/v1/sessions`, `DELETE /api/v1/sessions/*` | `SYSTEM_ADMINISTRATOR` |
| `GET /api/v1/auth/demo-accounts` | none (public) — only reachable at all when `app.demo-accounts.enabled=true`, §11.4 |
| `GET /api/v1/students/**` | `REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR`, or `STUDENT` — scoping for `STUDENT` happens in the Application Service |
| `GET /api/v1/books/**` | `LIBRARIAN` or `STUDENT` — same Application-Service scoping for `STUDENT` |
| `GET /api/v1/courses/**` | `REGISTRAR`, `COURSE_ADMINISTRATOR`, or `STUDENT` — the catalogue is not personal data, so no scoping applies |
| `GET /api/v1/enrollments/**` | `REGISTRAR` or `COURSE_ADMINISTRATOR` |
| `GET /api/v1/me/**` | `STUDENT` |

**Why reads are listed per resource rather than as one matcher:** an earlier version of this chain
granted all four domain roles read access to every domain path in a single `hasAnyRole(...)`. That
is broader than `02-component-diagram.md` §4 ever intended — it let a Registrar enumerate the book
catalogue and a Librarian enumerate enrollments, neither of which any use case asks for. Splitting
it per resource makes each grant a deliberate statement (see §4 of that document for what each grant
is *for*), and makes the absent ones enforceable rather than merely unexercised.

Two consequences worth naming:

- **`COURSE_ADMINISTRATOR` keeps `GET /api/v1/students/**`** even though it has no student-browsing
  workflow. It needs the grant to open a student's profile from a course roster (UC-17 reached via
  UC-19), which is a detail read, not a browse. The restriction is expressed in the client's
  navigation, not in the filter chain, because the filter chain cannot tell the two apart.
- **`STUDENT` loses `GET /api/v1/enrollments/**` entirely.** Its enrolled courses come from
  `GET /api/v1/me/courses`, scoped by the session principal rather than by a caller-supplied student
  code — so `EnrollmentService.getDetail` no longer takes a `callerStudentId` and carries no
  own-records check at all (§7). Withdrawing the grant is strictly safer than scoping it: there is
  no comparison left to get wrong.

**Why the login filter needs its session strategy set by hand.** `.sessionManagement()` publishes its
`SessionAuthenticationStrategy` as a shared object, and that object is consumed only by
`AbstractAuthenticationFilterConfigurer` — i.e. by filters the DSL builds, such as `formLogin`. This
chain installs its login filter with `addFilterAt` (§11.2), so no configurer ever runs for it and it
keeps `AbstractAuthenticationProcessingFilter`'s inherited `NullAuthenticatedSessionStrategy`. There
is no fallback either: `SessionManagementConfigurer.createSessionManagementFilter` returns `null`
under `shouldRequireExplicitAuthenticationStrategy()`, so `SessionManagementFilter` is not in the
chain to compensate.

Left alone that costs two things, and only one of them is the new feature:

- `SessionRegistry.getAllPrincipals()` would be permanently empty, so §8.8's endpoints would list
  nothing.
- **The session id was never rotated on login** — the application had no session-fixation
  protection. That had gone unnoticed precisely because nothing else depended on the strategy being
  real.

Order within the composite matters: `ChangeSessionIdAuthenticationStrategy` must precede
`RegisterSessionAuthenticationStrategy`, or the id registered is the pre-rotation one.
`ConcurrentSessionControlAuthenticationStrategy` is deliberately omitted — under
`SessionLimit.UNLIMITED` it only adds a `getAllSessions()` call per login.

**Why `ConcurrentSessionFilter` needs a custom expired strategy.** Its default,
`ResponseBodySessionInformationExpiredStrategy`, prints one plain-text sentence and never calls
`setStatus`, so a revoked session's next request would answer **200 OK** with prose — which no client
can distinguish from success. `SessionRevokedExpiredStrategy` replaces it with a 401 in the standard
`Error` envelope, written directly to the response because this filter runs ahead of
`DispatcherServlet` and `GlobalExceptionHandler` can never see it. It assembles the body as a `Map`
with a pre-formatted timestamp, matching `onLoginFailure` — the `ObjectMapper` in `shared.security` is
constructed directly rather than injected from Boot, and a bare Jackson mapper renders an `Instant` as
a numeric epoch.

`FilterOrderRegistration` places `ConcurrentSessionFilter` after the login filter and before
`AuthorizationFilter`, and therefore before `MustChangePasswordFilter`, so a revoked session is turned
away ahead of both authorization and the must-change gate.

**Why `SYSTEM_ADMINISTRATOR` needs an explicit exclusion, not just an absent grant:** every other role rule above is additive (`hasRole(...)` on specific paths), with `.anyRequest().authenticated()` as a permissive catch-all for reads. Adding `SYSTEM_ADMINISTRATOR` as a 5th authenticated role without also touching that catch-all would let it fall through to `.anyRequest().authenticated()` on every domain `GET` — passing the filter chain even though `02-component-diagram.md` §4 grants it zero domain read access. The `hasAnyRole(...)` matcher above is what actually enforces "no domain access at all" for this role at the filter-chain level, exercised by [cross-cutting.md](../Testing/03-test-cases/cross-cutting.md) TC-XC-040; `.anyRequest().authenticated()` remains only as a fallback for paths not explicitly listed.

**CSRF — a decision `04-authentication-authorization.md` doesn't make, called out explicitly:** disabled. Auth here is session-based, which is normally exactly the case CSRF protection exists for, but this API has no HTML form surface — every write is a JSON body from a programmatic client, not a browser `<form>` submission a forged cross-site request could imitate — so the standard justification for enabling it doesn't apply. If a browser SPA client is added later that also carries other same-site cookies, this is the decision to revisit.

### 11.2 JSON login filter

`POST /api/v1/auth/login` takes a JSON body (`{username, password}`, UC-21), but Spring Security's default `UsernamePasswordAuthenticationFilter` reads form-urlencoded parameters — so `shared.security` adds a small subclass that parses the body itself and otherwise behaves identically:

```java
public class JsonUsernamePasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonUsernamePasswordAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        try {
            LoginRequest body = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
            var authRequest = UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password());
            setDetails(request, authRequest);
            return getAuthenticationManager().authenticate(authRequest);
        } catch (IOException e) {
            throw new AuthenticationServiceException("Malformed login request body", e);
        }
    }

    private record LoginRequest(String username, String password) {}
}
```

Success/failure handlers write exactly the bodies `04-authentication-authorization.md` §4.1 specifies:

```java
private void onLoginSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth) throws IOException {
    AuthenticatedPrincipal principal = (AuthenticatedPrincipal) auth.getPrincipal();
    res.setStatus(HttpServletResponse.SC_OK);
    res.setContentType("application/json");
    objectMapper.writeValue(res.getWriter(),
        Map.of("role", principal.role(), "mustChangePassword", principal.mustChangePassword()));
}

private void onLoginFailure(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setContentType("application/json");
    objectMapper.writeValue(res.getWriter(), Map.of(
        "timestamp", Instant.now().toString(), "status", 401,
        "error", "Unauthorized", "message", "Invalid username or password", "path", req.getRequestURI()));
}
```

The 401 body deliberately reuses the same `{timestamp, status, error, message, path}` envelope `GlobalExceptionHandler` (§3) produces for every other error — login failure never reaches `GlobalExceptionHandler` (it's rejected inside the filter chain, before any `@Controller`), so the shape has to be duplicated here rather than delegated, but it stays visually consistent for API consumers.

### 11.3 `AppUserDetailsService` and `MustChangePasswordFilter` bodies

```java
@Component
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = repository.findByUsername(new Username(username))
            .orElseThrow(() -> new UsernameNotFoundException(username));
        if (!user.enabled()) {
            throw new DisabledException("Account is disabled");
        }
        return new AuthenticatedPrincipal(user);
    }
}

public record AuthenticatedPrincipal(User user) implements UserDetails {
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
    }
    @Override public String getPassword() { return user.passwordHash().value(); }
    @Override public String getUsername() { return user.username().value(); }
    public boolean mustChangePassword() { return user.mustChangePassword(); }
    public String role() { return user.role().name(); }
    public Optional<StudentId> studentId() { return Optional.ofNullable(user.studentId()); }
}
```

```java
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final String CHANGE_PASSWORD_PATH = "/api/v1/auth/password";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal principal
                && principal.mustChangePassword()
                && !CHANGE_PASSWORD_PATH.equals(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
```

This is the exact `alt` gate `04-authentication-authorization.md` §4.2 already specifies (`principal.mustChangePassword && path != "/api/v1/auth/password" → 403`) — the filter adds no new rule, only the Java for the one already fixed.

### 11.4 Demo-accounts controller — conditional registration

`04-authentication-authorization.md` §8's production-safety requirement — the route must not exist at all when disabled, not merely reject with 403 — is implemented by making the bean itself conditional, not by adding a security rule that could be misconfigured or bypassed:

```java
@RestController
@ConditionalOnProperty(name = "app.demo-accounts.enabled", havingValue = "true")
public class DemoAccountsController {

    private static final List<DemoAccountResponse> DEMO_ACCOUNTS = List.of(
        new DemoAccountResponse("SYSTEM_ADMINISTRATOR", "demo.sysadmin", "Demo#12345"),
        new DemoAccountResponse("REGISTRAR", "demo.registrar", "Demo#12345"),
        new DemoAccountResponse("LIBRARIAN", "demo.librarian", "Demo#12345"),
        new DemoAccountResponse("COURSE_ADMINISTRATOR", "demo.courseadmin", "Demo#12345"),
        new DemoAccountResponse("STUDENT", "demo.student", "Demo#12345"));

    @GetMapping("/api/v1/auth/demo-accounts")
    public List<DemoAccountResponse> listDemoAccounts() {
        return DEMO_ACCOUNTS;
    }
}
```

`application-prod.properties` fixes `app.demo-accounts.enabled=false`, overriding whatever the base `application.properties` default is — a `prod`-profile value that can't be forgotten by omission, since Spring Boot profile-specific properties always win over the base file. When the property is `false`, `@ConditionalOnProperty` skips registering `DemoAccountsController` entirely: no bean, no route, no `.permitAll()` matcher in §11.1 that could ever be exercised — a request to `GET /api/v1/auth/demo-accounts` in `prod` gets Spring MVC's ordinary `404` for an unmapped path, indistinguishable from any other nonexistent endpoint.

### 11.5 Own-records-only scoping implementation (PM-010)

§11.1's table decides which roles reach which GETs at the filter-chain level; where STUDENT is granted a read, the "own records only" narrowing is left to each Application Service (`02-component-diagram.md` §4). Two resources are in that position — `student` and `book`; `enrollment` is not, because STUDENT holds no grant there at all (see the third bullet below). That narrowing shipped as part of PM-010 (`04-sprint-backlog.md` §6), alongside the RBAC matrix tests the ticket was originally scoped for — the scoping logic didn't exist yet when the ticket was picked up, only its test cases (`cross-cutting.md` §1.3, TC-XC-009/010/011) did.

**Extracting the caller's identity.** `AuthenticatedPrincipal` gained a static helper used by every affected controller:

```java
public static Long studentIdOf(Authentication authentication) {
    return authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal
        ? principal.studentId()
        : null;
}
```

Null-safe on both `authentication` itself (a standalone/filter-chain-free MockMvc test resolves an unset `Authentication` parameter to `null`, not a wrongly typed principal) and on the principal's shape (a non-`AuthenticatedPrincipal` principal, e.g. a `@WithMockUser`-installed test principal, is treated the same as an unscoped staff caller). This differs from `MeController`'s blind cast to `AuthenticatedPrincipal`, which is only safe there because `/me/**` is already filter-chain-restricted to `hasRole("STUDENT")` — `/students` and `/books` are reachable by staff roles too, so a blind cast would throw for every non-Student caller.

**Threading the check through.** Each web controller extracts `Long callerStudentId = AuthenticatedPrincipal.studentIdOf(authentication)` and passes it into the corresponding Application Service method, which performs the actual authorization check — per §4's mandate that this live in `application/`, not `web/`:

- `StudentService.search(query, pageable, callerStudentId)` / `getDetail(code, callerStudentId)` — `search` passes `callerStudentId` through as `StudentRepository.search`'s new nullable `scopeToId` parameter (`AND (:scopeId IS NULL OR id = :scopeId)`, mirroring `BookRepository.search`'s pre-existing `ownerFilter` pattern); `getDetail` checks existence first (404), then ownership (`AccessDeniedException` → 403) — a mismatch is a 403, not a 404, per `api-specification.md` §5 decision #3.
- `BookService.search(query, ownerStudentCode, pageable, callerStudentId)` — when `callerStudentId` is present it **silently overrides** the client-supplied `ownerStudentCode` (and skips resolving it entirely) rather than rejecting a mismatched value, consistent with decision #4's "transparently scoped, never blocked" search philosophy. `getDetail(isbn, callerStudentId)` — existence check first, then ownership; **an unowned book is also a 403 for a Student caller**, not a 200 — resolved as a product decision during PM-010: "own records only" means the caller must actually own the book, and Students have no self-service checkout endpoint that would give them a reason to browse the full catalog.
- `EnrollmentService` — **no scoping parameter at all.** This bullet originally described a
  check-before-lookup ownership test on `getDetail(studentId, courseCode, callerStudentId)`, needed
  because `studentId` was a caller-supplied path variable exposing another student's numeric id and
  a probing Student had to get an identical 403 whether the target enrollment existed, had ended, or
  never existed. That whole class of problem was removed rather than defended: `SecurityConfig` no
  longer grants STUDENT any access to `/api/v1/enrollments/**` (§11.1), so the method has no
  `callerStudentId` and no ownership branch. A Student's enrolled courses come from
  `GET /api/v1/me/courses`, scoped by the session principal rather than by anything the caller
  supplies — there is no id to probe with and no comparison to get wrong.

**Exception choice.** All three reuse `org.springframework.security.access.AccessDeniedException` rather than a new `shared.exception` type — `GlobalExceptionHandler` already maps it to the standard `{timestamp,status,error,message,path}` 403 envelope (§3), so this was a zero-new-class change, and `AccessDeniedException` thrown from deep inside an `application/` service is still resolved by Spring MVC's `ExceptionHandlerExceptionResolver` inside `DispatcherServlet` before it can reach `ExceptionTranslationFilter`'s own (envelope-less) default handler — confirmed via `OwnRecordsScopingIntegrationTest`, which exercises this path through the real `SecurityFilterChain` rather than the standalone-MVC harness `GlobalExceptionHandlerTest` uses.

## 12. MapStruct Mapper Method Bodies

Every `*Mapper` interface (`@Mapper(componentModel = "spring")`) mixes two kinds of method, per §2.2's Command/DTO conventions:

- **DTO → Command**: flat `String`-to-`String` copies (a `Command` is "primitive/`String`-typed fields," §2.2) — MapStruct generates these from the bare abstract method signature; no body to write.
- **Domain → DTO**: needs VO `.value()` unwrapping and, for the `*DetailDto` methods, assembling nested collections — MapStruct can't infer either, so these are hand-written `default` methods.

`StudentMapper`, in full (reference module):

```java
@Mapper(componentModel = "spring")
public interface StudentMapper {

    // Generated — flat field copy, no body needed.
    RegisterStudentCommand toCommand(RegisterStudentRequest request);
    UpdateStudentCommand toCommand(UpdateStudentRequest request);
    BookSummaryDto toDto(BookSummary book);
    CourseSummaryDto toDto(CourseSummary course);

    // Hand-written — VO unwrapping MapStruct cannot infer.
    default StudentSummaryDto toSummaryDto(Student student) {
        return new StudentSummaryDto(
            student.id().value(), student.code().value(),
            student.firstName(), student.lastName(), student.email().value());
    }

    default StudentResponse toResponse(Student student) {
        return new StudentResponse(
            student.code().value(),
            student.firstName(), student.lastName(), student.email().value(),
            student.dateOfBirth().value(), student.createdAt(), student.updatedAt());
    }

    default StudentRegistrationResponse toRegistrationResponse(ProvisionedStudent provisioned) {
        return new StudentRegistrationResponse(
            toResponse(provisioned.student()), provisioned.username(), provisioned.initialPassword());
    }

    // No collections to compose: a student's books and courses are their own endpoints (§4.6).
    default StudentDetailDto toDetailDto(StudentDetailView view) {
        return new StudentDetailDto(
            view.studentCode(), view.firstName(), view.lastName(), view.email(),
            view.dateOfBirth(), view.createdAt(), view.updatedAt());
    }
}
```

What differs per sibling module — each still follows the same generated/hand-written split above, so only the bodies that actually differ are shown:

| Module | Hand-written method | Body detail |
| --- | --- | --- |
| `course` | `toResponse(Course course)` | `course.credits().value()` unwraps `Credits`; `description` copies straight through (plain `String`, nullable) |
| `book` | `toResponse(Book book)` | The owner is rendered as `ownerStudentCode`, not `ownerId` — `BookService` resolves the code through `StudentLookup.summaryOf` and the mapper copies it straight through; `null` when the book is unowned |
| `enrollment` | `toResponse(CreatedEnrollment created)` | Fully generated: `EnrollmentService` already unwraps the VOs and substitutes the caller's `studentCode` for the saved row's `studentId` (§7), so the mapper has only same-named `String`/`Instant` fields to copy |
| `identity` | *(none)* | `identity`'s only DTOs (`ChangePasswordRequest`, `InitialPasswordResponse`) map through `IdentityService`'s own records (`ChangePasswordCommand`, `InitialPasswordView`) directly — no `Mapper` class was ever named for `identity` in §8.1's package layout, and this document introduces none |

---

## 13. Cross-module event listeners

Both events are `record`s at the publishing module's root (§2.2): `student.StudentDeleted(StudentId studentId)`, `course.CourseDeleted(CourseCode courseCode)`. Each listener is a `@ApplicationModuleListener` method on the consuming module's own Application Service — Spring Modulith's Event Publication Registry guarantees at-least-once delivery after the publisher's transaction commits (`tactical-ddd-design.md` §9).

| Publisher | Event | Listener method | Consuming action |
| --- | --- | --- | --- |
| `StudentService.remove` | `StudentDeleted` | `BookService.onStudentDeleted(StudentDeleted event)` | `bookRepository.clearOwnerByStudentId(event.studentId())` |
| `StudentService.remove` | `StudentDeleted` | `EnrollmentService.onStudentDeleted(StudentDeleted event)` | `enrollmentRepository.deleteByStudentId(event.studentId())` |
| `StudentService.remove` | `StudentDeleted` | `IdentityService.onStudentDeleted(StudentDeleted event)` | `userRepository.deleteByStudentId(event.studentId())` |
| `CourseService.remove` | `CourseDeleted` | `EnrollmentService.onCourseDeleted(CourseDeleted event)` | `enrollmentRepository.deleteByCourseCode(event.courseCode())` |

## 14. Traceability

`tactical-ddd-design.md` §12 already maps every `req.md` rule to a tactical construct and an existing (module-level) class/method name; every entry in that table resolves, unchanged, to a concrete method in §§4–8 and §13 above — this document adds no new mapping, only the parameter/return types and the exception types each method throws. The deltas this document introduces *beyond* what `tactical-ddd-design.md` names are called out individually rather than in a separate table: `UserRepository.deleteByStudentId` (§8.3), the `PasswordHasher`/`PasswordCipher`/`InitialPasswordGenerator` ports (§8.1), the full `shared` exception hierarchy including `StaleWriteException` (§3), the `AuthController`-not-`StudentController` routing note for UC-23 (§4.6), the `createdAt`/`updatedAt`/`enrolledAt` fields closing the aggregate↔DTO gap found while writing this revision (§4.4, §7), the `version`-column optimistic-locking decision (§10), the `EnrollmentRow.courseId ⇄ Enrollment.courseCode` translation the repository adapter performs (§9.1), and — new in this revision — the `SYSTEM_ADMINISTRATOR` role and `User.enabled` field, `IdentityService.provisionStaff`/`setAccountEnabled`/`listDemoAccounts`, `StaffAccountController` (§8.7), `DemoAccountsController` (§11.4), and the `DuplicateUsernameException`/`UserNotFoundException` exception types (§3), all implementing UC-24/UC-25 and the demo-accounts convenience per `04-authentication-authorization.md` §3a/§3b/§8.

This revision adds four more, three of which are new mappings and one of which is a correction:

- **`Identity.8`** → `SessionService.listActiveSessions`/`revoke` and `SessionController` (§8.8), implementing UC-27/UC-28 per `04-authentication-authorization.md` §3c. This is the first `req.md` rule added since `tactical-ddd-design.md` §12 was written, and it maps to no aggregate: a session is not a modelled entity here but a framework read model, which is why `SessionService` has no port and no `*Row`.
- **`Enrollment.1–3` in bulk** → `EnrollmentBatchService.enrollAll` (§7.1), implementing UC-26. It introduces no new invariant; it applies `EnrollmentService.enroll`'s existing ones per course, and the only thing genuinely new is the transaction boundary.
- **`enrolledCount`** → `CourseRepository.enrollmentCountsFor`/`enrollmentCountOf` (§5). A read model, not an invariant.
- **A correction, not an addition:** `JdbcConversionsConfig` (§9.1a) does not implement any rule — it repairs the `Instant`/`LocalDate` ⇄ MySQL mapping §9.1's `*Row` records assume and never stated, under which `createdAt` drifted by the JVM's UTC offset on every read and `date_of_birth` was stored a day early.

## 15. Out of Scope

- **DTO field lists** — every `web/dto` class named above is fully specified already in `openapi/components/schemas/*.yaml`; this document does not repeat those field lists (`02-component-diagram.md` §5's existing rule, extended to this document).
- **Unit-test design** — test doubles for `port/` interfaces, `@ApplicationModuleTest` setup, and `ApplicationModules.verify()` wiring are build-phase work.
- **AES key management/rotation, session-store horizontal scaling (Spring Session JDBC/Redis), MFA, password expiry/rotation/history** — all already ruled out of scope by `04-authentication-authorization.md` §9; this document's §11 implements what that document decided, not what it deferred.
- **Composite/covering indexes beyond what §9.2's `UNIQUE`/FK constraints already create** — `05-database-schema.md` §7 defers this without a known query pattern to design against; nothing in this revision changes that.
