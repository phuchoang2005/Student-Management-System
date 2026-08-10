# Student Management System — REST API

A production-oriented **Student Management System** backend, built as a portfolio-grade Spring Boot REST API. It manages students, books, and courses with proper layered architecture, validation, exception handling, and relational data integrity.

> **New here? Read the docs in order:** [docs-modulith-DDD/READING-ORDER.md](./docs-modulith-DDD/READING-ORDER.md)
> Full requirements: [req.md](./docs-modulith-DDD/req.md)
> API contract: [api-contract/openapi.yml](./docs-modulith-DDD/api-contract/openapi.yml)
> Database schema: [database/schema.mermaid](./docs-modulith-DDD/database/schema.mermaid)

### Two documentation folders

| Folder | Status |
| --- | --- |
| [docs-modulith-DDD/](./docs-modulith-DDD/) | **Current design** — Spring Modulith application modules + light DDD. Start here. |
| [docs-DDD/](./docs-DDD/) | Archived pure-DDD version — one bounded context, hexagonal layering, policy domain services. Kept for comparison. |

Both describe the same 23 operations, the same status codes, the same database schema and the same OpenAPI contract. What differs is where the code lives and what enforces the boundaries.

## Tech Stack

- Java 21 + Spring Boot 4.1
- Spring Modulith (application modules + build-time boundary verification)
- Spring Web MVC, Spring Data JPA
- PostgreSQL + Hibernate, Flyway
- Jakarta Bean Validation
- JUnit 5, Mockito, MockMvc, `@ApplicationModuleTest`, Testcontainers

## Architecture

One Spring Boot process, one PostgreSQL schema, **five Spring Modulith application modules** — vertical slices, one aggregate and one table each:

```text
org.phuchoang2005.management
├── shared/      ← open module: exceptions, error envelopes, config
├── student/     → shared
├── course/      → shared
├── book/        → shared, student          (Book.ownerId : StudentId)
└── enrollment/  → shared, student, course
```

Each module contains its own controller, service, DTOs, entity and repository. Entities and repositories live in `module/internal/` and are **unreachable from any other module** — a boundary enforced by `ApplicationModules.verify()` running as a JUnit test, not by review. The dependency graph is acyclic; cross-module cleanup (deleting a student releases their books and drops their enrollments) travels backwards along the same edges as **synchronous, in-transaction domain events**.

Business logic lives on the aggregate where it inspects only its own state, and in the service where it needs a repository. Controllers stay thin, and JPA entities are never exposed over the API (DTOs only).

See [module-map.mmd](./docs-modulith-DDD/diagram/modules/module-map.mmd) for the full picture and [modulith-verification.md](./docs-modulith-DDD/modulith-verification.md) for how it is enforced.

## Domain Model

- **Student → Book**: one-to-many. A student can own many books; a book belongs to at most one student (`student_id` is nullable).
- **Student ↔ Course**: many-to-many via a `student_courses` join table, modelled as an explicit `Enrollment` aggregate with a composite primary key.

```text
Student 1 ─────────── N Book
Student N ─────────── N Course
```

Aggregates reference each other **by identity only** — `Book` holds an `ownerId : StudentId`, never a `Student`. There is no `@ManyToOne` and no `@ManyToMany` in the codebase; that rule is what lets each aggregate live in its own module.

See [database/schema.mermaid](./docs-modulith-DDD/database/schema.mermaid) for the full ER diagram.

## API Overview

Base path: `/api/v1`

| Resource | Endpoints |
| --- | --- |
| Students | `POST /students`, `GET /students`, `GET /students/{id}`, `PUT /students/{id}`, `DELETE /students/{id}` |
| Books | `POST /books`, `GET /books`, `GET /books/{id}`, `PUT /books/{id}`, `DELETE /books/{id}` |
| Courses | `POST /courses`, `GET /courses`, `GET /courses/{id}`, `PUT /courses/{id}`, `DELETE /courses/{id}` |
| Student–Book | `POST /students/{studentId}/books/{bookId}` (assign), `GET /students/{studentId}/books`, `GET /books/{bookId}/owner`, `DELETE /students/{studentId}/books/{bookId}` (unassign) |
| Student–Course | `POST /students/{studentId}/courses/{courseId}` (enroll), `GET /students/{studentId}/courses`, `GET /courses/{courseId}/students`, `DELETE /students/{studentId}/courses/{courseId}` (unenroll) |

Full request/response schemas are defined in [api-contract/openapi.yml](./docs-modulith-DDD/api-contract/openapi.yml), and every operation is traced to its module, sequence diagram and business rule in [traceability.md](./docs-modulith-DDD/diagram/traceability.md).

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

## Development Phases

Built as **vertical slices** — one module end to end (entity → repository → service → controller → tests) before starting the next, so there is a working endpoint after phase 2 rather than after phase 6.

1. Skeleton: Modulith dependencies, five module declarations, the verification test, Flyway `V1__init.sql`
2. `shared`: exception hierarchy, error envelopes, global handler
3. `student`: the reference module, operations 1–5, plus the cross-module API and `StudentDeleted`
4. `course`: structurally a twin of `student`, operations 11–15
5. `book`: operations 6–10 and 16–19, the first cross-module edge, the `StudentDeleted` listener
6. `enrollment`: operations 20–23, two cross-module dependencies, both cleanup listeners
7. Cross-cutting: pagination, sorting, search, `Documenter` output, Swagger/OpenAPI
8. Testing (module slices, integration, Testcontainers)

Full sequencing and rationale in [plan-modulithDdd.prompt.md](./docs-modulith-DDD/plan-modulithDdd.prompt.md).

## Roadmap

**MVP**: Student/Book/Course CRUD, Student–Book (1:N), Student–Course (N:M), DTOs, validation, exception handling.

**Internship-ready**: MVP + pagination, search, filtering, unit/integration tests, Testcontainers, Docker Compose, Swagger/OpenAPI, explicit `Enrollment` aggregate in place of a plain `@ManyToMany`. A later extension would give `Enrollment` a `status` (`ENROLLED` / `DROPPED` / `COMPLETED`) and a `finalGrade`.

For full detail and rationale behind each decision, see [req.md](./docs-modulith-DDD/req.md).
