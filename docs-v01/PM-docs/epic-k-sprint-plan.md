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

**Status: executed** (commit `e29248f`, 2026-08-29) — all seven items landed together rather than incrementally per the order above. This document wasn't updated at the time; noted here only so it doesn't read as unstarted. A per-item retrospective (what each estimate got wrong, mirroring `docs-v00/PM-docs/04-sprint-backlog.md`'s convention) hasn't been written and would need its own review of that commit — out of scope for the Sprint 10 work this document's status section otherwise covers.

## Sprint 10 — Isolation and correctness (10h of 40h capacity)

| Order | ID | Item | Est. |
| --- | --- | --- | --- |
| 1 | PM-047 | Fix H6's event-publication loss | 5h |
| 2 | PM-048 | Isolate login bursts | 5h |

Phase 4 of the roadmap. Both items sit in disjoint subsystems (`shared/async` vs. the auth endpoint/Tomcat) with no file overlap with each other or with Sprint 9's items, and could in principle run alongside Sprint 9 rather than after it — split into its own sprint here for the same reason Epic J split its own read-scenario and write/cross-cutting work across two sprints: each sprint stays coherent by what it's actually verifying (in this case, failure-mode bounding rather than latency reduction), which keeps the sprint retrospective legible once these are executed.

A light sprint by design — 21h-of-40h precedent already exists in Sprint 8, so this isn't unusual for the tail of a benchmark-derived epic.

**Status: executed** (this session). `docs-v01/Benchmark/08-hazard-fix-specs.md`'s `IP-06`/`IP-08` entries each left the fix mechanism as an open choice between two or three named options; the mechanism actually chosen for each is recorded below, since an implicit, unstated choice is exactly what `IP-06`'s own entry says produced the H6 defect in the first place.

- **PM-047 — 5h estimate.** Chosen mechanism: widened `shared/async/AsyncConfig.java`'s `taskExecutor` (core=2/max=4/queue=50, default `AbortPolicy` → core=8/max=20/queue=1000, `ThreadPoolExecutor.CallerRunsPolicy`), added `EventPublicationRecoveryJob` (`@Scheduled`, fixed rate 60s, resubmits any publication incomplete for over 2 minutes via `IncompleteEventPublications.resubmitIncompletePublicationsOlderThan`), and turned on `spring.modulith.events.republish-outstanding-events-on-restart` as a third, restart-time net — three independent layers rather than picking one of the spec's three options outright, since none of them individually both fixes live bursts and covers a mid-cascade restart. Verified by the new `EventPublicationRegistryIntegrationTest.burstOfDeletesLeavesNoIncompletePublications` (JUnit-scale, N=10 concurrent student deletes, each enrolled in its own course to avoid an unrelated InnoDB secondary-index hot-spot on a shared course row) — green. **Not done this session:** the `BM-XC-001` re-run at N=200 this spec's own "Verification" section calls for — it needs the k6 harness against real hardware, not the 2-vCPU local sandbox this session ran in, so this item is code-complete but not yet benchmark-verified at the scale the hazard was originally found at.
- **PM-048 — 5h estimate.** Chosen mechanism: a semaphore-gated bulkhead (`shared/security/LoginBulkheadFilter.java`, `app.security.login-bulkhead.permits=20`) added immediately ahead of the login filter in `SecurityConfig`'s chain — a saturated permit count returns `429` before the authentication manager, and BCrypt, ever run. Verified by the new `LoginBulkheadFilterTest` (unit-level: rejects when saturated, recovers once a permit is released, non-login paths always pass through) plus the full existing `shared/security` + `identity` login/change-password/RBAC test suites staying green with the filter wired into the chain. **Not done this session:** the `BM-IDN-001`-ramp-during-`BM-XC-002`-soak blast-radius re-run the spec's "Verification" section calls for — same k6/hardware caveat as PM-047.

## Not scheduled

**PM-049** (move sessions off-heap) is in the Epic K backlog table but placed in neither sprint, matching `docs-v01/Benchmark/07-improvement-roadmap.md` Phase 5's own treatment: it stays a tracked backlog entry until a horizontal-scaling plan exists, at which point it is promoted and sequenced by that plan — not by this one.

## Before either sprint starts

Every item above still needs its own GitHub issue carrying the before-number, the hypothesis, and (later) the after-number, per `docs-v00/Benchmark/benchmark-strategy/05-baseline-and-reporting.md` §5. Scheduling an item into a sprint here is planning, not authorization — the same caveat `06-conclusions-and-recommendations.md` §1 states for the recommendations this epic is built from.
