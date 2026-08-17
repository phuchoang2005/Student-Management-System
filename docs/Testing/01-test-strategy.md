# Test Strategy

Testing Documentation — Part 1 of 4 (Test Strategy → [Test Plan](./02-test-plan.md) → [Test Cases](./03-test-cases/) → [Test Data Preparation](./04-test-data-preparation.md)).

Derived from [req.md](../BA-docs/req.md), [use-cases.md](../BA-docs/use-cases.md), [user-stories.md](../BA-docs/user-stories.md), and the full `SA-docs` set (`01-system-overview.md` → `06-low-level-design.md`, `tactical-ddd-design.md`, `api-specification.md`). This document answers _what kind of testing this system needs and why_ — not the concrete schedule, suite layout, or individual test cases, which are the subject of [02-test-plan.md](./02-test-plan.md) and [03-test-cases/](./03-test-cases/).

Written ahead of implementation: `management/` currently has only a Spring Boot skeleton (`ManagementApplication`, one throwaway `DemoController`, a default `contextLoads()` test) — no domain code, no Flyway migrations. This strategy targets the system **as specified**, so that test design is ready the moment each module is built, rather than retrofitted afterward.

---

## 1. Purpose & Scope

### 1.1 Purpose

Define the testing approach for the Student Management System — a single-deployable Spring Boot REST API (modular monolith, 5 Spring Modulith modules: `student`, `book`, `course`, `enrollment`, `identity`, plus cross-cutting `shared`) backed by one MySQL 8 schema. The goal is to verify that the implementation, once written, honors every business rule in `req.md`, every flow in `use-cases.md`, and every contract fixed in `api-specification.md` / `06-low-level-design.md`.

### 1.2 In Scope

- All 23 use cases (UC-1–UC-23) — main flow and every alternate/exception flow.
- All business rule invariants in `req.md` §4 (Student.1–4, Book.1–5, Course.1–3, Enrollment.1–4, Identity.1–5) and the lifecycle rules in §5 (cascading effects of removing a student, book, or course).
- Role-based access control (RBAC) across the 4 roles (Registrar, Librarian, Course Administrator, Student), including the "own records only" scoping applied to the Student role.
- Session-based authentication, the must-change-password gate, and the password policy.
- The error-response contract (`Error` / `ValidationError` envelope, and the exact HTTP status per exception type in `06-low-level-design.md` §3).
- Database-level integrity: uniqueness constraints, foreign keys, `CHECK` constraints, and cascade (`ON DELETE`) behavior.
- Optimistic locking (`StaleWriteException` / 409) on the four versioned aggregates (`Student`, `Course`, `Book`, `User`).
- Spring Modulith module boundary enforcement (`ApplicationModules.verify()`).
- The 7 explicit ambiguity resolutions documented in `api-specification.md` §5 — each is a deliberate design decision and therefore a named test case, not an assumption.

### 1.3 Out of Scope

| Item | Reason |
| --- | --- |
| Load / stress / scalability testing beyond a basic smoke check | `01-system-overview.md` §5 fixes a single-process, no-clustering deployment with no stated throughput target; a full performance test program is not justified until real usage patterns emerge (`api-specification.md` §6 makes the same call for pagination). |
| UI / frontend testing | The system is backend-only (`01-system-overview.md` §1) — no bundled frontend exists to test. |
| Formal penetration testing / security audit | Basic authorization and session-handling coverage is included (§2.4 below); a dedicated audit is a separate, specialist engagement. |
| SSO / OAuth / MFA / rate limiting / forgot-password flow testing | All explicitly out of scope of the design itself (`04-authentication-authorization.md` §7, `api-specification.md` §6) — nothing exists to test. |
| Internationalization / accessibility | Not addressed anywhere in the BA/SA doc set; no requirement exists to verify. |
| Horizontal scaling / distributed session behavior | Ruled out by the single-process topology (`01-system-overview.md` §5). |

---

## 2. Test Levels & Types

