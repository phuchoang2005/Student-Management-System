# Project Management Documentation

Sprint Plan and Scrum artifacts for the Student Management System, written **ahead of implementation** — like [Testing/](../Testing/), this set turns the completed specification into an executable plan rather than describing work that already exists. `management/src/main/java` currently has only a Spring Boot skeleton; nothing here should be read as "already built."

This is planning documentation only — no code is included here.

---

## Reading Order

1. **[01-product-backlog.md](./01-product-backlog.md)** — the ranked, estimated backlog: 7 epics, 37 items (20 user stories reused verbatim from `BA-docs/user-stories.md`, plus 7 platform-setup, 2 staff-account/demo-account, and 6 hardening items sourced from `Testing/02-test-plan.md`).
2. **[02-sprint-plan.md](./02-sprint-plan.md)** — 5 sprints (Sprint 0 setup + Sprints 1–4), following the exact module build order already fixed by `Testing/02-test-plan.md` §2, with per-sprint goals, scope, and Definition of Done.
3. **[03-scrum-artifacts.md](./03-scrum-artifacts.md)** — roles (solo PO/SM/Dev), ceremonies, Definition of Ready/Done, risk register, and the v1.0 release plan.
4. **[04-sprint-backlog.md](./04-sprint-backlog.md)** — every backlog item decomposed into concrete implementation sub-tasks (Domain/Port-Internal/Application/Web/Tests), sourced from `SA-docs/06-low-level-design.md`'s class/method definitions, across all 5 sprints.

Each of the four numbered documents has a styled HTML twin (`.html`) matching the house style already used in [SA-docs/](../SA-docs/) and [Testing/](../Testing/).

## Relationship to `BA-docs` / `SA-docs` / `Testing`

This set introduces no new business rules, use cases, architecture, or test cases — it sequences and estimates work already fully specified elsewhere:

| Source | What it contributes here |
| --- | --- |
| [BA-docs/user-stories.md](../BA-docs/user-stories.md) | The 20 user stories (US-1.1–US-7.2) that make up most of the Product Backlog, reused by ID |
| [BA-docs/use-cases.md](../BA-docs/use-cases.md) | The 25 use cases (UC-1–UC-25) each backlog item traces to |
| [SA-docs/02-component-diagram.md](../SA-docs/02-component-diagram.md) | The 5 Spring Modulith modules + `shared` that define the backlog's epics and the sprint sequence's dependency order |
| [SA-docs/06-low-level-design.md](../SA-docs/06-low-level-design.md) | Flyway DDL, exception hierarchy, security filter chain — sized as Sprint 0/1 platform items |
| [Testing/02-test-plan.md](../Testing/02-test-plan.md) | The fixed module build order (§2), missing-prerequisite items (§5), and risks (§8) this plan reuses directly |
| [Testing/03-test-cases/](../Testing/03-test-cases/) | The 170 test cases each sprint's Definition of Done points back to |
| [SA-docs/06-low-level-design.md](../SA-docs/06-low-level-design.md) §§4–8 | The concrete class/method names each Sprint Backlog task cites |

If a source document changes, review this set for drift — it is not an independent source of truth.

---

## UC / User Story → Sprint Traceability

| Sprint | User Stories | Use Cases |
| --- | --- | --- |
| Sprint 0 | — (platform only) | — |
| Sprint 1 | US-1.1, US-1.2, US-1.3, US-5.1 | UC-1, UC-2, UC-3, UC-13, UC-17 |
| Sprint 2 | US-3.1, US-3.2, US-3.3, US-5.3, US-2.1, US-2.2, US-2.3, US-2.4, US-5.2 | UC-8, UC-9, UC-10, UC-15, UC-19, UC-4, UC-5, UC-6, UC-7, UC-14, UC-18 |
| Sprint 3 | US-4.1, US-4.2, US-5.5, US-6.1, US-6.2, US-6.3, US-5.4, US-7.1, US-7.2 | UC-11, UC-12, UC-20, UC-21, UC-22, UC-23, UC-16, UC-24, UC-25 |
| Sprint 4 | — (cross-cutting; spans all) | RBAC/cascade/optimistic-locking/ambiguity-resolution coverage across all 25 UCs |

All 20 user stories and all 25 use cases are accounted for across Sprints 1–4; see [Testing/README.md](../Testing/README.md)'s UC → File Index for the corresponding test-case mapping.
