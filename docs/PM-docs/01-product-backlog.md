# Product Backlog

Project Management Documentation — Part 1 of 4 (Product Backlog → [Sprint Plan](./02-sprint-plan.md) → [Scrum Artifacts](./03-scrum-artifacts.md) → [Sprint Backlog](./04-sprint-backlog.md)).

Turns the completed specification ([BA-docs](../BA-docs/), [SA-docs](../SA-docs/), [Testing](../Testing/)) into a ranked, estimated backlog. No new business rules, roles, or endpoints are introduced here — every functional item reuses the existing `US-x.x` / `UC-x` IDs verbatim from [user-stories.md](../BA-docs/user-stories.md) and [use-cases.md](../BA-docs/use-cases.md). Items with no user story (build/tooling prerequisites) are numbered `PM-0xx` and sourced from the gaps already flagged in [Testing/02-test-plan.md](../Testing/02-test-plan.md) §5 and §8.

---

## 1. How to read this backlog

- **Epic** — one of the 5 Spring Modulith modules (`student`, `course`, `book`, `enrollment`, `identity`) plus `shared`/platform and a final cross-cutting/hardening epic, matching the module boundaries fixed in [SA-docs/02-component-diagram.md](../SA-docs/02-component-diagram.md).
- **Item** — either a user story ID (`US-x.x`, acceptance criteria live in `user-stories.md` and are not restated here) or a platform item (`PM-0xx`).
- **Priority** — MoSCoW. Nearly everything is *Must* because [Testing/README.md](../Testing/README.md) already commits to covering all 25 UCs with at least one test case each; items marked *Should* are explicitly flagged as recommended-not-required in `Testing/02-test-plan.md` §8.
- **Estimate** — ideal developer-hours, not story points. The confirmed delivery model is a solo developer, so a shared team velocity in points has no meaning here; hours are a sizing input for [02-sprint-plan.md](./02-sprint-plan.md), not a commitment.
- **Sprint** — which sprint the item is pulled into, per the sequence fixed by `Testing/02-test-plan.md` §2 (dependency order: `shared` → `student`+`identity` provisioning → `course` → `book` → `enrollment` → `identity` auth → cross-cutting).

---

## 2. Epic A — Platform / `shared` foundation

No user-facing UC; unblocks every other epic.

| ID | Item | Priority | Estimate | Source |
| --- | --- | --- | --- | --- |
| PM-000 | Write the Flyway baseline migration (`V1__*.sql`) transcribed from the DDL already designed in `06-low-level-design.md` §9 | Must | 6h | `Testing/02-test-plan.md` §5 — "does not exist yet... hard prerequisite" |
| PM-001 | Fix the `Makefile` vs `docker-compose.yml` inconsistency (Makefile targets a Postgres container; compose/`application.properties` are MySQL) | Must | 2h | `Testing/02-test-plan.md` §5, §8 risk 1 |
| PM-002 | Remove the hardcoded `spring.security.user.name`/`password` placeholder in `application.properties` | Must | 1h | `Testing/02-test-plan.md` §5 "known pre-existing scaffolding item" |
| PM-003 | Stand up CI (GitHub Actions running `mvn verify` on every PR against `main`) | Must | 4h | `Testing/02-test-plan.md` §5, §8 risk 3 |
| PM-004 | Add ArchUnit + Testcontainers dependencies; create the `architecture/` test package skeleton (`LayeringRulesTest`, `DomainPurityTest`, `NamingConventionsTest`) and `shared/ModuleBoundaryTest` | Should | 6h | `Testing/02-test-plan.md` §4, §8 risks 5–6 (recommended, not required) |
| PM-005 | `shared` module: exception hierarchy + global exception handler + error envelope | Must | 6h | `06-low-level-design.md` §3 |
| PM-006 | Spring Security filter chain skeleton — session-based auth, role-based authorization gate in front of all five modules | Must | 8h | `06-low-level-design.md` §11; `SA-docs/04-authentication-authorization.md` |

**Epic A subtotal: 33h**

---

## 3. Epic B — `student` module + `identity` provisioning