| Level | Purpose | Target classes / layers | Suggested tooling |
| --- | --- | --- | --- |
| **Unit** | Verify domain invariants in isolation — Value Object constructors (`StudentCode`, `Email`, `Credits`, `Isbn`, …) and aggregate behavior methods (`Student.applyChanges`, `Book.changeOwner`, `User.changePassword`, …) throw the right exception for the right rule. | `domain/` packages, no Spring context | JUnit 5, AssertJ, Mockito (for Application Service tests that mock repository/lookup ports) |
| **Component / slice integration** | Verify one module's layers wire together correctly against a real database. | `internal/` repository adapters, `web/` controllers | `@DataJdbcTest` (repository layer, Testcontainers-backed), `@WebMvcTest` (controller layer, mocked service) |
| **Module boundary** | Enforce that no module imports another's `internal/` package, and that the 5-module structure matches `02-component-diagram.md`. | Whole `management` module tree | Spring Modulith `ApplicationModules.verify()` (already a dependency: `spring-modulith-starter-test`) |
| **Architecture / layering conformance** | Enforce the *intra*-module rules `06-low-level-design.md` §2.1–2.2 fixes but `ApplicationModules.verify()` doesn't check: dependency direction within a module (`web` → `application` → `domain`; `internal/` depends inward only and is never depended upon outside itself), domain-layer framework-freedom, and the naming/shape conventions (exceptions unchecked under `shared.exception`, Application Services carry no `Impl` suffix, repository ports stay interfaces, Spring Data types never leak outside `internal/`). | Whole `management` module tree, class-level | **ArchUnit** (`com.tngtech.archunit:archunit-junit5`) — runs as a plain JUnit 5 test with no Spring context and no database, so it can be written and run from the very first class added to the project, unblocked by the missing Flyway migration (see [02-test-plan.md](./02-test-plan.md) §5) |
| **API / contract (full integration)** | Exercise a real HTTP request through Spring Security, the controller, application service, domain, and a real MySQL instance — the closest thing to "does the built system do what `use-cases.md` says." | Full `@SpringBootTest(webEnvironment = RANDOM_PORT)` | MockMvc or RestAssured, against a Testcontainers MySQL instance (see §3) |
| **Security / authorization** | Verify the RBAC matrix (`06-low-level-design.md` §11.1), session behavior, CSRF-disabled posture, and the must-change-password gate. | Spring Security filter chain, end-to-end | `spring-security-test` (`.with(user(...))`, `.with(httpBasic(...))` as applicable), full `@SpringBootTest` for session-cookie flows |
| **Database integrity** | Confirm schema-level constraints (`UNIQUE`, `FOREIGN KEY`, `CHECK`) actually reject what the application layer is also expected to reject, and that `ON DELETE` cascade/set-null behavior matches `05-database-schema.md` §5. | Flyway-migrated MySQL schema | Testcontainers MySQL + raw JDBC or `@DataJdbcTest`, run once the Flyway migration (`06-low-level-design.md` §9) exists |
| **Regression** | Re-run the full suite on every change so previously-verified behavior doesn't silently break. | Everything above | CI pipeline (GitHub Actions recommended — no CI config exists yet) on every PR |

**Tooling note:** `pom.xml` already includes `spring-boot-starter-data-jdbc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-webmvc-test`, and `spring-modulith-starter-test` as test-scope dependencies — the levels above are designed to use exactly what's already declared. Two additions this strategy recommends for the build phase: **Testcontainers** (`org.testcontainers:mysql`, `org.testcontainers:junit-jupiter`) as the standard substrate for any test that needs a real MySQL instance, so integration tests run against the same engine (MySQL 8, `CHECK` constraints, `ON DELETE` behavior) as production rather than an in-memory substitute that wouldn't honor the same constraint semantics; and **ArchUnit** (`com.tngtech.archunit:archunit-junit5`) to keep the hexagonal layering and module-shape conventions `06-low-level-design.md` §2 fixes on paper actually enforced in code, rather than relying on code review alone to catch a stray `internal/` import or a leaked `CrudRepository`. Both are recommendations for whoever picks up implementation — no `pom.xml` change is made by this documentation task.

### 2.1 Why not more (or less)

- **No contract-testing framework (Pact, Spring Cloud Contract):** there is exactly one consumer type (any HTTP client) and one producer (this API) — no second service to keep a contract in sync with. The OpenAPI document itself, validated against real responses in the API/contract test level, is sufficient.
- **No dedicated mutation-testing tooling mandated:** valuable, but not proportionate to a 5-aggregate, single-team project at this stage; can be revisited once the initial suite exists.
- **No end-to-end browser testing:** no frontend exists (§1.3).

### 2.2 Boundary between unit and integration coverage

Rule of thumb used throughout §3 of [03-test-cases/](./03-test-cases/): if a rule is enforced by a Value Object or aggregate method with no I/O (e.g., "credits must be positive"), it gets a **unit** test first. If a rule can only be verified against persisted state (e.g., "student code must be unique across all students," which needs a second row to already exist), it is verified at the **API/contract** level, where the full stack — including the database unique constraint as a backstop — is exercised together.

