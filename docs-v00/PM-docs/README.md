# Project Management Documentation

Product Backlog, Sprint Plan, and Sprint Backlog for the Student Management System, written **ahead of implementation** — like [Testing/](../Testing/), this set turns a completed specification into an executable plan rather than describing work that already exists.

Two states now coexist here, and the distinction matters when reading any figure below. **Sprints 0–6 have been implemented**; the items in those sprints carry retrospective `**Status:**` notes in [04-sprint-backlog.md](./04-sprint-backlog.md) recording what the estimate got wrong. **Sprints 7–8 (Epic J, performance benchmarking) have not**: they are specified from [benchmark-strategy/](../benchmark-strategy/01-benchmark-strategy.md) and nothing has been built or measured yet — `bench/` does not exist and `benchmark-strategy/result/` holds no run records. Nothing in Sprints 7–8 should be read as "already built."

This is planning documentation only — no code is included here.

---

## Reading Order

1. **[01-product-backlog.md](./01-product-backlog.md)** — the ranked, estimated backlog: 10 epics, 62 items (25 user stories reused verbatim from `BA-docs/user-stories.md`, plus 37 `PM-0xx` platform, hardening, rework, and benchmark items sourced from `Testing/02-test-plan.md` and `benchmark-strategy/`).
2. **[02-sprint-plan.md](./02-sprint-plan.md)** — the 5 sprints planned up front (Sprint 0 setup + Sprints 1–4), following the exact module build order already fixed by `Testing/02-test-plan.md` §2, with per-sprint goals, scope, and Definition of Done; Sprints 5–8 were all added afterwards and are recorded there as addenda rather than folded into the timeline.
3. **[04-sprint-backlog.md](./04-sprint-backlog.md)** — every backlog item decomposed into concrete implementation sub-tasks (Domain/Port-Internal/Application/Web/Tests), sourced from `SA-docs/06-low-level-design.md`'s class/method definitions, across all 9 sprints.

Every document under `docs/` compiles to a styled HTML page via `make docs` (`util/md-to-html.js`). The HTML is generated and gitignored — the Markdown here is the source.

## Relationship to `BA-docs` / `SA-docs` / `Testing`

This set introduces no new business rules, use cases, architecture, or test cases — it sequences and estimates work already fully specified elsewhere:

| Source | What it contributes here |
| --- | --- |
| [BA-docs/user-stories.md](../BA-docs/user-stories.md) | The 20 user stories (US-1.1–US-7.2) that make up most of the Product Backlog, reused by ID |
| [BA-docs/use-cases.md](../BA-docs/use-cases.md) | The 28 use cases (UC-1–UC-28) each backlog item traces to |
| [SA-docs/02-component-diagram.md](../SA-docs/02-component-diagram.md) | The 5 Spring Modulith modules + `shared` that define the backlog's epics and the sprint sequence's dependency order |
| [SA-docs/06-low-level-design.md](../SA-docs/06-low-level-design.md) | Flyway DDL, exception hierarchy, security filter chain — sized as Sprint 0/1 platform items |
| [Testing/02-test-plan.md](../Testing/02-test-plan.md) | The fixed module build order (§2), missing-prerequisite items (§5), and risks (§8) this plan reuses directly |
| [Testing/03-test-cases/](../Testing/03-test-cases/) | The 211 test cases each sprint's Definition of Done points back to |
| [SA-docs/06-low-level-design.md](../SA-docs/06-low-level-design.md) §§4–8 | The concrete class/method names each Sprint Backlog task cites |
| [benchmark-strategy/01-benchmark-strategy.md](../benchmark-strategy/01-benchmark-strategy.md) | The eight-hazard register (H1–H8) and the proposed SLO classes Epic J's items are sized against — and the §2.1 reversal of `Testing/01-test-strategy.md` §1.3 that admits the work at all |
| [benchmark-strategy/03-benchmark-scenarios.md](../benchmark-strategy/03-benchmark-scenarios.md) | The 39 `BM-<MODULE>-<NNN>` scenarios PM-033, PM-035, PM-036, and PM-037 build |

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
| Sprint 5 | — (platform/rework only) | Narrows UC-11, UC-16, UC-19, UC-20 — adds none |
| Sprint 6 | US-4.3, US-7.3, US-7.4 | UC-26, UC-27, UC-28 |
| Sprint 7 | — (no UC; hazard-driven) | Adds none — traces to hazards H1, H2, H3, H8 and the `BM-*` read catalog |
| Sprint 8 | — (no UC; hazard-driven) | Adds none — traces to hazards H4, H5, H6, H7 and the `BM-*` write, cross-cutting, and JMH catalog |

All 25 user stories and all 28 use cases are accounted for across Sprints 1–8; see [Testing/README.md](../Testing/README.md)'s UC → File Index for the corresponding test-case mapping. Sprints 7 and 8 are the first to be traced by something other than a use case: their items answer to the hazard register in [benchmark-strategy/01-benchmark-strategy.md](../benchmark-strategy/01-benchmark-strategy.md) §3, on the parallel chain *hazard → `BM-*` scenario → UC → endpoint*.

Sprints 5 through 8 were all added after [02-sprint-plan.md](./02-sprint-plan.md) was written and are recorded there as addenda rather than folded into the planned timeline. Sprint 6 is the only one to introduce a new business rule (`Identity.8`) rather than only narrowing or re-keying existing ones. Sprints 7–8 are the only ones whose source document was itself written *after* the code it describes. Sprint 7 is partially executed — PM-029/030/031 (instrumentation, harness skeleton, dataset generator) are done; PM-032/033/034 (Makefile targets, the read-scenario catalog, and the first baseline run) and all of Sprint 8 remain unexecuted.
