# Tactical Domain-Driven Design

A companion artifact to the Solution Architecture document set — not part of the [01–05 numbered series](./01-system-overview.md), the same status [api-specification.md](./api-specification.md) already holds. Where those five documents describe *layers* (hexagonal `web/`→`application/`→`domain/`) and *modules* (Spring Modulith boundaries), this document names the **tactical DDD building blocks** sitting inside that shape — Aggregates, Entities, Value Objects, Domain Events, Repositories, Domain Services, Application Services, Factories — and maps every one of them to a class name that already appears in [02-component-diagram.md](./02-component-diagram.md), [03-sequence-diagrams.md](./03-sequence-diagrams.md), [04-authentication-authorization.md](./04-authentication-authorization.md), or [05-database-schema.md](./05-database-schema.md). Nothing here is a new architectural decision; it is the existing design re-read through Evans/Vernon's tactical vocabulary, so that vocabulary can guide the implementation that doesn't exist yet.

Business invariants are quoted from [../BA-docs/req.md](../BA-docs/req.md) throughout (`Student.1`–`5`, `Book.1`–`5`, `Course.1`–`3`, `Enrollment.1`–`4`, `Identity.1`–`5`) — the same numbering the rest of the doc set already uses.

---

## 1. Purpose & Scope

This document answers: *for each of the five Spring Modulith modules, what is the Aggregate, what are its Entities and Value Objects, which class is its Factory, which class is its Repository, what Domain Events does it publish, and where — if anywhere — does a Domain Service belong?* It also names the one strategic-DDD concept unavoidable at the edges (modules as bounded contexts, published interfaces as Open Host Service) only far enough to justify tactical choices already fixed elsewhere — it is not a context-mapping exercise in its own right.

**Out of scope**, deliberately: CQRS, event sourcing, sagas/process managers, optimistic-locking/versioning strategy, and any bounded-context split beyond the five modules `01-system-overview.md` already fixes as one schema, one process. See §13.

## 2. Ubiquitous Language & Bounded Contexts

Each Spring Modulith module *is* a bounded context in miniature — small enough that the ubiquitous language barely shifts between them, but each still owns its own aggregate and its own meaning for shared-sounding words (e.g. "owner" only means something inside `book`; `student` has no concept of ownership at all).

| Module (bounded context) | Aggregate | Core vocabulary |
| --- | --- | --- |
| `student` | `Student` | register, code, enroll-eligible, own (a book) |
| `course` | `Course` | offering, credits, roster |
| `book` | `Book` | catalog, ISBN, owner, unassigned |
| `enrollment` | `Enrollment` | enroll, withdraw/end, duplicate |
| `identity` | `User` | principal, credential, must-change-password, provision |
| `shared` | — | no domain vocabulary; cross-cutting only (error envelope, security filter chain) — confirmed in `02-component-diagram.md` §1 |

`shared` is deliberately absent from the aggregate list below — it is infrastructure, not a bounded context; it has no ubiquitous language of its own, matching Evans' guidance that a technical/generic subdomain doesn't need one.

## 3. Aggregates & Aggregate Roots

Every aggregate in this system happens to be a **single-entity aggregate**: the root has no internal child entities with their own identity (no `OrderLine`-under-`Order` shape anywhere in this domain). This is a genuine characteristic of the domain, not a simplification imposed by this document — `req.md` never describes a business entity nested inside another. It's worth stating explicitly because it means every "Entity" in this system (§4) is also an aggregate root; there's no separate non-root entity to enumerate.

