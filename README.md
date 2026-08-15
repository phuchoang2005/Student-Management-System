# Student Management System — REST API

A production-oriented **Student Management System** backend, built as a portfolio-grade Spring Boot REST API. It manages students, books, and courses with proper layered architecture, validation, exception handling, and relational data integrity.

> **New here? Read the docs in order:**
> Business requirements: [docs/BA-docs/req.md](./docs/BA-docs/req.md)
> Use cases: [docs/BA-docs/use-cases.md](./docs/BA-docs/use-cases.md)
> Solution architecture: [docs/SA-docs/](./docs/SA-docs/)

## Tech Stack

- Java 21 + Spring Boot 4.1
- Spring Modulith (application modules + build-time boundary verification)
- Spring Web MVC
- Spring Security (authentication/authorization)
- Spring Data JDBC (explicit aggregate persistence, no lazy loading, no implicit cascades)
- MySQL 8 + Flyway (versioned schema, since Spring Data JDBC does not auto-generate DDL)
- MapStruct (compile-time mapping between domain model and DTOs, where a mapping isn't trivial enough for a plain constructor call)
- Jakarta Bean Validation
- JUnit 5, Mockito, MockMvc, `@ApplicationModuleTest`, Testcontainers

## Architecture

One Spring Boot process, one MySQL schema, **five Spring Modulith application modules** — vertical slices, one aggregate and one table each:

```text
org.phuchoang2005.management
├── shared/      ← open module: exceptions, error envelopes, config
├── student/     → shared
├── course/      → shared
├── book/        → shared, student          (Book.ownerId : StudentId)
└── enrollment/  → shared, student, course
```

Spring Modulith draws the **outer** boundary (which module may depend on which). Inside each module, a light **Clean/Hexagonal** layering draws the **inner** boundary (domain stays ignorant of frameworks):

```text
module/
├── domain/       ← aggregate root + value objects; plain Java, no Spring/JDBC imports
├── application/  ← use-case services; orchestrate the domain and repository ports
├── port/         ← repository interfaces the domain/application layer depends on
├── web/          ← REST controller + request/response DTOs (driving adapter)
└── internal/     ← Spring Data JDBC repository + persistence model (driven adapter);
                     unreachable from any other module
```

The domain layer has no knowledge of Spring, JDBC, or HTTP — it depends only on the ports it defines. `internal/` implements those ports against Spring Data JDBC and is where the only framework-aware persistence code lives. This is what "Spring Modulith + DDD + Hexagonal" means concretely here: Modulith enforces module-to-module boundaries at build time via `ApplicationModules.verify()` running as a JUnit test; the domain/port split enforces the dependency-inversion boundary within a module at code-review time.

Business logic lives on the aggregate where it inspects only its own state, and in the application service where it needs a port. Controllers stay thin, and persistence entities are never exposed over the API (DTOs only, mapped with MapStruct where the mapping isn't a trivial constructor call). Cross-module cleanup (deleting a student releases their books and drops their enrollments) travels backwards along the same edges as **synchronous, in-transaction domain events**.

See [docs/SA-docs/](./docs/SA-docs/) for the system overview, component, and sequence diagrams.

## Domain Model

- **Student → Book**: one-to-many. A student can own many books; a book belongs to at most one student (`student_id` is nullable).
- **Student ↔ Course**: many-to-many via a `student_courses` join table, modelled as an explicit `Enrollment` aggregate with a composite primary key.

```text
Student 1 ─────────── N Book
Student N ─────────── N Course
```

Aggregates reference each other **by identity only** — `Book` holds an `ownerId : StudentId`, never a `Student`. There is no `@ManyToOne` and no `@ManyToMany` in the codebase; that rule is what lets each aggregate live in its own module, and it's a natural fit for Spring Data JDBC, which persists aggregates as a whole and does not silently traverse relationships the way JPA does.

## API Overview

Base path: `/api/v1`

| Resource | Endpoints |
| --- | --- |
| Students | `POST /students`, `GET /students`, `GET /students/{id}`, `PUT /students/{id}`, `DELETE /students/{id}` |
| Books | `POST /books`, `GET /books`, `GET /books/{id}`, `PUT /books/{id}`, `DELETE /books/{id}` |
| Courses | `POST /courses`, `GET /courses`, `GET /courses/{id}`, `PUT /courses/{id}`, `DELETE /courses/{id}` |
| Student–Book | `POST /students/{studentId}/books/{bookId}` (assign), `GET /students/{studentId}/books`, `GET /books/{bookId}/owner`, `DELETE /students/{studentId}/books/{bookId}` (unassign) |
| Student–Course | `POST /students/{studentId}/courses/{courseId}` (enroll), `GET /students/{studentId}/courses`, `GET /courses/{courseId}/students`, `DELETE /students/{studentId}/courses/{courseId}` (unenroll) |

Endpoints that mutate state require an authenticated principal (Spring Security); read endpoints exposed to the `Student` actor are scoped to that student's own data.

## Key Business Rules

- `studentCode`, `email`, `isbn`, and `courseCode` must each be unique.
- A student cannot enroll in the same course twice (`409 Conflict`).
- Unassigning a book or unenrolling a student never deletes the underlying entity — only the relationship.
- Deleting a student sets `books.student_id = NULL` for their books and removes their course enrollments; the books and courses themselves remain. This happens via a `StudentDeleted` domain event handled by the `book` and `enrollment` modules **before** the student row is removed — all in one transaction, so a failure anywhere rolls the whole delete back.
- Deleting a course removes its `student_courses` enrollment records the same way, via `CourseDeleted`; students remain.
- Deleting a book publishes nothing — ownership is a column on the book, so the link dies with the row.

| Exception | HTTP Status |
| --- | --- |
| Resource not found | 404 |
| Validation failed | 400 |
| Duplicate resource | 409 |
| Duplicate enrollment | 409 |
| Invalid relationship | 409 |

## Database & MySQL Optimization

The schema is hand-written and versioned with **Flyway** (`src/main/resources/db/migration`), since Spring Data JDBC has no `ddl-auto` — this is treated as a feature, not a gap: every constraint and index is explicit and reviewable. For a system of this size, "optimization" means getting the fundamentals right rather than premature tuning:

- **`utf8mb4` / `utf8mb4_0900_ai_ci`** everywhere (set at the database and connection level) — correct Unicode storage (names, titles) and case-insensitive comparison for lookups like email/title search, without extra `LOWER()` calls.
- **InnoDB** (MySQL 8 default) for every table — row-level locking and real foreign-key constraints, which is what lets the database itself enforce "a book's owner must exist" and "an enrollment must reference a real student and course" alongside the application-level checks.
- **Explicit indexes** matched to the actual access patterns from the use cases: `UNIQUE` on `student_code`, `email`, `isbn`, `course_code` (uniqueness constraints already create these); a secondary index on `book.student_id` for "books owned by student" and "unassign on student delete"; the `enrollment` composite primary key `(student_id, course_code)` already covers "is this pair enrolled" and "roster for a course" lookups without an extra index.
- **No N+1 by construction**: Spring Data JDBC doesn't lazy-load, so a roster or "student detail with books + enrollments" view is written as one explicit query (or one query per aggregate collection) rather than triggering one row-by-row.
- **HikariCP** (Spring Boot default) left at its default pool size — right for a system with modest concurrency; only worth revisiting once real load-testing says otherwise.

## Development Phases

Built as **vertical slices** — one module end to end (domain → port → JDBC adapter → application service → controller → tests) before starting the next, so there is a working endpoint early rather than after every module is scaffolded.

1. Skeleton: Modulith dependencies, five module declarations, the verification test, Flyway `V1__init.sql`
2. `shared`: exception hierarchy, error envelopes, global handler, Spring Security baseline
3. `student`: the reference module, operations 1–5, plus the cross-module API and `StudentDeleted`
4. `course`: structurally a twin of `student`, operations 8–10
5. `book`: the first cross-module edge, the `StudentDeleted` listener
6. `enrollment`: two cross-module dependencies, both cleanup listeners
7. Cross-cutting: pagination, sorting, search, Swagger/OpenAPI
8. Testing (module slices, integration, Testcontainers)

Full sequencing and rationale in [docs/SA-docs/](./docs/SA-docs/).

## Roadmap

**MVP**: Student/Book/Course CRUD, Student–Book (1:N), Student–Course (N:M), DTOs, validation, exception handling, authentication.

**Internship-ready**: MVP + pagination, search, filtering, unit/integration tests, Testcontainers, Docker Compose, Swagger/OpenAPI. A later extension would give `Enrollment` a `status` (`ENROLLED` / `DROPPED` / `COMPLETED`) and a `finalGrade`.

For full detail and rationale behind each decision, see [docs/BA-docs/req.md](./docs/BA-docs/req.md).
