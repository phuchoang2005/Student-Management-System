# Test Data Preparation

Testing Documentation — Part 4 of 4 ([Test Strategy](./01-test-strategy.md) → [Test Plan](./02-test-plan.md) → [Test Cases](./03-test-cases/) → Test Data Preparation).

Defines the test data referenced by ID throughout [03-test-cases/](./03-test-cases/) (e.g. `student-valid-01`, `course-boundary-credits-1`). Grounded in the column types/constraints in [05-database-schema.md](../SA-docs/05-database-schema.md) and [06-low-level-design.md](../SA-docs/06-low-level-design.md) §9's DDL.

---

## 1. Seed / Reference Data

### 1.1 System Administrator account

`04-authentication-authorization.md` §2.2/§3a states the System Administrator account is the one identity that is **always pre-seeded out-of-band** — no use case creates it. Test environments need exactly one, since it's the only caller that can exercise UC-24/UC-25:

| ID | Username | Role | Initial state |
| --- | --- | --- | --- |
| `staff-sysadmin-01` | `sysadmin.test@example.test` | SYSTEM_ADMINISTRATOR | Password already set, for use as the default authorized caller across UC-24/UC-25 test cases |

### 1.2 Staff accounts

Registrar/Librarian/Course Administrator accounts now have a defined in-app provisioning flow (UC-24), and TC-IDN-024–030 exercise that flow directly. Most other write test cases don't need to exercise provisioning itself, though, so this document still names a pre-seeded baseline set for speed and determinism — treat these as "created via UC-24 once, at environment setup" rather than "pre-seeded because no flow exists":

| ID | Username | Role | Initial state |
| --- | --- | --- | --- |
| `staff-registrar-01` | `registrar.test@example.test` | REGISTRAR | Password already set (not in must-change-password state), enabled — the default authorized caller across most write test cases |
| `staff-librarian-01` | `librarian.test@example.test` | LIBRARIAN | Password already set, enabled |
| `staff-course-admin-01` | `course-admin.test@example.test` | COURSE_ADMINISTRATOR | Password already set, enabled |
| `staff-registrar-02` | `registrar2.test@example.test` | REGISTRAR | Password already set, enabled — a second Registrar for concurrency test cases (e.g. TC-XC-015) needing two independent authenticated sessions |
| `staff-disabled-01` | `disabled-librarian.test@example.test` | LIBRARIAN | Password already set, **`enabled = false`** — used by TC-IDN-030 (login rejected: disabled account) |

**Seeding mechanism (recommendation for implementation):** a Flyway `R__` repeatable migration or a `@Sql`/Testcontainers init script scoped to test profiles only — never bundled into the production migration set — inserting `staff-sysadmin-01` and these 5 rows with a fixed, known password (e.g. hashed `TestPass123!`) so integration tests can log in deterministically.

### 1.3 Demo accounts (development/testing convenience, `GET /auth/demo-accounts`)

The 5 fixed identities `04-authentication-authorization.md` §8 specifies, one per actor — distinct from the fixtures in §1.1/§1.2 above (those exist for automated test cases; these exist for a human developer to click through the frontend). Seeded by the same dev/test-only mechanism, gated by the same `app.demo-accounts.enabled` flag as the endpoint itself (`06-low-level-design.md` §11.4) — **never present in a `prod`-migrated database**, which TC-IDN-032 verifies directly.

| Username | Role | Password |
| --- | --- | --- |
| `demo.sysadmin` | SYSTEM_ADMINISTRATOR | `Demo#12345` |
| `demo.registrar` | REGISTRAR | `Demo#12345` |
| `demo.librarian` | LIBRARIAN | `Demo#12345` |
| `demo.courseadmin` | COURSE_ADMINISTRATOR | `Demo#12345` |
| `demo.student` | STUDENT | `Demo#12345` |

### 1.4 Student-linked accounts

Every `student-*` fixture in §2 automatically implies a corresponding `users` row per Identity.1 (auto-provisioned at creation) — these are not separately seeded; they're a side effect of creating the student fixture through the real `POST /students` flow, which is itself the mechanism under test for most student-related cases.

---

## 2. Baseline Valid Fixtures

One representative, fully-valid record per aggregate, used as the default "happy path" input across test cases.

| ID | Fields |
| --- | --- |
| `student-valid-01` | code `STU-0001`, first `Ada`, last `Lovelace`, email `ada.lovelace@example.test`, DOB `1990-01-01` |
| `student-valid-02` | code `STU-0002`, first `Alan`, last `Turing`, email `alan.turing@example.test`, DOB `1991-02-02` |
| `student-valid-03` | code `STU-0003`, first `Grace`, last `Hopper`, email `grace.hopper@example.test`, DOB `1992-03-03` — used where a student with zero associations is needed (e.g. plain removal cases) |
| `book-valid-01` | ISBN `978-0-13-468599-1`, title `Clean Architecture`, author `Robert C. Martin`, published `2017-09-20`, no owner |
| `book-valid-02` | ISBN `978-1-4919-5035-7`, title `Designing Data-Intensive Applications`, author `Martin Kleppmann`, published `2017-03-16`, no owner |
| `course-valid-01` | code `CS-101`, name `Introduction to Computer Science`, description non-blank, credits `3` |
| `course-valid-02` | code `CS-201`, name `Data Structures`, description non-blank, credits `4` — used where a course with no enrollments is needed |

