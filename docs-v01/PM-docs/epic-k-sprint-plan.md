# Sprints 9–10 — Epic K

Project Management — v0.1 addendum to [`docs-v00/PM-docs/02-sprint-plan.md`](../../docs-v00/PM-docs/02-sprint-plan.md). Continues past Sprint 8 (the last sprint that document defines), at the same capacity assumption it states: **40 ideal-hours per 2-week sprint**.

## Sprint 9 — Foundation and search-endpoint shape (31h of 40h capacity)

| Order | ID | Item | Est. |
| --- | --- | --- | --- |
| 1 | PM-040 | Raise HikariCP pool size | 2h |
| 2 | PM-041 | Raise `innodb_buffer_pool_size` | 2h |
| 3 | PM-042 | Fix `vuShard.js` sharding | 2h |
| 4 | PM-043 | Drop the double `COUNT(*)` scan | 4h |
| 5 | PM-044 | `FULLTEXT` search index | 6h |
| 6 | PM-045 | Keyset pagination | 10h |
| 7 | PM-046 | Batch-resolve enrollment N+1 | 5h |

Order 1–3 (Phase 1 of `docs-v01/Benchmark/07-improvement-roadmap.md`) is pure configuration and harness work with no application-code risk, and is a re-run baseline in itself: the P0 read catalog is re-run at S1/S2 once these three land, before anything in Phase 2 is measured against it. Order 4–6 (Phase 2) touches the same three repository classes and is sequenced cheapest-and-lowest-risk first, per the roadmap's own reasoning. Order 7 (Phase 3) has no file overlap with Phase 2 and could run concurrently with it if capacity allowed — it's placed last in this sprint's list only for narrative clarity, not because it's blocked on anything above it.

**Why 9h of slack, unlike Epic J's sprints:** Epic J only ever added new harness code outside `management/src/`, which nothing in `./mvnw verify`'s architecture tests could break. Every item in this sprint touches live production code with real regression risk — the slack is deliberate headroom for a fix that doesn't land as cleanly as its hour estimate assumed, not evidence the estimates are loose.

## Sprint 10 — Isolation and correctness (10h of 40h capacity)

| Order | ID | Item | Est. |
| --- | --- | --- | --- |
| 1 | PM-047 | Fix H6's event-publication loss | 5h |
| 2 | PM-048 | Isolate login bursts | 5h |

Phase 4 of the roadmap. Both items sit in disjoint subsystems (`shared/async` vs. the auth endpoint/Tomcat) with no file overlap with each other or with Sprint 9's items, and could in principle run alongside Sprint 9 rather than after it — split into its own sprint here for the same reason Epic J split its own read-scenario and write/cross-cutting work across two sprints: each sprint stays coherent by what it's actually verifying (in this case, failure-mode bounding rather than latency reduction), which keeps the sprint retrospective legible once these are executed.

A light sprint by design — 21h-of-40h precedent already exists in Sprint 8, so this isn't unusual for the tail of a benchmark-derived epic.

## Not scheduled

**PM-049** (move sessions off-heap) is in the Epic K backlog table but placed in neither sprint, matching `docs-v01/Benchmark/07-improvement-roadmap.md` Phase 5's own treatment: it stays a tracked backlog entry until a horizontal-scaling plan exists, at which point it is promoted and sequenced by that plan — not by this one.

## Before either sprint starts

Every item above still needs its own GitHub issue carrying the before-number, the hypothesis, and (later) the after-number, per `docs-v00/Benchmark/benchmark-strategy/05-baseline-and-reporting.md` §5. Scheduling an item into a sprint here is planning, not authorization — the same caveat `06-conclusions-and-recommendations.md` §1 states for the recommendations this epic is built from.