| ID | Item | Priority | Estimate | Linked UC |
| --- | --- | --- | --- | --- |
| US-1.1 | Register a student (includes automatic `identity` account provisioning — username = email, random 8-char initial password, must-change-password flag) | Must | 8h | UC-1 |
| US-1.2 | Update a student's details | Must | 4h | UC-2 |
| US-1.3 | Remove a student (cascade: unassign owned books, remove enrollments, remove account) | Must | 5h | UC-3 |
| US-5.1 | Registrar looks up a student (search + full detail: fields, owned books, enrollments) | Must | 5h | UC-13, UC-17 |

**Epic B subtotal: 22h**

---

## 4. Epic C — `course` module

| ID | Item | Priority | Estimate | Linked UC |
| --- | --- | --- | --- | --- |
| US-3.1 | Create a course | Must | 4h | UC-8 |
| US-3.2 | Update a course | Must | 3h | UC-9 |
| US-3.3 | Remove a course (cascade: remove tied enrollments) | Must | 4h | UC-10 |
| US-5.3 | Course Administrator looks up courses + enrolled-student roster | Must | 5h | UC-15, UC-19 |

**Epic C subtotal: 16h** — has no dependency on `student` or `book`; can be pulled ahead of `book` within its sprint if slack allows (`Testing/02-test-plan.md` §2 note).

---

## 5. Epic D — `book` module

| ID | Item | Priority | Estimate | Linked UC |
| --- | --- | --- | --- | --- |
| US-2.1 | Add a book to the catalog | Must | 4h | UC-4 |
| US-2.2 | Assign a book to a student | Must | 4h | UC-5 |
| US-2.3 | Unassign a book (end ownership) | Must | 2h | UC-6 |
| US-2.4 | Remove a book | Must | 3h | UC-7 |
| US-5.2 | Librarian looks up books + current ownership | Must | 5h | UC-14, UC-18 |

**Epic D subtotal: 18h** — depends on `student` existing (optional ownership lookup via `StudentLookup`).

---

## 6. Epic E — `enrollment` module

| ID | Item | Priority | Estimate | Linked UC |
| --- | --- | --- | --- | --- |
| US-4.1 | Enroll a student in a course | Must | 5h | UC-11 |
| US-4.2 | End an enrollment | Must | 3h | UC-12 |
| US-5.5 | View enrollment detail (from a student's list or a course's roster) | Must | 3h | UC-20 |

**Epic E subtotal: 11h** — depends on both `student` and `course` existing.

---

## 7. Epic F — `identity` auth + self-service

Account provisioning itself ships with Epic B (US-1.1); these items are the rest of the `identity` module. **Sudden addition (post-planning):** staff-account provisioning (System Administrator role) and a dev/test-only demo-accounts endpoint, added to this epic per the updated `04-authentication-authorization.md` §3a/§3b/§8 — see [02-sprint-plan.md](./02-sprint-plan.md) Sprint 3 for the capacity impact.

| ID | Item | Priority | Estimate | Linked UC |
| --- | --- | --- | --- | --- |
| US-6.1 | Log in | Must | 5h | UC-21 |
| US-6.2 | Change my password | Must | 4h | UC-22 |
| US-6.3 | View a student's initial password (Registrar-only, only until changed) | Must | 3h | UC-23 |
| US-5.4 | Student views their own owned books and enrolled courses | Must | 4h | UC-16 |
| PM-016 | System Administrator role: extend `Role` enum + `SecurityFilterChain` RBAC rules for `/staff-accounts/**` | Must | 2h | `06-low-level-design.md` §11.1 |
| US-7.1 | Create a staff account (System Administrator provisions Registrar/Librarian/Course Administrator) | Must | 5h | UC-24 |
| US-7.2 | Deactivate/reactivate a staff account | Must | 3h | UC-25 |
| PM-017 | Demo-accounts endpoint (`GET /auth/demo-accounts`), profile-gated via `app.demo-accounts.enabled` | Must | 3h | `04-authentication-authorization.md` §8 |

**Epic F subtotal: 29h** (16h original + 13h added by the staff-account/demo-account requirement)

