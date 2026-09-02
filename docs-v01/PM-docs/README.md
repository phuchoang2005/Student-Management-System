# Project Management — v0.1

This folder is the **v0.1 delta** over [`../../docs-v00/PM-docs/`](../../docs-v00/PM-docs/) — not a fork of it. Nothing under `docs-v00/PM-docs/` is edited by this version.

A heads-up before reading further: `docs-v00/PM-docs/README.md:5` says Sprints 7-8 (Epic J) "have not [been implemented]... nothing has been built or measured yet," while `docs-v00/PM-docs/02-sprint-plan.md:154` says "both sprints are now executed." The second is correct as of this writing — `bench/` exists, `docs-v00/Benchmark/result/` holds six accepted run records, and `06-conclusions-and-recommendations.md` synthesizes them. This is a pre-existing drift in a `docs-v00` file; it is not fixed here, only flagged so it doesn't mislead a reader of this addendum into thinking Epic J's own status is unsettled.

---

## Inherited unchanged from v00

| Document | Covers |
| --- | --- |
| [`01-product-backlog.md`](../../docs-v00/PM-docs/01-product-backlog.md) | The ranked, estimated backlog — 10 epics (A–J), 62 items, 280 ideal-hours |
| [`02-sprint-plan.md`](../../docs-v00/PM-docs/02-sprint-plan.md) | Sprint-by-sprint capacity and scheduling, Sprints 0–8 |
| [`04-sprint-backlog.md`](../../docs-v00/PM-docs/04-sprint-backlog.md) | Every backlog item decomposed into Domain/Port-Internal/Application/Web/Test sub-tasks |

## What's new in v0.1

`docs-v01/Benchmark/07-improvement-roadmap.md` and `08-hazard-fix-specs.md` sequence ten still-open fixes (`IP-01`…`IP-08`, `IP-10`, `IP-11` — `IP-09` is already delivered, see `docs-v01/Benchmark/08-hazard-fix-specs.md`'s corrected entry) into phases. Nothing in `docs-v00/PM-docs/` turns that into a scheduled, estimated backlog item — this addendum is that layer:

| Document | Adds |
| --- | --- |
| [`epic-k-product-backlog.md`](./epic-k-product-backlog.md) | Epic K: ten backlog items, `PM-040`–`PM-049`, one per still-open `IP-*` fix, in the same 5-column format Epic J uses |
| [`epic-k-sprint-plan.md`](./epic-k-sprint-plan.md) | Two new sprints (9 and 10) scheduling Epic K's items, continuing past Sprint 8 |

## Non-goals

- **No task-level decomposition in `04-sprint-backlog.md`.** Epic K's items don't get the Domain/Port-Internal/Application/Web/Test breakdown `04-sprint-backlog.md` gives every other epic. That level of detail is cheaper to write once an item is about to start — and none has a GitHub issue yet, let alone a start date — and `08-hazard-fix-specs.md`'s Approach/Targets/Verification fields already cover the same ground at the specification level.
- **No `docs-v00/PM-docs/` file is edited.**
- **No GitHub issues filed.** `05-baseline-and-reporting.md` §5 requires one per `IP-*` item before any code change — scheduling here is not that authorization.
