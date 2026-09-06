# Project Management Documentation

Product Backlog, Sprint Plan, and Sprint Backlog for the Student Management System, written **ahead of implementation** — like [Testing/](../Testing/), this set turns a completed specification into an executable plan rather than describing work that already exists.

This is planning documentation only — no code is included here.

Documents are labelled by the version that introduced them. **v0.0** planned and scheduled Sprints 0–8; **v0.1** adds Epic K and Sprints 9–10, scheduling the fixes the first benchmark pass found. Nothing carrying a v0.0 label was edited by v0.1.

---

## Reading Order

1. **[01-product-backlog.md](./01-product-backlog.md)** *(v0.0)* — the ranked, estimated backlog: 10 epics, 62 items (25 user stories reused verbatim from `BA-docs/user-stories.md`, plus 37 `PM-0xx` platform, hardening, rework, and benchmark items sourced from `Testing/02-test-plan.md` and `Benchmark/benchmark-strategy/`).
2. **[02-sprint-plan.md](./02-sprint-plan.md)** *(v0.0)* — the 5 sprints planned up front (Sprint 0 setup + Sprints 1–4), following the exact module build order already fixed by `Testing/02-test-plan.md` §2, with per-sprint goals, scope, and Definition of Done; Sprints 5–8 were all added afterwards and are recorded there as addenda rather than folded into the timeline.
3. **[04-sprint-backlog.md](./04-sprint-backlog.md)** *(v0.0)* — every backlog item decomposed into concrete implementation sub-tasks (Domain/Port-Internal/Application/Web/Tests), sourced from `SA-docs/06-low-level-design.md`'s class/method definitions, across all 9 sprints.
4. **[epic-k-product-backlog.md](./epic-k-product-backlog.md)** *(v0.1)* — Epic K: ten backlog items, `PM-040`–`PM-049`, one per still-open `IP-*` fix from [`Benchmark/08-hazard-fix-specs.md`](../Benchmark/08-hazard-fix-specs.md), in the same 5-column format Epic J uses.
5. **[epic-k-sprint-plan.md](./epic-k-sprint-plan.md)** *(v0.1)* — Sprints 9 and 10, scheduling Epic K's items, continuing past Sprint 8.

Every document under `docs/` compiles to a styled HTML page via `make docs` (`util/md-to-html.js`). The HTML is generated and gitignored — the Markdown here is the source.

## Implementation status

**Sprints 0–6 are implemented**; the items in those sprints carry retrospective `**Status:**` notes in [04-sprint-backlog.md](./04-sprint-backlog.md) recording what the estimate got wrong.

**Sprints 7–8 (Epic J, performance benchmarking) are also executed** — `bench/` exists, [`Benchmark/result/`](../Benchmark/result/) holds six accepted v0.0-era run records, and [`Benchmark/06-conclusions-and-recommendations.md`](../Benchmark/06-conclusions-and-recommendations.md) synthesizes them. *This corrects a drift that existed while the two document sets were separate: the v0.0 edition of this README still described Epic J as unbuilt and unmeasured, contradicting [02-sprint-plan.md](./02-sprint-plan.md) §Sprint 8 ("both sprints are now executed"), which is the accurate statement. The v0.1 set flagged the contradiction rather than editing a v0.0 file; merging the two sets into one is what makes fixing it the right move.*

**Sprints 9–10 (Epic K) are executed**, per the per-sprint `Status:` sections in [epic-k-sprint-plan.md](./epic-k-sprint-plan.md): Sprint 9's `PM-040`–`PM-046` landed together in commit `e29248f`, and Sprint 10's `PM-047`/`PM-048` are code-complete with unit and integration coverage but **not yet benchmark-verified at the scale their hazards were found at** — that document says so item by item, including which re-runs are still owed. `PM-049` (move sessions off-heap) is in the Epic K backlog table but scheduled into no sprint, matching Phase 5 of [`Benchmark/07-improvement-roadmap.md`](../Benchmark/07-improvement-roadmap.md).

## Relationship to `BA-docs` / `SA-docs` / `Testing` / `Benchmark`

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
| [Benchmark/benchmark-strategy/01-benchmark-strategy.md](../Benchmark/benchmark-strategy/01-benchmark-strategy.md) | The eight-hazard register (H1–H8) and the proposed SLO classes Epic J's items are sized against — and the §2.1 reversal of `Testing/01-test-strategy.md` §1.3 that admits the work at all |
| [Benchmark/benchmark-strategy/03-benchmark-scenarios.md](../Benchmark/benchmark-strategy/03-benchmark-scenarios.md) | The 39 `BM-<MODULE>-<NNN>` scenarios PM-033, PM-035, PM-036, and PM-037 build |
| [Benchmark/07-improvement-roadmap.md](../Benchmark/07-improvement-roadmap.md) / [08-hazard-fix-specs.md](../Benchmark/08-hazard-fix-specs.md) | The `IP-01`…`IP-11` remediation items Epic K's `PM-040`–`PM-049` schedule one-for-one (`IP-09` excluded — already delivered) |

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
| Sprint 9 | — (no UC; remediation-driven) | Adds none — `PM-040`–`PM-046`, Phases 1–3 of the improvement roadmap |
| Sprint 10 | — (no UC; remediation-driven) | Adds none — `PM-047`–`PM-048`, Phase 4 (`IP-06`, `IP-08`) |

All 25 user stories and all 28 use cases are accounted for across Sprints 1–8; see [Testing/README.md](../Testing/README.md)'s UC → File Index for the corresponding test-case mapping. Sprints 7 and 8 are the first to be traced by something other than a use case: their items answer to the hazard register in [Benchmark/benchmark-strategy/01-benchmark-strategy.md](../Benchmark/benchmark-strategy/01-benchmark-strategy.md) §3, on the parallel chain *hazard → `BM-*` scenario → UC → endpoint*. Sprints 9–10 extend that chain one link further — *hazard → finding → `IP-*` → `PM-0xx`* — and likewise add no use case.

Sprints 5 through 8 were all added after [02-sprint-plan.md](./02-sprint-plan.md) was written and are recorded there as addenda rather than folded into the planned timeline; Sprints 9–10 are in [epic-k-sprint-plan.md](./epic-k-sprint-plan.md) for the same reason. Sprint 6 is the only one to introduce a new business rule (`Identity.8`) rather than only narrowing or re-keying existing ones. Sprints 7–8 are the only ones whose source document was itself written *after* the code it describes.

## Non-goals carried over from v0.1

- **No task-level decomposition for Epic K in `04-sprint-backlog.md`.** Epic K's items don't get the Domain/Port-Internal/Application/Web/Test breakdown every other epic gets. That detail is cheaper to write once an item is about to start, and `08-hazard-fix-specs.md`'s Approach/Targets/Verification fields already cover the same ground at the specification level.
- **No GitHub issues filed.** `Benchmark/benchmark-strategy/05-baseline-and-reporting.md` §5 requires one per `IP-*` item before any code change — scheduling here is not that authorization.
