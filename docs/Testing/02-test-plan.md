# Test Plan

Testing Documentation — Part 2 of 4 ([Test Strategy](./01-test-strategy.md) → Test Plan → [Test Cases](./03-test-cases/) → [Test Data Preparation](./04-test-data-preparation.md)).

Expands [01-test-strategy.md](./01-test-strategy.md)'s levels/types into a concrete plan: what gets tested, in what order, in what environment, with what exit criteria per item. Where the strategy document answers "what kind of testing and why," this document answers "which features, in what sequence, with what's needed to run it."

---

## 1. Introduction & References

| Source document | What this plan draws from it |
| --- | --- |
| [req.md](../BA-docs/req.md) | Business entities, relationships, invariants (Student/Book/Course/Enrollment/Identity rules), lifecycle rules |
| [use-cases.md](../BA-docs/use-cases.md) | 25 use cases (UC-1–UC-25), actors, main/alternate/exception flows |
| [user-stories.md](../BA-docs/user-stories.md) | 20 user stories (US-1.1–US-7.2), acceptance criteria |
| [01-system-overview.md](../SA-docs/01-system-overview.md) | Deployment topology, actors, out-of-scope boundaries |
| [02-component-diagram.md](../SA-docs/02-component-diagram.md) | Module layout, RBAC read/write table |
| [03-sequence-diagrams.md](../SA-docs/03-sequence-diagrams.md) | Exact call ordering, `alt`/`else` branches per UC |
| [04-authentication-authorization.md](../SA-docs/04-authentication-authorization.md) | Session auth, RBAC decisions, password policy |
| [05-database-schema.md](../SA-docs/05-database-schema.md) | Table DDL, constraints, cascade behavior |
| [06-low-level-design.md](../SA-docs/06-low-level-design.md) | Exception hierarchy, HTTP status mapping, optimistic locking, Spring Security filter chain, Flyway DDL |
| [api-specification.md](../SA-docs/api-specification.md) + `openapi/` | Endpoint list, request/response schemas, 7 explicit ambiguity resolutions |

---

## 2. Test Items & Recommended Build/Test Sequence

Test items are the 5 Spring Modulith modules plus `shared`. The recommended sequence follows the dependency order already fixed by the architecture (`identity` provisioning is synchronous with student registration; `book`/`enrollment` depend on `student` and `course` existing via published lookups):

| Order | Module | Why this position | Depends on |
| --- | --- | --- | --- |
| 1 | `shared` | Security config, global exception handler, error envelope — every other module's tests depend on this existing | — |
| 2 | `student` (+ `identity` account auto-provisioning) | Everything else references a student; `identity`'s `AccountProvisioning.provisionForStudent` is called synchronously inside student registration (UC-1), so the two are tested together first | `shared` |
| 3 | `course` | Independent aggregate, no dependency on `student`/`book` | `shared` |
| 4 | `book` | Depends on `student` existing (optional ownership via `StudentLookup`) | `student` |
| 5 | `enrollment` | Depends on both `student` and `course` existing (via `StudentLookup`/`CourseLookup`) | `student`, `course` |
| 6 | `identity` (login / change password / view initial password / staff account provisioning & deactivation / demo-accounts listing, beyond auto-provisioning) | Needs a provisioned account (from step 2) to log in with; staff provisioning (UC-24/25) and the demo-accounts endpoint are additional surface on this same module, tested alongside it | `student` |
| 7 | Cross-cutting (RBAC matrix, cascade/lifecycle scenarios spanning modules, optimistic locking) | Needs all 5 modules implemented to exercise cross-module effects | 2–6 |

This ordering is a recommendation for implementation sequencing, not a hard gate — `course` and `student` (steps 2–3) can proceed in parallel since neither depends on the other.

---

## 3. Features To Be Tested / Not To Be Tested

| Module | Use cases covered | Not covered (and why) |
| --- | --- | --- |
| `student` | UC-1, UC-2, UC-3, UC-13, UC-17 | Student self-registration — out of scope; only Registrar-driven registration exists (`use-cases.md` UC-1 actor) |
| `book` | UC-4, UC-5, UC-6, UC-7, UC-14, UC-18 | Book condition/inventory tracking — no such field exists in `req.md` §2 |
| `course` | UC-8, UC-9, UC-10, UC-15, UC-19 | Prerequisites, terms/semesters, capacity limits — none are modeled in `req.md` §2 |
| `enrollment` | UC-11, UC-12, UC-20 | Grades, attendance, enrollment status beyond active/ended — not modeled; `Enrollment` has no update use case (`06-low-level-design.md` §7) |
| `identity` | UC-1 (tail), UC-21, UC-22, UC-23, UC-24, UC-25 | Staff account *self-registration* — not modeled, a staff account can only come from UC-24 (`04-authentication-authorization.md` §3a); forgot-password/MFA/SSO — out of scope (`04-authentication-authorization.md` §9); force-terminating a deactivated account's already-open session — not designed (§3b) |
| Cross-cutting | RBAC matrix, must-change-password gate, cascade/lifecycle, optimistic locking, error envelope, the 7 `api-specification.md` §5 ambiguity resolutions | Load/performance beyond a smoke check, penetration testing (see [01-test-strategy.md](./01-test-strategy.md) §1.3) |