---

## 8. Epic G — Cross-cutting / hardening

No new UC; makes the whole system's non-functional guarantees testable and closes the release.

| ID | Item | Priority | Estimate | Source |
| --- | --- | --- | --- | --- |
| PM-010 | RBAC matrix integration tests (role × endpoint) | Must | 8h | `Testing/03-test-cases/cross-cutting.md` |
| PM-011 | Must-change-password gate enforced across all endpoints | Must | 4h | Identity.3; `use-cases.md` UC-21/UC-22 postconditions |
| PM-012 | Optimistic locking implementation + tests | Must | 5h | `06-low-level-design.md` §10 |
| PM-018 | Cross-module student-removal cascade: `book` reacts to `StudentDeleted` (`BookService.onStudentDeleted` clears ownership); `identity` is deprovisioned synchronously instead (`AccountProvisioning.deprovisionForStudent`, called from `StudentService.remove`) — an `@ApplicationModuleListener` on `identity` would cycle back against its existing `student` dependency (`AccountProvisioning`), which `ApplicationModules.verify()` rejects at build time | Must | 3h | `06-low-level-design.md` §13 (lines 1122/1124) specified both as listeners; only `enrollment`'s was decomposed into a Sprint 3 task, and implementing `identity`'s literally as specified proved to be a genuine module cycle, corrected during implementation |
| PM-013 | Cross-module cascade/lifecycle integration tests (student/book/course/enrollment removal chains) | Must | 6h | `req.md` §5; `Testing/03-test-cases/cross-cutting.md` |
| PM-014 | Implement and test the 7 explicit ambiguity resolutions | Must | 5h | `api-specification.md` §5 |
| PM-015 | JaCoCo coverage report + finalize living traceability matrix | Should | 3h | `Testing/02-test-plan.md` §6 |

**Epic G subtotal: 34h** (31h original + 3h added by PM-018)

---

## 8a. Epic H — Role-scoped access rework

Added after Sprint 4, in response to a walkthrough of the demo UI: with all four domain roles granted read access to everything, each role's screens showed data it had no reason to see, and two endpoints exposed database ids to the person operating them. No new UC — this narrows existing ones and re-keys two APIs. Sources: `note-fix.md` (the walkthrough findings), `02-component-diagram.md` §4, `api-specification.md` §5 decisions #9/#10.

| ID | Item | Priority | Estimate | Source |
| --- | --- | --- | --- | --- |
| PM-019 | Per-resource read grants: split the single four-role `GET` allow-list into one per resource, so a Registrar cannot read books, a Librarian cannot read courses or enrollments, and a Course Administrator reaches a student record only through a roster | Must | 4h | `02-component-diagram.md` §4; walkthrough findings |
| PM-020 | Business keys end to end: `POST`/`GET`/`DELETE /enrollments` keyed on `studentCode`; `PATCH /books/{isbn}/owner` and `GET /books?ownerStudentCode=` keyed on `studentCode`; every surrogate id removed from every response DTO. `StudentCode` promoted to `student`'s module root as published language, with `StudentLookup.idOf` as the single translation point | Must | 6h | `api-specification.md` §5 decision #9 |
| PM-021 | Related data as endpoints, not embedded fields: `GET /enrollments?studentCode\|courseCode` added; the permanently-empty `StudentDetail.books`/`.courses` and `CourseDetail.roster` stubs removed. Each list is separately paged and separately authorized | Must | 4h | `api-specification.md` §5 decision #10; the three stubs `06-low-level-design.md` §4.6/§5 had carried since Sprint 1 |
| PM-022 | Student self-service split: `GET /me/books-and-courses` replaced by `/me/profile`, `/me/courses`, `/me/books`. `/me/profile` is what lets a Student see their own record directly instead of searching for themselves, and is the only thing that tells them their own student code | Must | 3h | UC-16; walkthrough findings |
| PM-023 | Frontend rebuilt on Next.js + TypeScript + Chakra UI, with each role's navigation and screens narrowed to match PM-019 | Must | 13h | `UI-UX/01-frontend-strategy.md` |
| PM-024 | Docs HTML generated rather than hand-maintained: `util/md-to-html.js` compiles `docs/**/*.md`, the committed `.html` twins are deleted and gitignored | Should | 4h | The twins had already drifted — `SA-docs/01-system-overview.html` said "Part 1 of 5" where its Markdown source said "Part 1 of 6" |