### 2.3 Cascade / lifecycle testing

`req.md` §5's rules (student removal unassigns books and removes enrollments and the user account; course removal removes enrollments) are implemented, per `05-database-schema.md` §5, through **both** a Spring Modulith domain event path (`StudentDeleted`, `CourseDeleted`, consumed synchronously in-process by dependent modules) **and** a database-level `ON DELETE` safety net. Both layers are tested independently:
- The event-driven path is verified at the API/contract level (delete a student via the real endpoint, then query the book/enrollment/identity endpoints and confirm the expected side effects).
- The DB-level constraint is verified at the database integrity level by exercising the cascade directly against the schema (bypassing the application layer), confirming the safety net holds even if an event handler were ever skipped.

### 2.4 Security testing scope

Covers: the full role × endpoint matrix (`06-low-level-design.md` §11.1), unauthenticated access rejection, the must-change-password gate, session cookie issuance/invalidation, and password-policy enforcement (§5.2 of `04-authentication-authorization.md`). Does not cover: penetration testing, dependency/CVE scanning, or infrastructure hardening — those are operational concerns outside this application-level test scope.

---

## 3. Test Environments

| Environment | Purpose | Database | Notes |
| --- | --- | --- | --- |
| **Local development** | Developer-run unit/integration tests while building a module | `docker-compose.yml`'s MySQL 8.4 container | `make up` / `make down` per the root `Makefile` — **see §6 note below**, the `Makefile` currently references Postgres and is inconsistent with `docker-compose.yml`'s MySQL service; resolve before relying on it for test environment setup. |
| **CI (per pull request)** | Automated regression run on every change | Ephemeral Testcontainers MySQL, one instance per test class/run | No CI pipeline is configured yet (no `.github/workflows/` present); this strategy assumes GitHub Actions, matching the GitHub-hosted repository, but this is an assumption to confirm with the project owner, not a fixed decision. |
| **Staging / pre-prod** | Manual/exploratory verification against a near-production setup before release | Dedicated MySQL instance, Flyway-migrated | Not yet provisioned — out of scope until the project reaches a deployable state. |

**Environment parity principle:** every level above MySQL-in-memory-fake uses real MySQL 8 (via Testcontainers or docker-compose), because this schema relies on engine-specific behavior (`CHECK` constraints, `ENUM`, `ON DELETE SET NULL` vs. `CASCADE`) that an in-memory or different-engine substitute would not faithfully reproduce.

---

## 4. Tools & Frameworks

| Concern | Tool | Status |
| --- | --- | --- |
| Test runner / assertions | JUnit 5, AssertJ | Implied by Spring Boot Test starters already in `pom.xml` |
| Mocking | Mockito | Implied by Spring Boot Test starters |
| Web layer slice testing | `spring-boot-starter-webmvc-test` (MockMvc) | Already in `pom.xml` |
| Data layer slice testing | `spring-boot-starter-data-jdbc-test` | Already in `pom.xml` |
| Security testing | `spring-boot-starter-security-test` | Already in `pom.xml` |
| Module boundary verification | `spring-modulith-starter-test` (`ApplicationModules.verify()`) | Already in `pom.xml` |
| Architecture / layering conformance | ArchUnit (`archunit-junit5`) | **Recommended addition** — not yet in `pom.xml` |
| Real-database integration testing | Testcontainers (`mysql`, `junit-jupiter`) | **Recommended addition** — not yet in `pom.xml` |
| API/contract validation against OpenAPI | Any OpenAPI-aware response validator (e.g. `atlassian-oai-validator`, or a lightweight assertion against `docs/SA-docs/openapi/openapi.yaml`) | **Recommended addition**, optional — can start as manual cross-checks against the spec, formalize later |
| Coverage reporting | JaCoCo Maven plugin | **Recommended addition** — not yet in `pom.xml` |
| CI orchestration | GitHub Actions | **Recommended, unconfirmed** — no pipeline exists yet |

None of these additions are made by this documentation task; they are strategy recommendations for whoever picks up implementation.

---

## 5. Entry & Exit Criteria