### 2.1 Composite/relationship fixtures (built from the baseline fixtures above)

| ID | Composition | Used by |
| --- | --- | --- |
| `student-with-books-01` | `student-valid-01`, owning `book-valid-01` and `book-valid-02` | [student.md](./03-test-cases/student.md) TC-STU-022 |
| `student-with-enrollments-01` | `student-valid-01`, enrolled in `course-valid-01` and `course-valid-02` | [student.md](./03-test-cases/student.md) TC-STU-023 |
| `student-full-cascade-01` | A student owning ≥1 book, holding ≥1 enrollment, and with an active login account — the union of the two fixtures above | [student.md](./03-test-cases/student.md) TC-STU-025; [cross-cutting.md](./03-test-cases/cross-cutting.md) TC-XC-020 |
| `book-owned-by-student-01` | `book-valid-01` (or a copy), owner = `student-valid-01` | [book.md](./03-test-cases/book.md) TC-BOOK-007, TC-BOOK-010, TC-BOOK-013 |
| `course-with-enrollments-01` | `course-valid-01`, enrolled: `student-valid-01`, `student-valid-02` | [course.md](./03-test-cases/course.md) TC-CRS-015, TC-CRS-020; [cross-cutting.md](./03-test-cases/cross-cutting.md) TC-XC-022 |
| `student-search-set-01` | 3 students sharing a last-name substring (e.g. `Anders-`, `Anderson`, `Andersen`) | [student.md](./03-test-cases/student.md) TC-STU-028 |
| `book-search-set-01` | ≥3 books, mixed owners, sharing a title/author term | [book.md](./03-test-cases/book.md) TC-BOOK-016 |
| `course-search-set-01` | ≥3 courses sharing a name term | [course.md](./03-test-cases/course.md) TC-CRS-018 |

---

## 3. Boundary-Value Datasets

Derived directly from `05-database-schema.md` §3 and `04-authentication-authorization.md` §5.2.

| ID | Value | Column / rule | Expected outcome |
| --- | --- | --- | --- |
| `student-boundary-code-20chars` | 20-character `studentCode` | `students.student_code VARCHAR(20)` | Accepted (TC-STU-011) |
| `student-boundary-code-21chars` | 21-character `studentCode` | Same column | Rejected (TC-STU-012) |
| `course-boundary-code-20chars` | 20-character `courseCode` | `courses.course_code VARCHAR(20)` | Accepted (TC-CRS-007) |
| `book-boundary-isbn-20chars` | 20-character `isbn` (ISBN-13 with hyphens) | `books.isbn VARCHAR(20)` | Accepted (TC-BOOK-005) |
| `course-invalid-credits-zero` | `credits = 0` | `courses.credits CHECK (credits > 0)` | Rejected (TC-CRS-004) |
| `course-invalid-credits-negative` | `credits = -1` | Same constraint | Rejected (TC-CRS-005) |
| `course-boundary-credits-1` | `credits = 1` | Same constraint, minimum valid value | Accepted (TC-CRS-006) |
| `student-invalid-email-01` | `"not-an-email"` | `Email` VO format validation (Student.2) | Rejected, `400` (TC-STU-004) |
| `student-invalid-dob-01` | `"2023-02-30"` (non-existent calendar date) | `DateOfBirth` VO validation (Student.4) | Rejected, `400` (TC-STU-007) |
| `password-boundary-7chars` | 7-character password | Password policy minimum (8) | Rejected (TC-IDN-009) |
| `password-boundary-8chars` | 8-character password | Password policy minimum | Accepted (TC-IDN-010) |
| `password-boundary-72chars` | 72-character password | Password policy maximum (BCrypt 72-byte truncation limit) | Accepted (TC-IDN-011) |
| `password-boundary-73chars` | 73-character password | Same limit | Rejected (TC-IDN-012) |

### 3.1 Additional boundary values to prepare during implementation (not yet named as fixtures, flagged for completeness)

- `email` at the `VARCHAR(255)` boundary (255 and 256 characters) — mirrors the `student_code`/`isbn` pattern above; not enumerated as a named fixture here since `req.md` doesn't call out an email-length business rule, but the column constraint exists and should get the same boundary treatment once implementation starts.
- `first_name`/`last_name`/`title`/`author` at their respective `VARCHAR(100)`/`VARCHAR(255)` boundaries — same reasoning; lower priority (P2) since no business rule names an exact limit, only the schema does.

---

## 4. Negative / Duplicate Datasets