**Epic H subtotal: 34h**

The four backend items are deliberately ordered PM-020 → PM-021 → PM-019 → PM-022: re-keying first means the new list endpoints are born business-key-addressed, and tightening the grants last means the tests written for the new endpoints are already in place when the RBAC matrix changes underneath them.

---

## 8b. Epic I — Registrar workflow and session oversight

Added after Sprint 5, from a second pass over the running application. Three of the five items are things the Registrar's daily work asked for and one is a gap in the System Administrator's; the two bug fixes came out of the same pass and are folded in here rather than tracked separately, because they surfaced on the screens Epic I touches.

Introduces the first new business rule since the original set — `Identity.8` — and the first new use cases since UC-25: UC-26, UC-27, UC-28. Sources: `req.md` §4 Identity.8, `use-cases.md` UC-26–28, `api-specification.md` §5 decisions #11–13.

| ID | Item | Priority | Estimate | Source |
| --- | --- | --- | --- | --- |
| PM-025 | Timestamp and date correctness: symmetric UTC conversion for `Instant`/`LocalDate` against MySQL's zoneless `DATETIME`/`DATE`, plus the shared test-datasource binding that had been masking the defect | Must | 5h | Reported as "the time when registered student was incorrect"; `06-low-level-design.md` §9.1a |
| PM-026 | Edit forms fetch the full record: `StudentFormDialog` and `CourseFormDialog` read the detail endpoint instead of the list summary, so `dateOfBirth` and `description` are populated rather than blank | Must | 2h | Reported as "when I change the information of student, the information doesn't change" |
| US-4.3 | Enroll a student in several courses at once, with a per-course outcome | Must | 8h | UC-26; `api-specification.md` §5 decision #12 |
| PM-027 | Enrolled-student count on the course list and detail, joined in SQL to avoid a module cycle | Should | 3h | UC-15/UC-19; `api-specification.md` §5 decision #11 |
| US-7.3 | See who is signed in | Must | 5h | UC-27; Identity.8 |
| US-7.4 | End someone's session | Must | 4h | UC-28; Identity.8 |
| PM-028 | Session-fixation protection: rotate the session id on login | Must | 1h | Found while wiring US-7.3 — the login filter had silently kept a no-op session strategy |

**Epic I subtotal: 28h**

PM-025 is first and alone: it changes the JDBC converter graph and every integration test's datasource URL, and nothing else should be moving while that lands. PM-028 is last in the list but is not separable in practice — it is one line in the same `CompositeSessionAuthenticationStrategy` that US-7.3 has to add, and it is listed on its own only because it fixes a real vulnerability that was not part of any request.

---

## 9. Ranked backlog (delivery order)

Matches the sprint sequence in [02-sprint-plan.md](./02-sprint-plan.md); this is the order items are pulled off the backlog, not a strict one-at-a-time queue within a sprint.

