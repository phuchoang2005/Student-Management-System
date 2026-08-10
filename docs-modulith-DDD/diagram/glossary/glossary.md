# Glossary — Ubiquitous Language

One canonical term per concept, used identically in the diagrams, the OpenAPI contract, and the Java code. If a term here disagrees with a name in the code, the code is wrong.

The **domain** and **error** sections below are unchanged from the pure-DDD version of these documents — the business language does not depend on the architecture. Only the **structural** section changed, because the structure did.

## Structural terms

- **Application Module** — A direct sub-package of `org.phuchoang2005.management`, treated as a unit by Spring Modulith. There are five: `shared`, `student`, `course`, `book`, `enrollment`. A module owns at most one aggregate, its own table(s), its own controller, service, entity and repository. Modules are isolated by **package visibility that the build checks**, not by a network boundary and not by review.
- **Published API** — The types in a module's **root** package. Any other module may import them. Keep this set small: a type is published because something else needs it, never by accident.
- **Internal** — Everything in a module **sub-package**, by convention `internal/`. No other module may import it, and `ApplicationModules.verify()` fails the build if one tries. Entities and repositories always live here.
- **Module Dependency** — A declared, compile-time "may import" relation, written as `@ApplicationModule(allowedDependencies = {...})` on the module's `package-info.java`. The full set is drawn in [`../modules/module-map.mmd`](../modules/module-map.mmd) and is **acyclic**.
- **Module Canvas** — The one-page summary of a module: purpose, published types, internal types, dependencies, events in and out, and the invariant it protects. See [`../modules/module-canvas.md`](../modules/module-canvas.md).
- **Aggregate** — An entity that owns its own invariants and is loaded and saved as a unit. There are four, one per non-shared module: `Student`, `Course`, `Book`, `Enrollment`. Each has exactly one repository, and that repository is internal to its module. *(The pure-DDD term was "Aggregate Root"; with one aggregate per module and no nested entities, "root" no longer distinguishes anything.)*
- **Value Object** — Immutable, no identity of its own, compared by value: `StudentId`, `CourseId`, `BookId`, `StudentCode`, `CourseCode`, `ISBN`, `Email`. Each is a Java `record` validating its own format in a static `of()` factory, and each is persisted by a JPA `AttributeConverter` so the database column stays a plain `VARCHAR` or `BIGINT`.
- **Identity Reference** — How one aggregate points at another: by storing its id value object, never an object reference. `Book` holds `ownerId : StudentId`, not a `Student`. There is no `@ManyToOne` and no `@ManyToMany` in this system. This is the rule that lets each aggregate live in its own module.
- **Repository** — Persistence boundary for one aggregate: a single `interface XRepository extends JpaRepository<X, Long>` in `module/internal/`. There is **no** separate domain interface and Spring Data implementation — the module boundary already stops the wrong code from depending on Spring Data.
- **Service** — The use cases of one module, and the owner of the `@Transactional` boundary. Rules that need a repository live here (duplicate enrollment, uniqueness); rules that only inspect one aggregate's own state live on the entity (`Book.assignTo`). A service is not a bag of procedures.
- **Cross-Module API** — The two or three methods on a service that exist for *other modules* rather than for its own controller, e.g. `StudentService.exists(StudentId)`. They return DTOs or value objects, **never an entity**.
- **Domain Event** — A record published by the module that owns a fact, consumed by modules that must react: `StudentDeleted`, `CourseDeleted`. The event type is published API of the module that raises it.
- **Event Listener** — A `@EventListener` method in the reacting module's `internal/` package. In this system listeners are **synchronous and run in the publisher's transaction** — see *Synchronous Event* below. A listener is never published API.
- **Synchronous Event** — This project's deliberate choice: plain `@EventListener`, published *before* the parent row is removed, so cleanup runs inline, in order, in one transaction, and rolls back with everything else. Not `@ApplicationModuleListener` (async, after commit, own transaction) and not `@TransactionalEventListener` (fires at commit, too late for a foreign key). The reasoning is in [`../sequences/delete-student.mmd`](../sequences/delete-student.mmd).
- **DTO / Transport Model** — A module's wire representation (`*CreateRequest`, `*UpdateRequest`, `*Response`). Published, because both the HTTP layer and — for the summary types — other modules consume it. Never imported by an entity.

## Domain terms

