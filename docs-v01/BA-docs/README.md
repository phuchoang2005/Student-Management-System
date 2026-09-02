# Business Analysis — v0.1

This folder is the **v0.1 delta** over [`../../docs-v00/BA-docs/`](../../docs-v00/BA-docs/) — not a fork of it. `docs-v00/BA-docs/` has no `README.md` of its own; this one exists because `docs-v01/` needs an entry point that states the delta convention, not because v00 had one to extend.

Nothing under `docs-v00/BA-docs/` is edited by this version.

---

## Inherited unchanged from v00

| Document | Covers |
| --- | --- |
| [`req.md`](../../docs-v00/BA-docs/req.md) | Business requirements: entities, relationships, invariants, lifecycle/cascade rules, data-integrity rules |
| [`use-cases.md`](../../docs-v00/BA-docs/use-cases.md) | UC-1…UC-28, each with actor, pre/postconditions, main/alternate flows, traced back to `req.md` |
| [`use-case-diagram.md`](../../docs-v00/BA-docs/use-case-diagram.md) | PlantUML use-case diagrams |
| [`activity-diagram.md`](../../docs-v00/BA-docs/activity-diagram.md) | Per-use-case activity diagrams |
| [`user-stories.md`](../../docs-v00/BA-docs/user-stories.md) | US-1.1…US-7.4 in Given/When/Then form |

## What's new in v0.1

[`docs-v01/Benchmark/`](../Benchmark/) turned `06-conclusions-and-recommendations.md`'s findings into a sequenced fix plan (`IP-01`…`IP-11`). Two of those findings reach into BA-docs:

| Document | Adds |
| --- | --- |
| [`non-functional-requirements.md`](./non-functional-requirements.md) | The first non-functional requirements this document set has ever stated — promoted from the benchmark's proposed SLO classes, now evidenced by six accepted runs rather than invented. Also carries two conditional notes for existing use cases, tied to open decisions in `docs-v01/Benchmark/08-hazard-fix-specs.md`. |

## Non-goals

- No use case, user story, or diagram is added or edited. Every UC/US referenced here is cited from `docs-v00/BA-docs/` as-is — see `non-functional-requirements.md` for why none of them currently needs to change.