| Rank | ID | Item | Sprint |
| --- | --- | --- | --- |
| 1 | PM-000 | Flyway baseline migration | Sprint 0 |
| 2 | PM-001 | Makefile/docker-compose fix | Sprint 0 |
| 3 | PM-002 | Remove hardcoded security placeholder | Sprint 0 |
| 4 | PM-003 | CI pipeline | Sprint 0 |
| 5 | PM-004 | ArchUnit/Testcontainers skeleton | Sprint 0 |
| 6 | PM-005 | `shared` exception hierarchy + error envelope | Sprint 1 |
| 7 | PM-006 | Security filter chain skeleton | Sprint 1 |
| 8 | US-1.1 | Register a student (+ provisioning) | Sprint 1 |
| 9 | US-1.2 | Update a student | Sprint 1 |
| 10 | US-1.3 | Remove a student | Sprint 1 |
| 11 | US-5.1 | Registrar looks up a student | Sprint 1 |
| 12 | US-3.1 | Create a course | Sprint 2 |
| 13 | US-3.2 | Update a course | Sprint 2 |
| 14 | US-3.3 | Remove a course | Sprint 2 |
| 15 | US-5.3 | Course Administrator looks up courses | Sprint 2 |
| 16 | US-2.1 | Add a book | Sprint 2 |
| 17 | US-2.2 | Assign a book | Sprint 2 |
| 18 | US-2.3 | Unassign a book | Sprint 2 |
| 19 | US-2.4 | Remove a book | Sprint 2 |
| 20 | US-5.2 | Librarian looks up books | Sprint 2 |
| 21 | US-4.1 | Enroll a student in a course | Sprint 3 |
| 22 | US-4.2 | End an enrollment | Sprint 3 |
| 23 | US-5.5 | View enrollment detail | Sprint 3 |
| 24 | US-6.1 | Log in | Sprint 3 |
| 25 | US-6.2 | Change my password | Sprint 3 |
| 26 | US-6.3 | View a student's initial password | Sprint 3 |
| 27 | US-5.4 | Student views own books/courses | Sprint 3 |
| 28 | PM-016 | System Administrator role: RBAC extension | Sprint 3 |
| 29 | US-7.1 | Create a staff account | Sprint 3 |
| 30 | US-7.2 | Deactivate/reactivate a staff account | Sprint 3 |
| 31 | PM-017 | Demo-accounts endpoint | Sprint 3 |
| 32 | PM-010 | RBAC matrix tests | Sprint 4 |
| 33 | PM-011 | Must-change-password gate | Sprint 4 |
| 34 | PM-012 | Optimistic locking | Sprint 4 |
| 35 | PM-018 | Cascade listeners: `book` + `identity` (`StudentDeleted`) | Sprint 4 |
| 36 | PM-013 | Cascade/lifecycle integration tests | Sprint 4 |
| 37 | PM-014 | 7 ambiguity resolutions | Sprint 4 |
| 38 | PM-015 | Coverage report + traceability matrix | Sprint 4 |
| 39 | PM-020 | Business keys end to end (no ids on the API) | Sprint 5 |
| 40 | PM-021 | Related data as endpoints, not embedded fields | Sprint 5 |
| 41 | PM-019 | Per-resource read grants | Sprint 5 |
| 42 | PM-022 | Student self-service split | Sprint 5 |
| 43 | PM-023 | Frontend rebuild (Next.js + TypeScript + Chakra) | Sprint 5 |
| 44 | PM-024 | Generated docs HTML | Sprint 5 |
| 45 | PM-025 | Timestamp/date UTC correctness | Sprint 6 |
| 46 | PM-026 | Edit forms fetch the full record | Sprint 6 |
| 47 | PM-027 | Enrolled-student count on courses | Sprint 6 |
| 48 | US-4.3 | Enroll into several courses at once | Sprint 6 |
| 49 | US-7.3 | See who is signed in | Sprint 6 |
| 50 | US-7.4 | End someone's session | Sprint 6 |
| 51 | PM-028 | Session-fixation protection | Sprint 6 |

**Total: 225 ideal-hours across 51 backlog items** (25 user stories, 26 platform/hardening `PM-0xx` items), covering all 28 UCs identified in the Testing documentation. PM-016/017 and US-7.1/7.2 (13h) are a sudden mid-plan addition — see [02-sprint-plan.md](./02-sprint-plan.md) Sprint 3 for how this changed that sprint's capacity. PM-018 (3h) is a second, later addition — see Sprint 4 for how it closes a gap between `06-low-level-design.md` §13 and this backlog's original decomposition. Epic H (PM-019–024, 34h) is a third: it came out of walking the finished demo UI role by role, which surfaced access breadth and id exposure that reading the specification had not. Epic I (PM-025–028, US-4.3, US-7.3/7.4, 28h) is a fourth, and the first to add a business rule (`Identity.8`) rather than only narrowing or re-keying existing ones — it came from using the application rather than demoing it, which is why two of its items are bug fixes for defects no walkthrough had caught.
