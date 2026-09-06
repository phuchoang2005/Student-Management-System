# Business Analysis

The business-requirements set: what the system must do, for whom, and under which rules — stated independently of how it is built.

Documents are labelled by the version that introduced them. **v0.0** is the original specification; **v0.1** is the increment written after the first benchmark pass. Nothing carrying a v0.0 label was edited by v0.1 — the newer document cites the older rather than rewriting it, and where the two could be read as disagreeing, the newer one says so explicitly.

---

## Documents

| Document | Version | Covers |
| --- | --- | --- |
| [`req.md`](./req.md) | v0.0 | Business requirements: entities, relationships, invariants, lifecycle/cascade rules, data-integrity rules |
| [`use-cases.md`](./use-cases.md) | v0.0 | UC-1…UC-28, each with actor, pre/postconditions, main/alternate flows, traced back to `req.md` |
| [`use-case-diagram.md`](./use-case-diagram.md) | v0.0 | PlantUML use-case diagrams |
| [`activity-diagram.md`](./activity-diagram.md) | v0.0 | Per-use-case activity diagrams |
| [`user-stories.md`](./user-stories.md) | v0.0 | US-1.1…US-7.4 in Given/When/Then form |
| [`non-functional-requirements.md`](./non-functional-requirements.md) | v0.1 | The first non-functional requirements this document set has ever stated — promoted from the benchmark's proposed SLO classes, now evidenced by six accepted runs rather than invented. Also carries two conditional notes for existing use cases, tied to open decisions in [`Benchmark/08-hazard-fix-specs.md`](../Benchmark/08-hazard-fix-specs.md). |

## What v0.1 added, and what it deliberately did not

[`Benchmark/`](../Benchmark/) turned `06-conclusions-and-recommendations.md`'s findings into a sequenced fix plan (`IP-01`…`IP-11`). Those findings reach into BA-docs in exactly one place: `non-functional-requirements.md`.

No use case, user story, or diagram was added or edited. Every UC/US that `non-functional-requirements.md` references is cited from the v0.0 documents above as-is — see that document for why none of them currently needs to change.