| Aggregate root | Identity | Invariants enforced | Why it's its own aggregate |
| --- | --- | --- | --- |
| `Student` | `StudentId` (surrogate) / `StudentCode` (business key) | Student.1–4 | The unit Registrar CRUD operates on; referenced by id only from `book`/`enrollment`, never embedded — keeps it small and independently transactable (UC-1/2/3). The two identifiers have a strict division of labour: `StudentId` is what the FK columns store, `StudentCode` is what every caller outside the process ever names (api-specification.md §5 decision #9), and `StudentLookup.idOf` is the single translation between them. |
| `Course` | `CourseId` / `CourseCode` | Course.1–3 | Independently created/updated/removed by Course Administrator (UC-8/9/10); enrollments reference it by code, never hold a `Course` object. |
| `Book` | `BookId` / `Isbn` | Book.1–5 | Ownership is a single nullable reference (`ownerId: StudentId`), not a collection — this is precisely the DDD rule "reference other aggregates by identity, not by object," already called out for exactly this reason in `02-component-diagram.md` §2.5. |
| `Enrollment` | `EnrollmentId` (surrogate); conceptual business key `(studentId, courseCode)` | Enrollment.1–4 | Modeled as its own aggregate rather than a child collection under `Student` or `Course` because it has its own lifecycle (create/end, UC-11/12) and a uniqueness invariant that spans *two* other aggregates (Enrollment.1) — a child-entity shape would force one of `Student`/`Course` to own a rule about the other, which neither should. |
| `User` | `UserId` / `Username` | Identity.1–7 | Owns credential state (`passwordHash`, `initialPasswordEncrypted`, `mustChangePassword`, `enabled`) — a distinct lifecycle from `Student`'s, even though every Student has exactly one `User` (req.md §3), and staff `User`s have no `Student` at all. Kept as a separate aggregate, not embedded in `Student`, so that `student`'s aggregate stays free of authentication concerns; `04-authentication-authorization.md` §2.1 makes the identical call for the same reason. |

**Aggregate design rules already in force** (each one traceable to an existing decision elsewhere in the doc set):

- **Reference by identity, never by object.** `Book.ownerId` is a `StudentId`, `Enrollment.studentId`/`courseCode` are plain identifiers, `User.studentId` is a plain FK-shaped id — never a live `Student` reference. Already stated as a rule in `02-component-diagram.md` §2.5 and mirrored at the schema level in `05-database-schema.md` §2 ("plain FK column, never an ORM-level relationship mapping").
- **One transaction touches one aggregate**, with a single deliberate exception (§9): everything that spans aggregates after the fact (student/course deletion cascades) goes through a Domain Event, not a multi-aggregate save (§9).
- **Small aggregates.** None of the five aggregates carries a collection of child entities; the largest, `Student`, still only holds scalar fields — `books`/`courses` shown in `StudentDetail` (per the OpenAPI contract) are read-time compositions from other modules' repositories, not part of the `Student` aggregate's persisted state.

## 4. Entities vs. Value Objects

Because every aggregate here is single-entity (§3), the "Entity" list and the "Aggregate root" list are the same five classes. The interesting tactical work is in the **Value Objects** — the immutable, equality-by-value types each aggregate is built from instead of primitive strings/longs:

| Value Object | Used by | Encodes |
| --- | --- | --- |
| `StudentId` | `Student` (self), `Book.ownerId`, `Enrollment.studentId`, `User.studentId` | Cross-aggregate reference target — Student.5, Book.4, Enrollment.3 |
| `StudentCode` | `Student`; the *input* form of `Book.ownerId` and `Enrollment.studentId` | Student.1 (uniqueness enforced via repository, format via the VO's constructor). **Published Language** — it lives at `student`'s module root beside `StudentId`, not in `domain/`, precisely because `book`, `enrollment`, and every `web/` DTO name a student with it (06-low-level-design.md §4.3). |
| `Email` | `Student`, source of `Username` for a Student's `User` | Student.2 (format validity is a VO-constructor concern per `05-database-schema.md` §4: "format validation is application-level, not a DB concern") |
| `DateOfBirth` | `Student` | Student.4 |
| `CourseId` / `CourseCode` | `Course` (self), `Enrollment.courseCode` | Course.1, Enrollment.2 |
| `Credits` | `Course` | Course.3 (positive-integer invariant lives in the VO constructor, not scattered null/range checks in the service) |
| `Isbn` | `Book` | Book.1 |
| `Role` | `User` | Identity — fixed 4-value enum, already named in `01-system-overview.md` §2 |
| `Username` | `User` | Identity.2; equals `Email` at provisioning time for a Student account (req.md §3) but is its own VO on `User` since staff usernames have no `Student` behind them |
| `PasswordHash` | `User` | One-way BCrypt digest; a VO specifically *because* it should never expose its raw bytes or be compared except via `PasswordEncoder.matches(...)` |
| `EncryptedInitialPassword` | `User` | Nullable AES ciphertext; Identity.4/5 — the VO's only legal transitions are "set once at provisioning" and "cleared forever at first change," which is worth encoding in the type rather than leaving as a bare nullable string |

**Recommended refinement, not an existing decision:** `passwordHash`, `initialPasswordEncrypted`, and `mustChangePassword` on `User` always change together — at provisioning (all three set) and at first password change (`hash` replaced, `initialPasswordEncrypted` cleared, `mustChangePassword` flipped false — see `04-authentication-authorization.md` §5.1's `Agg.hash(...)`/`Agg.encrypt(...)`/`Agg.changePassword(...)` step). Grouping the three into one `Credential` value object on `User` would make that co-transition impossible to do partially by construction. This is offered as an implementation option, not a requirement — nothing in the SA docs currently names such a type.

## 5. Domain Services

A domain service is warranted only for an operation that (a) doesn't naturally belong to any one Entity/VO and (b) isn't just data lookup. Checked against every operation in this system:

- **Uniqueness checks** (`existsByCode`, `existsByEmail`, `existsByIsbn`, `existsByStudentAndCourse`, `existsByUsername`) are **not** modeled as domain services here — they're `Repository` queries the `Application Service` calls directly before invoking the aggregate's factory method (visible throughout `03-sequence-diagrams.md`, e.g. §2.1's `Svc->>Repo: existsByCode(code)` happening before `Svc->>Agg: create(...)`). This is the pragmatic, explicitly Vernon-endorsed shape: a plain repository query orchestrated by the application service, not a dedicated class, because the operation is a lookup, not a business computation.
- **Initial-password generation** (`04-authentication-authorization.md` §3: "generate 8-char alphanumeric password (SecureRandom)") is the one operation in this system that's a genuine domain-service candidate: it's a business policy (Identity.1/3 — every new Student account needs a system-issued temporary password of a specific shape), it doesn't belong on the `User` aggregate itself (an aggregate shouldn't own a `SecureRandom`/generation-policy dependency), and it isn't a lookup. The existing sequence diagram already keeps it out of the aggregate — `IdentitySvc->>IdentitySvc: generate password` runs *before* `IdentitySvc->>Agg: create(...)` — which is the right shape; formalizing it as a named collaborator (e.g. `InitialPasswordGenerator`, injected into `IdentityService`) rather than an inline `IdentityService` method is an optional naming refinement, not a structural change.
- **Password hashing/encryption** (`PasswordEncoder.matches(...)`, BCrypt hash, AES encrypt/decrypt) are infrastructure concerns exposed as ports the aggregate or application service depends on — not domain services in the DDD sense, since they encode no business rule of their own (the business rule is "when to hash/encrypt/clear," which lives in `User`'s factory method and `changePassword` behavior, per §4's `Credential` discussion).

No other cross-aggregate business computation exists in this domain — every remaining rule (Book.2/3, Enrollment.1, etc.) is either a single-aggregate invariant or a cross-aggregate *existence* check already covered by the published-interface pattern (§10).

## 6. Application Services (Use-Case Orchestrators)

Each is a **thin orchestrator**: no business rule of its own, only the sequencing already fixed in `03-sequence-diagrams.md` — validate uniqueness/existence via a repository or published interface, invoke the aggregate's factory or behavior method, persist via the repository port, publish a domain event if the use case is a deletion.

| Module | Application Service | Owns (UC) | Orchestration shape |
| --- | --- | --- | --- |
| `student` | `StudentService` | UC-1, 2, 3, 13, 17 | uniqueness check → `Student.register(...)` → save → `AccountProvisioning.provisionForStudent(...)` (same transaction) → publish `StudentDeleted` on remove |
| `course` | `CourseService` | UC-8, 9, 10, 15, 19 | uniqueness check → `Course.create(...)` → save → publish `CourseDeleted` on remove |
| `book` | `BookService` | UC-4, 5, 6, 7, 14, 18 | uniqueness check → `StudentLookup.idOf(...)` (Book.4 — the existence check and the code→id resolution are one call) → `Book.create(...)`/`assignOwner(...)`/`clearOwner(...)` → save |
| `enrollment` | `EnrollmentService` | UC-11, 12, 20 | `StudentLookup.idOf(...)`/`CourseLookup.existsByCode(...)` existence checks → duplicate check → `Enrollment.create(...)` → save |
| `identity` | `IdentityService` | UC-21, 22, 23 (+ provisioning tail of UC-1) | password-policy checks (§5.2 of the auth doc) → `User.changePassword(...)`/factory → save |

None of these classes contains an `if` statement enforcing a business invariant directly — every invariant check either delegates to the aggregate (which throws a domain exception) or to a repository/published-interface boolean the service branches on. This is the Application Service / Domain Model split doing its job: the service is easy to read as a checklist, the aggregate is where the actual rule lives and is unit-testable without Spring or a database (`02-component-diagram.md` §3 already makes this claim about `domain/`; this section is that claim, generalized to all five modules).

## 7. Repositories (Ports)

DDD's rule that **only aggregate roots get repositories** holds trivially here since every aggregate is single-entity (§3) — there is no temptation to give a child entity its own repository. One port per aggregate root, each already named in `02-component-diagram.md` §2.1/§3:

| Aggregate root | Port (domain-owned interface) | Adapter (`internal/`) | Representative methods |
| --- | --- | --- | --- |
| `Student` | `StudentRepository` | `JdbcStudentRepository` | `findByCode`, `existsByCode`, `existsByEmail`, `save`, `deleteByCode` |
| `Course` | `CourseRepository` | `JdbcCourseRepository` | `findByCode`, `existsByCode`, `save`, `deleteByCode` |
| `Book` | `BookRepository` | `JdbcBookRepository` | `findByIsbn`, `existsByIsbn`, `findByOwnerId`, `clearOwnerByStudentId`, `save`, `deleteByIsbn` |
| `Enrollment` | `EnrollmentRepository` | `JdbcEnrollmentRepository` | `existsByStudentAndCourse`, `findByStudentAndCourse`, `deleteByStudentId`, `deleteByCourseCode` |
| `User` | `UserRepository` | `JdbcUserRepository` | `findByUsername`, `findByStudentCode`, `existsByUsername`, `save` |

Each port lists only the methods its Application Service actually calls (`02-component-diagram.md` §3: "not a generic `CrudRepository` leaked outward") — the table above is a direct read of the calls already shown across every `03-sequence-diagrams.md` sequence for that module.

## 8. Factories

Where creating an aggregate involves enforcing an invariant — not just calling a constructor — a **factory method on the aggregate itself** is the right tool (Evans favors this over a separate Factory class when the logic is simple enough to live with the thing it creates, which is the case for all five here: no aggregate assembly in this domain requires consulting more than the fields already passed in).

| Aggregate | Factory method | Invariants enforced at creation |
| --- | --- | --- |
| `Student` | `Student.register(code, firstName, lastName, email, dob)` | Student.3 (non-blank names), Student.4 (valid DOB) — Student.1/2 (uniqueness) are checked by `StudentService` *before* this call, since they require the repository, which the aggregate must not depend on |
| `Course` | `Course.create(code, name, description, credits)` | Course.2 (non-blank name), Course.3 (positive credits) |
| `Book` | `Book.create(isbn, title, author, publishedDate, ownerId?)` | Book.2/3 (single optional owner — enforced by the field's type, `StudentId?`, not a collection) |
| `Enrollment` | `Enrollment.create(studentId, courseCode)` | Structural only (non-null references) — Enrollment.1/2/3 are all checked by `EnrollmentService` beforehand via repository/published-interface calls |
| `User` | `User.provisionForStudent(username, role=STUDENT, studentId, plaintextPassword)` | Identity.3 (`mustChangePassword = true` at creation); hashes/encrypts the pre-generated plaintext (§5) but does not generate it itself |

No aggregate here needs a *standalone* Factory class (as opposed to a factory method) because none of them assembles from multiple other aggregates or requires infrastructure access during construction — the moment `User`'s creation needed a `SecureRandom`-backed policy, that policy was correctly pushed to a caller-supplied value (§5), keeping the factory method itself pure and deterministic.

## 9. Domain Events

Both events already named in `02-component-diagram.md` §2.3 and detailed in `03-sequence-diagrams.md` §6, restated here in tactical-pattern terms:

| Event | Publisher | Trigger | Payload | Subscribers | Consistency |
| --- | --- | --- | --- | --- | --- |
| `StudentDeleted` | `student` (`StudentService`, after `Student` aggregate is deleted) | UC-3 | `studentId` | `book` (clear ownership), `enrollment` (delete enrollments), `identity` (delete user account) | Eventual, in-process — published after the deleting transaction commits; the HTTP response does not wait for listeners (`03-sequence-diagrams.md` §2.3) |
| `CourseDeleted` | `course` (`CourseService`, after `Course` aggregate is deleted) | UC-10 | `courseCode` | `enrollment` (delete enrollments) | Same as above |

Two design choices worth stating explicitly in tactical terms:

- **Why these are events and not synchronous calls:** each listener modifies an aggregate it doesn't own from the publisher's point of view (`book` clearing its own `Book.ownerId`, `enrollment` deleting its own `Enrollment` rows) — a domain event is the standard DDD answer to "aggregate A's change has a side effect on aggregate B, but A shouldn't need to know B's internals or hold a lock on it." Spring Modulith's Event Publication Registry gives this an at-least-once delivery guarantee even across a process restart, which is what makes "the HTTP response doesn't wait" (§2.3/§4.3) a safe design rather than a lossy one.
- **Why account provisioning is *not* a domain event.** UC-1's tail (`AccountProvisioning.provisionForStudent`, `04-authentication-authorization.md` §3) runs synchronously, in the same transaction as the `Student` save — the opposite consistency model from the two events above. This is deliberate: Identity.1 requires a `User` to exist "never as a separate manual step," so a `Student` row must never successfully commit without its `User` — eventual consistency would create a window where a registered student has no way to log in. Where the invariant demands atomicity, the design uses a synchronous published-interface call (§10); where it only demands eventual cleanup, it uses an event. The same aggregate-boundary reasoning, applied to two different consistency requirements, produces two different tactical mechanisms.

## 10. Modules as the Tactical "Module" Pattern

Evans lists **Modules** as a tactical pattern in its own right — a way of organizing model elements that itself carries meaning ("things in the same module are conceptually related"). Spring Modulith's five modules already *are* this pattern, enforced at build time (`ApplicationModules.verify()`) rather than left as a naming convention. The one place tactical design touches strategic design is the published-interface boundary — `StudentLookup`, `CourseLookup`, `BookLookup`, `EnrollmentLookup`, `AccountProvisioning` — which is DDD's **Open Host Service with Published Language**: a small, deliberately stable read (or provisioning) API one module exposes so others can integrate without reaching into its aggregate or repository. `02-component-diagram.md` §2.5 already derives this exact pattern from first principles ("`book` doesn't want to own persistence for students, it wants to ask a question of the `student` module"); this document adds only the name.

## 11. Design Rules Enforced / Anti-Patterns Avoided

A short checklist, each line traceable to a decision already made elsewhere in the doc set:

- **No aggregate holds a reference to another aggregate's object** — only its id (§3; `02-component-diagram.md` §2.5).
- **No repository exists for a non-root entity** — moot here since every aggregate is single-entity (§3, §7), but worth stating so a future aggregate that *does* grow child entities doesn't get one by accident.
- **The domain layer is framework-free** — `Student`, `Course`, `Book`, `Enrollment`, `User` import no Spring/JDBC/HTTP types (`02-component-diagram.md` §3), which is what makes every invariant in §8's factory-method column unit-testable without a database.
- **Application Services carry no business rules** — every branch in `03-sequence-diagrams.md` that decides pass/fail delegates to either the aggregate or a repository/published-interface boolean (§6).
- **Cross-aggregate consistency is either fully synchronous-in-one-transaction (account provisioning) or fully eventual-via-event (delete cascades) — never a partial, ad hoc mix** (§9).

## 12. Traceability Matrix — req.md Rule → Tactical Construct

| req.md rule | Tactical construct | Class / method |
| --- | --- | --- |
| Student.1 (unique code) | Repository check + factory | `StudentService.register` → `StudentRepository.existsByCode` → `Student.register(...)` |
| Student.2 (unique, valid email) | Repository check + Value Object | `StudentRepository.existsByEmail`; format validity in `Email` VO |
| Student.3 (mandatory names) | Aggregate factory invariant | `Student.register(...)` |
| Student.4 (valid DOB) | Value Object | `DateOfBirth` |
| Student.5 (must exist before book/course association) | Published interface | `StudentLookup.idOf(...)`, called from `BookService`/`EnrollmentService` — an empty result *is* "does not exist" |
| Book.1 (unique ISBN) | Repository check | `BookRepository.existsByIsbn` |
| Book.2/3 (at most one, optional owner) | Aggregate behavior + typed field | `Book.changeOwner(...)`, `Book.clearOwner()`; `ownerId: StudentId?` |
| Book.4 (owner must exist) | Published interface | `StudentLookup.idOf(...)`, called from `BookService` |
| Book.5 (unassign ≠ delete) | Aggregate behavior | `Book.clearOwner()` distinct from `BookRepository.deleteByIsbn` |
| Course.1 (unique code) | Repository check | `CourseRepository.existsByCode` |
| Course.2 (mandatory name) | Aggregate factory invariant | `Course.create(...)` |
| Course.3 (positive credits) | Value Object | `Credits` |
| Enrollment.1 (no duplicate) | Repository check | `EnrollmentRepository.existsByStudentAndCourse` |
| Enrollment.2 (course must exist) | Published interface | `CourseLookup.existsByCode(...)` |
| Enrollment.3 (student must exist) | Published interface | `StudentLookup.idOf(...)` |
| Enrollment.4 (end removes only the link) | Repository scope | `EnrollmentRepository.deleteByStudentAndCourse` never touches `students`/`courses` tables |
| Identity.1 (auto-created, never manual) | Synchronous published interface, same transaction | `StudentService.register` → `AccountProvisioning.provisionForStudent(...)` |
| Identity.2 (unique username) | Repository check | `UserRepository.existsByUsername` (never violated for Student accounts since `Email` is already Student.2-unique) |
| Identity.3 (must-change-password at creation) | Aggregate factory invariant | `User.provisionForStudent(...)` sets `mustChangePassword = true` |
| Identity.4 (changed password never recoverable again) | Aggregate behavior | `User.changePassword(...)` clears `initialPasswordEncrypted` |
| Identity.5 (registrar can view only until changed) | Application service query gated by aggregate state | `IdentityService.viewInitialPassword` branches on `mustChangePassword` |
| Student deleted → cascade cleanup | Domain Event | `StudentDeleted` → `BookService`, `EnrollmentService`, `IdentityService` listeners |
| Course deleted → cascade cleanup | Domain Event | `CourseDeleted` → `EnrollmentService` listener |

**A session is not modelled as a tactical construct, and `Identity.8` therefore has no row above.** UC-27/UC-28 act on HTTP sessions, which live in the servlet container and are read through Spring Security's `SessionRegistry` — there is no aggregate, no repository port, and no table (`04-authentication-authorization.md` §3c). Adding a `Session` entity would mean modelling something this application does not own the lifecycle of, and keeping it consistent with the container's real state. The rule is enforced instead in the application service that reads that registry (`06-low-level-design.md` §8.8), which is the honest placement for an invariant about infrastructure rather than about the domain.

## 13. Out of Scope

- **Strategic DDD / context mapping** beyond what §10 needs to justify the module boundaries `02-component-diagram.md` already fixed — no Anti-Corruption Layer, Conformist, or Shared Kernel analysis, since there is exactly one codebase and one schema (`01-system-overview.md` §4.4).
- **CQRS, event sourcing, sagas/process managers** — not used anywhere in this design; the two Domain Events in §9 are plain eventual-cleanup notifications, not an event-sourced write model.
- **Aggregate versioning / optimistic locking strategy** — an implementation concern, not fixed at this level.
- **Class-level Java signatures, package layout beyond what `02-component-diagram.md` §3 already shows, or unit-test design** — this document names patterns and maps them to existing class names; it does not write the code.
- **The `Credential` value-object refinement in §4** is offered as an option, not adopted as a decision — implementers may keep `passwordHash`/`initialPasswordEncrypted`/`mustChangePassword` as three separate fields on `User` without contradicting anything else in this document.