Full per-case detail lives in [03-test-cases/](./03-test-cases/); this table is the feature-level index.

---

## 4. Test Approach Per Level

Concrete suite-naming convention (for whoever implements the test code, matching the package layout `06-low-level-design.md` §2.1 already fixes):

```
management/src/test/java/org/phuchoang/management/
├── student/
│   ├── domain/        — StudentTest, EmailTest, StudentCodeTest, DateOfBirthTest (unit)
│   ├── application/   — StudentServiceTest (unit, mocked repository/ports)
│   ├── web/            — StudentControllerTest (@WebMvcTest)
│   └── internal/       — JdbcStudentRepositoryTest (@DataJdbcTest, Testcontainers)
├── course/  ...          (same shape)
├── book/    ...          (same shape)
├── enrollment/ ...        (same shape; no domain update methods, per §7 above)
├── identity/ ...          (same shape; includes password hashing/encryption unit tests)
├── shared/
│   └── ModuleBoundaryTest.java   — ApplicationModules.verify()
├── architecture/
│   ├── LayeringRulesTest.java        — ArchUnit: web → application → domain dependency direction, internal/ isolation
│   ├── DomainPurityTest.java         — ArchUnit: domain/ classes free of Spring/Spring Data dependencies
│   └── NamingConventionsTest.java    — ArchUnit: exception hierarchy, no-Impl-suffix services, port/ stays interfaces
└── integration/
    ├── StudentApiIntegrationTest.java   — full @SpringBootTest, Testcontainers MySQL
    ├── BookApiIntegrationTest.java
    ├── CourseApiIntegrationTest.java
    ├── EnrollmentApiIntegrationTest.java
    ├── AuthApiIntegrationTest.java
    ├── StaffAccountApiIntegrationTest.java  — UC-24/UC-25
    ├── DemoAccountsApiIntegrationTest.java  — asserts 404 when built with the `prod` profile
    ├── RbacIntegrationTest.java          — role × endpoint matrix
    └── CascadeLifecycleIntegrationTest.java  — cross-module delete scenarios
```

This structure is a **naming/organization proposal**, not a constraint imposed by any SA document — it directly mirrors the module layout already fixed in `06-low-level-design.md` §2.1 so test code sits next to the production code it verifies. `architecture/` is deliberately placed alongside `shared/` rather than inside any one module, since ArchUnit rules there scan the whole `management` package tree in a single test class, the same scope as `ModuleBoundaryTest`. Unlike every other suite in this tree, `architecture/` has no dependency on the Flyway migration or a running database (§5) — it can be written and kept green from the first module skeleton onward, making it the cheapest early warning if a later change drifts from the fixed hexagonal shape.

---

## 5. Environment & Configuration Needs