- **Student** — Aggregate. `id : StudentId`, `studentCode`, `firstName`, `lastName`, `email`, `dateOfBirth`, plus audit timestamps. `studentCode` and `email` are unique across all students.
- **StudentCode** — Value object. The business-meaningful unique code for a student (e.g. `STU001`), distinct from the database `id`.
- **Email** — Value object. Valid email shape, unique across students.
- **Course** — Aggregate. `id : CourseId`, `courseCode`, `name`, `description`, `credits`, plus audit timestamps. `courseCode` is unique; `credits` must be positive.
- **CourseCode** — Value object. Unique business code for a course (e.g. `CS101`).
- **Book** — Aggregate. `id : BookId`, `isbn`, `title`, `author`, `publishedDate`, `ownerId : StudentId` (nullable), plus audit timestamps. `isbn` is unique.
- **ISBN** — Value object. Unique identifier for a book, 10 or 13 digits.
- **Ownership** — The `Book.ownerId` identity reference (column `books.student_id`). **Invariant: a book has at most one owner.** `ownerId` may be null, meaning unassigned. Ownership is a property *of the Book* and lives entirely in the `book` module; `Student` has no collection of books and does not know books exist.
- **Assign Book** — Command setting `Book.ownerId`. Rejected with 409 if the book is already owned by a *different* student; assigning to the same owner again is idempotent.
- **Unassign Book** — Command clearing `Book.ownerId`. The book is **not** deleted; it becomes unassigned and remains listable.
- **Enrollment** — Aggregate, and an *explicit entity*, not a plain join. Identity is the composite `(studentId, courseId)`; carries `enrolledAt`. Table `student_courses`. **Invariant: no duplicate `(studentId, courseId)`.**
- **Enroll** — Command creating an `Enrollment`. Requires that both the student and the course exist. 409 if already enrolled.
- **Unenroll** — Command deleting an `Enrollment`. 404 if the enrollment does not exist. Both the student and the course survive.
- **Delete Student** — Orchestrated command: publish `StudentDeleted` → the `book` module releases owned books (`ownerId := null`) and the `enrollment` module deletes the student's enrollments → then the student row is removed. Books and courses survive. One transaction.
- **Delete Course** — Orchestrated command: publish `CourseDeleted` → the `enrollment` module deletes the course's enrollments → then the course row is removed. Students survive. One transaction.
- **Delete Book** — Simple command. Ownership disappears with the row because `ownerId` lives on the book. No event, no listener, nothing to clean up. The owner survives.

## Error terms

- **DomainException** — Base type for rule violations, in `shared`. Carries a stable `code` string.
- **ResourceNotFoundException** → **404**. A referenced aggregate, or a required relationship, does not exist.
- **DuplicateResourceException** → **409**. A uniqueness rule was violated (`studentCode`, `email`, `isbn`, `courseCode`, duplicate enrollment).
- **InvalidRelationshipException** → **409**. A relationship rule was violated — currently only "book already owned by another student".
- **Conflict** — Any business rule collision surfaced as **409**, never as 500. See the error table in [`../traceability.md`](../traceability.md).
- **Validation Failure** → **400** with `ValidationErrorResponse.fieldErrors`. Raised by `@Valid` on request DTOs before any service is called.

---

## Terms retired from the pure-DDD version

Read this section only if you have seen the older documents; skip it otherwise.

| Retired term | What replaced it, and why |
|---|---|
| **Bounded Context** | **Application Module.** There was only ever one bounded context, so the term partitioned nothing. The five modules are the real boundaries, and unlike a context map they are checked by a test. |
| **Application Service** | **Service.** With one service per module there is no second kind of service to distinguish it from. It still owns the `@Transactional` boundary. |
| **Domain Service / Policy** (`OwnershipPolicy`, `EnrollmentPolicy`) | The rules moved to where they can see what they need: the single-owner rule onto `Book.assignTo()`, the duplicate-enrollment rule into `EnrollmentService`. Both policies existed only to forward a throw. |
| **Domain repository interface + infrastructure implementation** | One `JpaRepository` per aggregate, internal to its module. The split existed to stop the wrong code depending on Spring Data; module visibility does that now, mechanically. |
| **The `api` / `application` / `domain` / `infrastructure` packages** | Vertical module slices. The layers still exist inside a slice — see [`../modules/module-structure.mmd`](../modules/module-structure.mmd). |
| **Aggregate Root** | **Aggregate.** No aggregate here contains a second entity, so "root" distinguished nothing. |

---

**Scope.** These terms cover the current OpenAPI contract and database schema. Authentication/authorization, grading, attendance, and scheduling are out of scope. Asynchronous events, `@ApplicationModuleListener` and the event publication registry are a deferred option, not current design — see the closing note in [`../modules/module-map.mmd`](../modules/module-map.mmd).
