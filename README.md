# Student Management System — REST API

A production-oriented **Student Management System** backend, built as a portfolio-grade Spring Boot REST API. It manages students, books, and courses with proper layered architecture, validation, exception handling, and relational data integrity.

> Full requirements: [req.md](./req.md)
> API contract: [api-contract/openapi.yml](./api-contract/openapi.yml)
> Database schema: [database/schema.mermaid](./database/schema.mermaid)

## Tech Stack

- Java + Spring Boot
- Spring Web, Spring Data JPA
- PostgreSQL + Hibernate
- Jakarta Bean Validation
- JUnit 5, Mockito, MockMvc, Testcontainers

## Architecture

```text
Client
   │
   ▼
REST Controller
   │
   ▼
Service Layer
   │
   ▼
Repository Layer
   │
   ▼
PostgreSQL
```

Business logic lives in the service layer — controllers stay thin, and JPA entities are never exposed directly over the API (DTOs only).

## Domain Model

- **Student → Book**: one-to-many. A student can own many books; a book belongs to at most one student (`student_id` is nullable).
- **Student ↔ Course**: many-to-many via a `student_course` join table, with a unique `(student_id, course_id)` constraint.

```text
Student 1 ─────────── N Book
Student N ─────────── N Course
```

See [database/schema.mermaid](./database/schema.mermaid) for the full ER diagram.

## API Overview

Base path: `/api/v1`

| Resource | Endpoints |
| --- | --- |
| Students | `POST /students`, `GET /students`, `GET /students/{id}`, `PUT /students/{id}`, `DELETE /students/{id}` |
| Books | `POST /books`, `GET /books`, `GET /books/{id}`, `PUT /books/{id}`, `DELETE /books/{id}` |
| Courses | `POST /courses`, `GET /courses`, `GET /courses/{id}`, `PUT /courses/{id}`, `DELETE /courses/{id}` |
| Student–Book | `POST /students/{studentId}/books/{bookId}` (assign), `GET /students/{studentId}/books`, `GET /books/{bookId}/owner`, `DELETE /students/{studentId}/books/{bookId}` (unassign) |
| Student–Course | `POST /students/{studentId}/courses/{courseId}` (enroll), `GET /students/{studentId}/courses`, `GET /courses/{courseId}/students`, `DELETE /students/{studentId}/courses/{courseId}` (unenroll) |

Full request/response schemas are defined in [api-contract/openapi.yml](./api-contract/openapi.yml).

## Key Business Rules

- `studentCode`, `email`, `isbn`, and `courseCode` must each be unique.
- A student cannot enroll in the same course twice (`409 Conflict`).
- Unassigning a book or unenrolling a student never deletes the underlying entity — only the relationship.
- Deleting a student sets `book.student_id = NULL` for their books and removes their course enrollments; the books and courses themselves remain.
- Deleting a course removes its `student_course` enrollment records; students remain.

| Exception | HTTP Status |
| --- | --- |
| Resource not found | 404 |
| Validation failed | 400 |
| Duplicate resource | 409 |
| Duplicate enrollment | 409 |
| Invalid relationship | 400 |

## Development Phases

1. Project setup (Spring Boot, PostgreSQL, JPA)
2. Student CRUD
3. Book CRUD
4. Student–Book relationship
5. Course CRUD
6. Student–Course many-to-many relationship
7. Production quality (DTOs, validation, exception handling, transactions, pagination, sorting, filtering, logging, OpenAPI)
8. Testing (unit, integration, Testcontainers)

## Roadmap

**MVP**: Student/Book/Course CRUD, Student–Book (1:N), Student–Course (N:M), DTOs, validation, exception handling.

**Internship-ready**: MVP + pagination, search, filtering, unit/integration tests, Testcontainers, Docker Compose, Swagger/OpenAPI, explicit `Enrollment` entity (status: `ENROLLED` / `DROPPED` / `COMPLETED`, `enrolledAt`, `finalGrade`) in place of a plain `@ManyToMany`.

For full detail and rationale behind each decision, see [req.md](./req.md).
