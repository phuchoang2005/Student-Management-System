# Module Canvas — the five application modules

One page per module. This is the file to open when you are about to write code in a module or review a pull request that touches one: it answers *what may I use, what may use me, and what must I not break.*

`../modules/module-map.mmd` draws the graph, `../modules/module-structure.mmd` draws the inside of a box, and this file writes both down as text you can grep.

> Spring Modulith can generate a canvas like this from the code itself — `new Documenter(modules).writeModuleCanvases()`. Until the code exists, this hand-written one is the specification the generated one should end up agreeing with. See [`../../modulith-verification.md`](../../modulith-verification.md).

---

## At a glance

| Module | Aggregate | Table(s) | Depends on | Publishes events | Consumes events |
|---|---|---|---|---|---|
| `shared` | — | — | *(nothing)* | — | — |
| `student` | `Student` | `students` | `shared` | `StudentDeleted` | — |
| `course` | `Course` | `courses` | `shared` | `CourseDeleted` | — |
| `book` | `Book` | `books` | `shared`, `student` | — | `StudentDeleted` |
| `enrollment` | `Enrollment` | `student_courses` | `shared`, `student`, `course` | — | `StudentDeleted`, `CourseDeleted` |

Read the "Depends on" column top to bottom: it only ever grows. That is the acyclicity `ApplicationModules.verify()` checks.

---

## `shared` — «open module»

**Purpose.** The things every module needs and none of them owns: the exception hierarchy, the HTTP error envelopes, the handler that maps one to the other, and Spring configuration. It holds **no entity, no repository, no table and no business rule**.

| | |
|---|---|
| **Declaration** | `@ApplicationModule(type = Type.OPEN)` |
| **Allowed dependencies** | none |
| **Published** | `DomainException` (abstract, carries `code`) · `ResourceNotFoundException` · `DuplicateResourceException` · `InvalidRelationshipException` · `ErrorResponse` · `ValidationErrorResponse` · `PageMetadata` |
| **Internal** | `GlobalExceptionHandler` (`@RestControllerAdvice`) · `JpaAuditingConfig` (`@EnableJpaAuditing`) · `OpenApiConfig` |
| **Invariant** | If a class in `shared` knows what a Student *is*, it is in the wrong module. |

**Why it is open.** An open module is exempt from the internal-is-hidden rule, so `shared` never has to declare named interfaces for the four modules that use it. That exemption is safe here for exactly one reason — `shared` holds no rule. The day a rule lands in it, close it.

---

## `student`

**Purpose.** The student aggregate and its lifecycle. Also the **identity provider** for the rest of the application: `book` and `enrollment` both reference a student, and both do it through types published here.

| | |
|---|---|
| **Declaration** | `@ApplicationModule(allowedDependencies = {"shared"})` |
| **Aggregate** | `Student` — `id`, `studentCode`, `firstName`, `lastName`, `email`, `dateOfBirth`, `createdAt`, `updatedAt` |
| **Table** | `students` — `UNIQUE(student_code)`, `UNIQUE(email)` |
| **Invariant** | `studentCode` and `email` are unique across all students. Uniqueness on **update excludes self**. |

**Published**

| Type | Why it is public |
|---|---|
| `StudentController` | the HTTP boundary for ops 1–5 |
| `StudentService` | the use cases, **and the cross-module API** (below) |
| `StudentId` | `book` and `enrollment` store it — rule M1 |
| `StudentCode`, `Email` | value objects on the aggregate; published for symmetry and testing |
| `StudentDeleted` | the domain event `book` and `enrollment` listen for |
| `StudentCreateRequest`, `StudentUpdateRequest`, `StudentResponse`, `StudentSummaryResponse` | the wire shapes; `StudentSummaryResponse` is returned to other modules |

**Internal** — `Student` (`@Entity`) · `StudentRepository` · `StudentMapper` · `StudentCodeConverter` · `EmailConverter`

**The cross-module API.** These three methods are the *only* way another module reaches a student. Everything else on `StudentService` serves `StudentController`.

```java
boolean exists(StudentId id);                                   // book, enrollment — the 404 guard
Optional<StudentSummaryResponse> summaryOf(StudentId id);       // book — GET /books/{id}/owner
List<StudentSummaryResponse> summariesOf(List<StudentId> ids);  // enrollment — GET /courses/{id}/students
```

They return **DTOs, never the `Student` entity** — which no other module could import anyway.

**Publishes** `StudentDeleted(StudentId studentId)` — synchronously, **before** the row is removed. See [`../sequences/delete-student.mmd`](../sequences/delete-student.mmd) for why the ordering is not negotiable.

**Consumes** nothing. `student` is upstream of everything; that is what keeps the graph acyclic.

---

## `course`

**Purpose.** The course aggregate and its lifecycle. Structurally a twin of `student` with one fewer unique constraint and one extra check.

| | |
|---|---|
| **Declaration** | `@ApplicationModule(allowedDependencies = {"shared"})` |
| **Aggregate** | `Course` — `id`, `courseCode`, `name`, `description`, `credits`, `createdAt`, `updatedAt` |
| **Table** | `courses` — `UNIQUE(course_code)`, `CHECK(credits > 0)` |
| **Invariant** | `courseCode` unique (excluding self on update); `credits` strictly positive. |