Used to trigger the uniqueness-constraint (`409`) test cases. Each pairs a pre-seeded baseline fixture with a second payload that collides on exactly one field, so the test isolates which constraint fired.

| ID | Collides with | Field | Used by |
| --- | --- | --- | --- |
| `student-dup-code` | `student-valid-01` | `studentCode` (same code, different email) | TC-STU-002 |
| `student-dup-email` | `student-valid-01` | `email` (same email, different code) | TC-STU-003 |
| `book-dup-isbn` | `book-valid-01` | `isbn` | TC-BOOK-003 |
| `course-dup-code` | `course-valid-01` | `courseCode` | TC-CRS-002 |
| `enrollment-dup-pair` | An existing active enrollment | `(studentCode, courseCode)` pair | TC-ENR-002 |

---

## 5. Data Preparation Approach

### 5.1 Fixture construction

Recommend **test-data builder classes**, one per aggregate (`StudentTestDataBuilder`, `BookTestDataBuilder`, etc.), living under `src/test/java/.../testsupport/`, each defaulting to a valid baseline (matching §2's fixtures) with fluent overrides for the specific field a test case needs to vary (e.g. `.withCredits(0)`, `.withEmail("not-an-email")`). This keeps unit and component tests readable and keeps the boundary/negative fixtures in §3–4 expressible as one-line deviations from a known-good baseline, rather than hand-built payloads repeated across test files.

### 5.2 Integration/API-level seeding

For tests that need data already persisted before the test runs (most `03-test-cases/` cases with non-trivial preconditions), use either:
- `@Sql` scripts scoped per test class/method (Spring's standard mechanism, already available via the `spring-boot-starter-data-jdbc-test` dependency), or
- Programmatic setup through the real service layer in a `@BeforeEach`, which has the advantage of also exercising the "create" path as an implicit smoke test on every run.

Prefer the programmatic approach for aggregates with non-trivial creation side effects (`student`, since it auto-provisions an account) so the fixture creation itself stays honest about what the real flow does; prefer `@Sql` for simple, side-effect-free aggregates (`course`) where speed matters more.

### 5.3 Data isolation & reset strategy

| Test level | Isolation mechanism |
| --- | --- |
| Unit (domain/VO) | No persistence involved — no reset needed |
| Component/slice (`@DataJdbcTest`) | Spring's test-transaction rollback (`@Transactional` test default) — each test method's changes roll back automatically |
| API/contract (full `@SpringBootTest`) | Fresh Testcontainers MySQL instance per test class (or per test run, reused across classes for speed, migrated once) with a truncate-and-reseed step in `@BeforeEach`/`@AfterEach` — full `@Transactional` rollback doesn't cleanly cover HTTP-driven tests that may span multiple internal transactions (e.g. the cascade scenarios in [cross-cutting.md](./03-test-cases/cross-cutting.md) §4) |
| Database integrity (direct SQL against constraints) | Same Testcontainers instance as API/contract level, truncate-and-reseed between test methods to avoid unique-constraint collisions across unrelated tests (`student_code`, `email`, `isbn`, `course_code`, `username` all carry `UNIQUE` constraints — see `05-database-schema.md` §4) |

**Why truncate-and-reseed rather than a fresh container per test:** spinning up a new MySQL container per test method is correct but slow; truncating all 5 tables (in FK-safe order: `enrollments`, `users`, `books`, `courses`, `students`) between tests within one container gives the same isolation guarantee at a fraction of the cost, and is safe here because every test fixture ID in this document is deterministic and self-contained (no fixture depends on state left over from a previous, unrelated test).

### 5.4 Realistic bulk data (for search/list test cases)

For cases needing more than a handful of records (e.g. `student-search-set-01`), hand-authored fixture names are sufficient — this domain doesn't need large-volume synthetic generation (no performance/load testing is in scope, per [01-test-strategy.md](./01-test-strategy.md) §1.3). If bulk data is ever needed, a Java Faker-style library can generate additional filler rows around the named fixtures, but this is not required for the current test case set.

---

## 6. Environment-Specific Notes

| Environment | Data source |
| --- | --- |
| Local development | `docker-compose.yml` MySQL, manually seeded via the builder classes/`@Sql` scripts run by the developer, or by running the integration test suite itself |
| CI | Ephemeral Testcontainers MySQL, migrated fresh and seeded fresh on every run — no persistent state carries between CI runs |
| Staging (future) | Not yet provisioned; when it exists, seed data must be clearly separated from any real data — this environment should never contain real student PII (see §7) |

---

## 7. Synthetic Data / PII Note

All test data defined in this document — names, emails, dates of birth, ISBNs, course names — is **fabricated for testing purposes** (`@example.test` email domain, a reserved, non-routable TLD suffix intentionally used here instead of a real domain). Even though this is a portfolio project with no real student population, this convention should hold for every environment, including any future staging environment: **no real personal data is ever used as test data.** This also makes it safe to commit fixture definitions (as opposed to seeded database dumps, which are not committed — see `docs/.gitignore`) directly into the test source tree.