| Test level | Entry criteria | Exit criteria |
| --- | --- | --- |
| Unit | Domain class/VO compiles | All rule-mapped test cases pass; no `DomainValidationException` path left unexercised |
| Component/slice | Module's `internal/`/`web/` classes exist; Flyway migration for its table(s) exists | Repository CRUD + constraint-violation paths pass; controller returns correct status/DTO shape for each documented branch |
| Module boundary | All 5 modules + `shared` exist as packages | `ApplicationModules.verify()` passes with zero violations |
| Architecture conformance | Any module package exists (no database or Spring context required) | Every rule in [03-test-cases/cross-cutting.md](./03-test-cases/cross-cutting.md) §7 passes with zero violations |
| API/contract | Full application context starts against a real MySQL (migrated) | Every UC's main flow and every alternate/exception flow in `03-test-cases/` passes; response shapes match `api-specification.md` |
| Security | Spring Security config (`06-low-level-design.md` §11) implemented | Full RBAC matrix passes (§2.4); must-change-password gate verified; unauthenticated requests rejected everywhere except `POST /auth/login` |
| Database integrity | Flyway migration applied to a clean schema | Every `UNIQUE`/`FOREIGN KEY`/`CHECK` constraint in `05-database-schema.md` rejects the value it's meant to reject, independent of the application layer |
| Regression (CI) | A pull request is opened against `main` | All of the above levels pass; no previously-green test goes red |

---

## 6. Risk-Based Test Prioritization

| Priority | Category | Examples |
| --- | --- | --- |
| **P0** — must pass before any release | Uniqueness constraints (student code, email, ISBN, course code, username); cascade/lifecycle correctness on delete; authentication and RBAC; must-change-password gate; optimistic locking (`StaleWriteException`) | UC-1 duplicate student code, UC-3 full cascade, UC-21/UC-22 auth flows, any role attempting an out-of-scope write |
| **P1** — required for feature completeness | Field-level validation on create/update (blank name, invalid DOB, non-positive credits, malformed email) | UC-2, UC-8, UC-9 alternate flows |
| **P2** — required for correctness but lower blast radius | Read/search/detail views, empty-result handling, not-found-after-removal races | UC-13–20 |

This ordering also fixes the recommended build/test sequence in [02-test-plan.md](./02-test-plan.md) §2: modules and rules with P0 risk (student identity, cascades, auth) are built and tested first because everything else in the domain depends on a student existing and an authenticated, correctly-scoped caller.

---

## 7. Defect Management

No issue tracker is referenced anywhere in the existing documentation set. This strategy proposes **GitHub Issues** on this repository, using the following severity/priority scheme until a different tool is adopted:

| Severity | Definition | Example |
| --- | --- | --- |
| Critical | Data integrity violated, or a security/authorization boundary bypassed | A Student can enroll another student in a course; a duplicate student code is accepted |
| Major | A documented use case flow produces the wrong result or wrong HTTP status | UC-11 duplicate enrollment returns 500 instead of 409 |
| Minor | Cosmetic or non-blocking deviation (e.g., error message wording) | `ValidationError.errors` array missing a field name |
| Enhancement | Test gap or tooling improvement, not a product defect | Add a Testcontainers-based suite for the `enrollment` module |

Every defect ticket should reference the failing Test Case ID (`TC-<MODULE>-<NNN>`, see [03-test-cases/](./03-test-cases/)) and the `req.md` rule or UC it traces back to.

---

## 8. Traceability Approach

Every test case in [03-test-cases/](./03-test-cases/) carries a **Related UC / Rule** field pointing back to a specific `use-cases.md` UC ID and/or `req.md` rule ID (e.g., `Student.2`, `Enrollment.1`). Each test-case file closes with a traceability table mapping UC/US IDs to the Test Case IDs that cover them. [README.md](./README.md) holds the top-level index of which file covers which UC range. This mirrors the traceability convention `user-stories.md` already uses (linking each story back to `req.md` rules) — the test documentation extends the same chain one link further: `req.md` rule → UC → US → TC.

---

## 9. Roles & Responsibilities

This is currently a single-owner (portfolio) project — one person is expected to author the implementation, the tests, and act as reviewer. This strategy is still written in role-neutral terms (Test Author, Implementer, Reviewer) so it scales cleanly if the project ever gains additional contributors; until then, all three roles are the same person, and the CI pipeline (§3) is what substitutes for an independent reviewer's regression check.

---

## 10. Out of Scope (this document)

- Concrete suite/package names and execution order — see [02-test-plan.md](./02-test-plan.md).
- Individual test case steps and expected results — see [03-test-cases/](./03-test-cases/).
- Concrete fixture values and seed datasets — see [04-test-data-preparation.md](./04-test-data-preparation.md).
- Actual test code — deliberately not produced by this documentation task; these four documents are the design that later drives real JUnit/integration test authoring.