**Published** — `CourseController` · `CourseService` · `CourseId` · `CourseCode` · `CourseDeleted` · `CourseCreateRequest` · `CourseUpdateRequest` · `CourseResponse`

**Internal** — `Course` (`@Entity`) · `CourseRepository` · `CourseMapper` · `CourseCodeConverter`

**The cross-module API** — used only by `enrollment`:

```java
boolean exists(CourseId id);                                 // the 404 guard
List<CourseResponse> responsesOf(List<CourseId> ids);        // GET /students/{id}/courses
```

**Publishes** `CourseDeleted(CourseId courseId)` — synchronously, before the row is removed.

**Consumes** nothing.

---

## `book`

**Purpose.** The book aggregate **and ownership**. Ownership is a property of the book (`books.student_id`), so it lives entirely here — `student` has no collection of books and no knowledge that books exist.

| | |
|---|---|
| **Declaration** | `@ApplicationModule(allowedDependencies = {"shared", "student"})` |
| **Aggregate** | `Book` — `id`, `isbn`, `title`, `author`, `publishedDate`, `ownerId` *(nullable)*, `createdAt`, `updatedAt` |
| **Table** | `books` — `UNIQUE(isbn)`, `student_id` nullable FK → `students(id)` |
| **Invariant** | **A book has at most one owner.** Enforced by `Book.assignTo()`, which throws `InvalidRelationshipException` when the book is already owned by a *different* student. Re-assigning to the same owner is idempotent. |

**Published** — `BookController` · `StudentBookController` · `BookService` · `BookOwnershipService` · `BookId` · `ISBN` · `BookCreateRequest` · `BookUpdateRequest` · `BookResponse`

**Internal** — `Book` (`@Entity`) · `BookRepository` · `BookMapper` · `IsbnConverter` · `StudentIdConverter` · `StudentDeletedListener`

**What it uses from `student`** — `StudentId` (stored as `ownerId`), `StudentService.exists()` (the 404 on assign), `StudentService.summaryOf()` (the body of `GET /books/{id}/owner`), and `StudentDeleted` (the listener).

**Consumes** `StudentDeleted` → releases every book owned by that student (`ownerId := null`). The books survive as unowned; see [`../states/book-ownership.mmd`](../states/book-ownership.mmd).

**Publishes** nothing. Deleting a book touches nothing else — ownership dies with the row.

**Note on two services.** `BookService` owns plain CRUD (ops 6–10); `BookOwnershipService` owns the relationship operations (ops 16–19) and is the only one that talks to `student`. Splitting them keeps the cross-module dependency in one small class instead of smeared across book CRUD.

---

## `enrollment`

**Purpose.** The many-to-many between students and courses, modelled as an **explicit aggregate** rather than a `@ManyToMany` — which is also what makes the module boundaries possible, since a `@ManyToMany` would force `Student` to hold a `Set<Course>` and collapse two modules into one.

| | |
|---|---|
| **Declaration** | `@ApplicationModule(allowedDependencies = {"shared", "student", "course"})` |
| **Aggregate** | `Enrollment` — identity is the composite `(studentId, courseId)`; carries `enrolledAt` |
| **Table** | `student_courses` — `PRIMARY KEY(student_id, course_id)` |
| **Invariant** | **No duplicate `(studentId, courseId)`.** Checked by `EnrollmentService` before insert and backed by the composite primary key. |

**Published** — `StudentCourseController` (ops 20–23, including `GET /courses/{id}/students`) · `EnrollmentService` · `EnrollmentResponse`

**Internal** — `Enrollment` (`@Entity`) · `EnrollmentId` (`@Embeddable`) · `EnrollmentRepository` · `StudentDeletedListener` · `CourseDeletedListener`

**What it uses from its two peers** — `StudentId` / `CourseId` (the composite identity), `StudentService.exists()` and `CourseService.exists()` (the two 404s), `StudentService.summariesOf()` and `CourseService.responsesOf()` (the two list endpoints), plus both event types.

**Consumes** `StudentDeleted` → `deleteByStudentId`; `CourseDeleted` → `deleteByCourseId`. Both are the *only* cleanup either parent needs from this module, and both run inside the parent's transaction.

**Publishes** nothing. Nothing downstream of enrollment exists.

**Why the duplicate check is in the service, not on the entity.** The rule needs a repository lookup, and an entity that holds a repository is not an entity. `Book`'s single-owner rule *can* live on the entity because it only inspects the book's own state — hence the asymmetry between these two modules, which is deliberate and worth being able to explain.

---

## Checklist for a pull request touching any module

1. Did anything new land in a module's **root** package? If so, it is now public API — was that intended?
2. Does a **new import** cross a module boundary? It must be listed in that module's `allowedDependencies`, or the build is already broken.
3. Did an **entity or repository** leak out of `internal/`?
4. Is there a **new `@ManyToOne` / `@ManyToMany`** to another aggregate root? Rule M1 — use the identity value object.
5. Does a new cross-module call return an **entity** instead of a DTO?
6. Did a **rule** land in `shared`?
7. Does a new listener use `@ApplicationModuleListener` or `@TransactionalEventListener`? Both are wrong here — rule M4, and the reasoning is in [`../sequences/delete-student.mmd`](../sequences/delete-student.mmd).
8. Run `./mvnw test -Dtest=ModuleStructureTest`. That answers 1–4 mechanically; 5–7 still need a human.