| Need | Detail |
| --- | --- |
| Local MySQL for manual/dev testing | `docker-compose.yml` (MySQL 8.4, port 3306 by default) — `make up` / `make down` once the `Makefile`/`docker-compose.yml` inconsistency (see below) is resolved |
| `.env` values | `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `MYSQL_PORT` — copied from `.env.example` per the Makefile's `env` target |
| Flyway migration | **Does not exist yet.** `06-low-level-design.md` §9 contains the designed DDL; it must be transcribed into a runnable `V1__*.sql` under `src/main/resources/db/migration` before any integration-level test (component, API/contract, database integrity) can run. This is a hard prerequisite, not a nice-to-have — flag as a blocking dependency for Phase 2+ of the sequence in §2. |
| Testcontainers | Requires Docker available in the CI runner; spins up an ephemeral MySQL 8 container per test run, migrated by the same Flyway script used in production, so schema drift between test and production is structurally impossible |
| ArchUnit | No database, Docker, or Spring context required — pure static analysis over compiled classes. Can run in CI (and locally) before the Flyway migration exists and before any other integration-level test is unblocked (§7's suspension criteria explicitly exempt it) |
| CI pipeline | Not yet configured — recommend a GitHub Actions workflow that runs `mvn verify` (unit + architecture + component + module-boundary + API/contract levels) on every PR against `main` |
| `app.demo-accounts.enabled` per profile | `true` in `dev`/`test`/`local`, hard `false` in `prod` (`04-authentication-authorization.md` §8, `06-low-level-design.md` §11.4) — CI runs the test-profile value; a separate TC-IDN-032 case must build with the `prod` profile specifically to confirm the route is absent, not merely `403` |

**Known inconsistency to resolve before relying on local environment tooling:** the root `Makefile` targets (`make up`, `make psql`, etc.) reference a Postgres container (`management-postgres`, `psql`, `colima`) while `docker-compose.yml` and `application.properties` are MySQL-based. This is a pre-existing scaffolding artifact, not something this documentation task resolves — call it out to the project owner before building environment automation on top of the `Makefile` as it currently stands.

**Known pre-existing scaffolding item to remove before security testing is meaningful:** `application.properties` currently sets hardcoded default Spring Security credentials (`spring.security.user.name`/`password`), which predates the designed session-based RBAC/`identity` module (`04-authentication-authorization.md`). These should be removed once the `identity` module's real `SecurityFilterChain` (`06-low-level-design.md` §11) is implemented — the security test level (§2.4 of the strategy doc) assumes the designed filter chain, not this placeholder.

---

## 6. Test Deliverables

- This four-document set (`01-test-strategy.md`, `02-test-plan.md`, `03-test-cases/`, `04-test-data-preparation.md`).
- Future, once implementation begins: the actual JUnit/integration test source code (per §4's structure), a JaCoCo coverage report, and CI execution reports (pass/fail per PR).
- A living traceability matrix (started in [README.md](./README.md)) kept current as UCs are implemented and their test cases move from "planned" to "automated."

---

## 7. Suspension & Resumption Criteria

| Condition | Action |
| --- | --- |
| Flyway migration is missing or fails to apply cleanly | Suspend all component/API-contract/database-integrity testing for the affected table(s) until fixed — unit and architecture-conformance tests (domain/VO level, ArchUnit) can continue unaffected, since neither needs a database |
| `ApplicationModules.verify()` fails (a module boundary violation) | Suspend API/contract-level testing for the violating module until the boundary is fixed — a boundary violation indicates the module's public/internal split doesn't match its test doubles' assumptions |
| An ArchUnit layering/naming rule fails | Suspend further feature work on the violating module (not just its tests) until resolved — an architecture-conformance failure means new code no longer matches the fixed hexagonal shape, and is cheaper to fix immediately than after more code is built on top of the violation |
| A P0 defect (per [01-test-strategy.md](./01-test-strategy.md) §6) is found in a shared concern (auth, error handling) | Suspend testing of dependent modules until the shared defect is fixed, since every module's API/contract tests assume the shared layer is correct |
| Testcontainers/Docker unavailable in an environment | Fall back to `docker-compose.yml`'s MySQL for that environment; unit-level tests are unaffected either way |

Resume each suspended level once its blocking condition is resolved; no other gating is imposed.

---

## 8. Risks & Assumptions

| # | Risk / Assumption | Impact if wrong | Mitigation |
| --- | --- | --- | --- |
| 1 | `Makefile` vs. `docker-compose.yml` database inconsistency (Postgres vs. MySQL) is unresolved | Local environment setup instructions in this plan (§5) may not work as written until fixed | Flag to project owner; this plan follows `docker-compose.yml`/`pom.xml`/`application.properties` (MySQL) as the source of truth, since three independent files agree vs. one stale `Makefile` |
| 2 | No Flyway migration exists yet | Every integration-level test level is blocked until it's written | Treat as the first implementation task, ahead of any module's business logic (§5) |
| 3 | No CI pipeline exists yet; this plan assumes GitHub Actions | Regression testing (§2 of strategy doc) has no automated home until one is configured | Confirm tooling choice with project owner before building workflow config |
| 4 | The System Administrator account itself is still assumed pre-seeded, per `04-authentication-authorization.md` §2.2/§3a, with no in-app provisioning flow — Registrar/Librarian/Course Administrator accounts now *are* provisioned in-app, via UC-24 | Test environments need a documented, out-of-band way to seed the one System Administrator account, or UC-24/UC-25 test cases have no caller to authenticate as | Addressed in [04-test-data-preparation.md](./04-test-data-preparation.md) §1 with a defined seed set |
| 5 | Testcontainers is a recommended, not-yet-added dependency | If not adopted, integration tests must run against `docker-compose.yml` MySQL instead, with manual lifecycle management and weaker test isolation | Strategy doc §4 flags this as a recommendation; plan proceeds either way, Testcontainers is preferred but not required for the test design itself |
| 6 | ArchUnit is a recommended, not-yet-added dependency | Without it, the `web`/`application`/`domain`/`port`/`internal` layering and naming conventions `06-low-level-design.md` §2 fixes rely on code review alone to catch drift, with no automated backstop | Strategy doc §4 flags this as a recommendation; the [03-test-cases/cross-cutting.md](./03-test-cases/cross-cutting.md) §7 rules are written to be added as soon as the dependency is, ideally alongside the very first module |
| 7 | `app.demo-accounts.enabled` (`04-authentication-authorization.md` §8) could be left `true` in a `prod` deployment by a missing/misconfigured profile | The 5 demo accounts, with publicly-documented credentials, would be reachable and loginable in production — a critical security exposure | `06-low-level-design.md` §11.4's `@ConditionalOnProperty` bean gating plus an explicit `application-prod.properties` override, tested by TC-IDN-032; flagged as P0 in [01-test-strategy.md](./01-test-strategy.md) §6 |
