# Glossary — Ubiquitous Language

One canonical term per concept, used identically in the diagrams, the OpenAPI contract, and the Java code. If a term here disagrees with a name in the code, the code is wrong.

## Structural terms

- **Bounded Context** — There is exactly **one**: *Student Management*. Everything in this project lives inside it.
- **Module** — A partition *inside* the bounded context, realised as a Java package: `academic` (Student, Course, Enrollment) and `library` (Book). Modules are isolated by package boundaries and the identity-reference rule, **not** by a network boundary. Earlier drafts called these "bounded contexts"; that was wrong and is retired.
- **Aggregate Root** — An object that owns its own invariants and is loaded and saved as a unit. There are four: `Student`, `Course`, `Enrollment`, `Book`. Each has exactly one repository.
- **Value Object** — Immutable, no identity of its own, compared by value: `StudentId`, `CourseId`, `BookId`, `StudentCode`, `CourseCode`, `ISBN`, `Email`. Each validates its own format in a static factory.
- **Identity Reference** — How one aggregate points at another: by storing its id value object, never an object reference. `Book` holds `ownerId : StudentId`, not a `Student`. This is the rule that keeps the modules separable.
- **Repository** — Persistence boundary for one aggregate root. Interface in `domain`, Spring Data implementation in `infrastructure.persistence`.
- **Domain Service / Policy** — Stateless holder of a rule that spans more than one object: `OwnershipPolicy`, `EnrollmentPolicy`. Policies throw; they do not return booleans to be ignored.
- **Application Service** — Orchestrates one use case and owns the `@Transactional` boundary. The only place allowed to touch two aggregates in one call.
- **DTO / Transport Model** — API-layer representation (`*CreateRequest`, `*UpdateRequest`, `*Response`). Lives in `api.dto`, never inside the domain.

## Domain terms

- **Student** — Aggregate root. `id : StudentId`, `studentCode`, `firstName`, `lastName`, `email`, `dateOfBirth`, plus audit timestamps. `studentCode` and `email` are unique across all students.
- **StudentCode** — Value object. The business-meaningful unique code for a student (e.g. `STU001`), distinct from the database `id`.
- **Email** — Value object. Valid email shape, unique across students.
- **Course** — Aggregate root. `id : CourseId`, `courseCode`, `name`, `description`, `credits`, plus audit timestamps. `courseCode` is unique; `credits` must be positive.
- **CourseCode** — Value object. Unique business code for a course (e.g. `CS101`).
- **Book** — Aggregate root. `id : BookId`, `isbn`, `title`, `author`, `publishedDate`, `ownerId : StudentId` (nullable), plus audit timestamps. `isbn` is unique.
- **ISBN** — Value object. Unique identifier for a book, 10 or 13 digits.
- **Ownership** — The `Book.ownerId` identity reference (column `books.student_id`). **Invariant: a book has at most one owner.** `ownerId` may be null, meaning unassigned. Ownership is a property *of the Book*; `Student` has no collection of books.
- **Assign Book** — Command setting `Book.ownerId`. Rejected with 409 if the book is already owned by a *different* student; assigning to the same owner again is idempotent.
- **Unassign Book** — Command clearing `Book.ownerId`. The book is **not** deleted; it becomes unassigned and remains listable.
- **Enrollment** — Aggregate root, and an *explicit entity*, not a plain join. Identity is the composite `(studentId, courseId)`; carries `enrolledAt`. Table `student_courses`. **Invariant: no duplicate `(studentId, courseId)`.**
- **Enroll** — Command creating an `Enrollment`. Requires that both the student and the course exist. 409 if already enrolled.
- **Unenroll** — Command deleting an `Enrollment`. 404 if the enrollment does not exist. Both the student and the course survive.
- **Delete Student** — Orchestrated command: release owned books (`ownerId := null`) → delete the student's enrollments → delete the student. Books and courses survive. One transaction.
- **Delete Course** — Orchestrated command: delete the course's enrollments → delete the course. Students survive. One transaction.
- **Delete Book** — Simple command. Ownership disappears with the row because `ownerId` lives on the book. The owner survives.

## Error terms

- **DomainException** — Base type for rule violations thrown by the domain. Carries a stable `code` string.
- **ResourceNotFoundException** → **404**. A referenced aggregate, or a required relationship, does not exist.
- **DuplicateResourceException** → **409**. A uniqueness rule was violated (`studentCode`, `email`, `isbn`, `courseCode`, duplicate enrollment).
- **InvalidRelationshipException** → **409**. A relationship rule was violated — currently only "book already owned by another student".
- **Conflict** — Any business rule collision surfaced as **409**, never as 500. See the error table in [`../traceability.md`](../traceability.md).
- **Validation Failure** → **400** with `ValidationErrorResponse.fieldErrors`. Raised by `@Valid` on request DTOs before any service is called.

---

**Scope.** These terms cover the current OpenAPI contract and database schema. Authentication/authorization, grading, attendance, and scheduling are out of scope. Domain events (`StudentDeleted`, `BookAssigned`, `StudentEnrolled`) are a deferred option, not current design — see the closing note in [`../context/context-map.mmd`](../context/context-map.mmd).
