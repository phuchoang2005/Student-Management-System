# Improvement Roadmap

Benchmark Documentation — v0.1 addendum, Part 1 of 2 (Improvement Roadmap → [Hazard Fix Specs](./08-hazard-fix-specs.md)).

Turns [`06-conclusions-and-recommendations.md`](../../docs-v00/Benchmark/06-conclusions-and-recommendations.md) §6's eleven ranked recommendations into an execution order. v00 ranks them P0/P1/P2 by "expected impact ÷ cost" but never sequences them, never says which ones share files or risk, and never states which can run in parallel. This document is that layer — it is new reasoning on top of v00's evidence, not a restatement of it. Per-recommendation technical detail lives in [`08-hazard-fix-specs.md`](./08-hazard-fix-specs.md); this document only sequences.

---

## 1. How to Read This

Each `IP-*` id below maps 1:1, in order, onto a row of `06` §6's recommendation table. The mapping is fixed and does not change across future revisions of this document — if a recommendation is dropped or added later, it gets a new id rather than reusing or renumbering an existing one.

| IP id | `06` §6 # | Title | Hazard(s) | Phase |
| --- | --- | --- | --- | --- |
| **IP-01** | 1 | Raise HikariCP `maximumPoolSize` above the default of 10 | pool confound (§2) | 1 |
| **IP-02** | 2 | Replace leading-wildcard `LIKE` search with an index-friendly alternative | H1 | 2 |
| **IP-03** | 3 | Stop issuing a second full table scan for `COUNT(*)` on every search request | H1 | 2 |
| **IP-04** | 4 | Batch-resolve enrollment listing's per-row course lookups | H2 | 3 |
| **IP-05** | 5 | Switch deep list pages from `OFFSET`/`LIMIT` to keyset (seek) pagination | H3 | 2 |
| **IP-06** | 6 | Fix H6's silent event-publication loss | H6 | 4 |
| **IP-07** | 7 | Raise `innodb_buffer_pool_size` off the 128 MB container default | H1 | 1 |
| **IP-08** | 8 | Isolate login bursts from the rest of the application | H5 | 4 |
| **IP-09** | 9 | Keep actuator + Micrometer wired permanently behind the `benchmark` profile | H8 | Already delivered |
| **IP-10** | 10 | Move sessions off-heap before any horizontal-scaling plan | H7 | 5 |
| **IP-11** | 11 | Fix `bench/lib/vuShard.js`'s VU-sharding collision | benchmark harness only | 1 |

Every `IP-*` still needs its own GitHub issue carrying the before-number, the hypothesis, and (later) the after-number before any code change lands — `05-baseline-and-reporting.md` §5 is unchanged by this document and is not repeated per phase below.

---

## 2. Phase 1 — Foundation (config & tooling, no application-code risk)

**Items:** `IP-01`, `IP-07`, `IP-11`.

**`IP-09` was originally scheduled here and is not anymore — it's already done.** Verified directly against the current codebase: `management/pom.xml` carries `spring-boot-starter-actuator` and `micrometer-registry-prometheus` as permanent dependencies, `application-benchmark.properties` exposes `/actuator/{health,metrics,prometheus}` only under the `benchmark` profile on a dedicated `:8081` management port, and `SecurityConfig.java:161-170` gates `/actuator/**` on the main port behind `SYSTEM_ADMINISTRATOR`. `docs-v00/Benchmark/benchmark-strategy/06-dashboard-building.md` — a document this roadmap's own prior revision missed, since it isn't in the folder's "five-part design" reading order — specifies a full six-dashboard Grafana/Prometheus stack built on top of exactly this. `06` §6 recommendation #9 asked for actuator/Micrometer to be "kept wired permanently... rather than one-off instrumentation"; that has happened and been exceeded. See `08-hazard-fix-specs.md`'s corrected `IP-09` entry. Its removal doesn't change this phase's remaining logic below — the three items it does schedule were each independently justified without reference to `IP-09`.

None of the three remaining items touches `management/src/main/java` production code — `IP-01` and `IP-07` are configuration values, `IP-11` fixes the benchmark harness, not the application. None of them carries the regression risk the query-shape work in Phase 2 does, which is why they come first and are grouped together regardless of their `06` §6 priority labels:

- **`IP-01` is the dominant confound** (`06` §2): every scenario in the S1/S2 baselines was measured with a 10-connection pool saturating at 10 concurrent VUs, so every later verification run in every other phase is misleading until this lands. It has to be first, not merely early.
- **`IP-11` is promoted out of its P2 slot.** It is tooling-only, costs nothing extra to bundle here since this phase already touches the harness, and it is what makes `BM-STU-007` (student update) usable as a Write-simple regression baseline the next time any phase's verification run needs one — currently it cannot be trusted (`06` §5).
- **`IP-07` is grouped with `IP-01`** because both are Docker Compose / configuration changes with the same verification shape (re-run a scale sweep, read the new plateau or curve) and because `06` §3.1 already names it as the leading unverified hypothesis for `BM-BK-001`'s S2→S3 cliff — worth resolving before Phase 2 spends effort on the query itself, so that Phase 2's before-number for `IP-02`/`IP-03` isn't still confounded by buffer-pool misses.

**Exit criterion:** re-run the P0 read catalog at S1 and S2 (mirrors `02-benchmark-plan.md` §3 steps 1–2), plus `BM-XC-003` (pool sweep) and the S1→S2→S3 leg of `BM-XC-004` that touches `BM-BK-001`. This produces the new, pool- and buffer-pool-unconfounded floor that Phases 2–4 are measured against. Do not start Phase 2 before this floor exists — a query-shape fix measured against a pool-saturated baseline overstates its own effect. Since `IP-09`'s observability is already in place, this re-run can use it immediately — unlike the original benchmark execution sequence, no separate step is needed to close H8 first.

---

## 3. Phase 2 — Search-endpoint shape fixes (H1 + H3)

**Items, in this order:** `IP-03` → `IP-02` → `IP-05`.

All three touch the same three repository classes — `SpringDataStudentRepository`, `SpringDataBookRepository`, `SpringDataCourseRepository` — and the same endpoints (`BM-STU-002/003/004`, `BM-BK-001`, `BM-CRS-001`). Bundling them into one phase avoids re-measuring the same scenarios three separate times; the order within the phase is cheapest-and-lowest-risk first:

1. **`IP-03` first** — pure query logic, no schema change, no index to build or validate. Removing the redundant `COUNT(*)` scan halves the per-request scan cost immediately, independent of whatever indexing strategy `IP-02` ends up choosing.
2. **`IP-02` second** — a schema change (a `FULLTEXT` index or equivalent), which needs `IP-03` already in place so its own before/after measurement isn't still paying for two scans per request.
3. **`IP-05` last, and treated as its own sub-effort within the phase** — it is the only one of the three with a footprint outside `management/src/main/java`. The backend's `PageResponse` envelope and the frontend's shared `usePagedResource` hook + `Pagination.tsx` component are built around absolute page numbers and a `totalPages` count, and all five list screens (`students`, `books`, `courses`, `staff-accounts`, `enrollments`) funnel through that one shared hook and component. Switching to `WHERE id > :cursor ORDER BY id LIMIT :n` is therefore an API-contract change, not a repository-internal one — see `08-hazard-fix-specs.md`'s `IP-05` entry for the concrete file list. This is heavier than `06` §6's one-line description suggests, and is the reason `IP-05` is sequenced last and given the largest verification window in this phase.

**A coupling `06` §6 does not state:** `IP-03` and `IP-05` are not fully independent. `IP-03`'s "has more" / cheap-existence-check alternative to `COUNT(*)` only eliminates the count query entirely if the UI stops displaying "Page N of `totalPages`" — which `IP-05`'s keyset switch would otherwise still need to compute alongside cursor navigation, since `PageResponse.totalPages` is derived from the same count. Practically: implementing `IP-03` as "compute the count only on page 1, reuse it for subsequent pages of the same query" is safe to do independently of `IP-05`. Implementing it as "replace the count with a has-more check" requires `IP-05`'s frontend work to also drop the absolute page-number display — a product decision, flagged here, not made here.

**Exit criterion:** re-run `BM-STU-002/003/004`, `BM-BK-001`, `BM-CRS-001` at S1/S2/S3 (the `BM-XC-004` scale-sweep set) against the Phase 1 floor. `BM-BK-001` is the headline number to watch, given it is the worst absolute latency in the whole benchmark set (`06` §3.1).

---

## 4. Phase 3 — Enrollment N+1 (H2)

**Items:** `IP-04` alone.

Independent module (`enrollment/`), with no file overlap with Phase 2's student/book/course repositories. `06` §4 already names the pattern to port — `BookService`'s per-page owner-code memo, which `BM-BK-003` confirms already defeats the same shape of N+1 for book listings. Because the pattern is proven and already exists in the same codebase, this is porting, not designing, and carries less risk than either Phase 2 item. **Safe to run in parallel with Phase 2** if there is capacity, since neither touches the other's files and neither's verification scenario overlaps (`BM-ENR-001/002/003`, `BM-ME-002` vs. the student/book/course search scenarios).

**Exit criterion:** re-run `BM-ENR-001/002/003` and `BM-ME-002` against the Phase 1 floor; compare `BM-ENR-002` (100-row page) against `BM-ENR-001` (20-row page) the same way `06` §3.2 does — the ratio between them, not either number alone, is what shows whether the N+1 is actually gone.

---

## 5. Phase 4 — Isolation & correctness (H6 + H5 blast radius)

**Items:** `IP-06`, `IP-08`.

Grouped because both are about bounding a *failure or degradation mode* rather than reducing raw latency, and because they sit in disjoint subsystems — `shared/async` (`IP-06`) versus the auth endpoint / Tomcat thread pool (`IP-08`) — with no file overlap with each other or with Phases 2–3. **Safe to run in parallel with Phases 2 and 3** for the same reason.

`IP-06` targets the **one confirmed defect** in the whole benchmark set (`06` §3.5 — 568 of 801 `EVENT_PUBLICATION` rows left permanently incomplete after a 200-student burst delete, not delayed). Its `06` §6 severity label is P1, but sequencing it behind two full phases of latency work would be a mistake for reasons severity alone doesn't capture: it is a silent-audit-trail defect, not a slow endpoint, and nothing about it gets easier to reason about by waiting. This phase is what keeps it from drifting to the bottom of a priority-ordered backlog just because "P1" reads as less urgent than "P0."

`IP-08` does not touch BCrypt's work factor (a deliberate security property, per `06` §3.4 and `01-benchmark-strategy.md` H5) — it bounds the blast radius of a login burst on the rest of the application instead.

**Exit criterion:** for `IP-06`, re-run `BM-XC-001` at N=200 and confirm `event_publication` fully drains within a bounded window with zero rejected tasks. For `IP-08`, re-run `BM-IDN-001`'s concurrency ramp and confirm other endpoints' latency (sampled concurrently, as in the `BM-XC-002` soak mix) no longer degrades during the ramp.

---

## 6. Phase 5 — Tracked, not scheduled (P2, long-horizon)

**Item:** `IP-10`.

`06` §4 and §6 are explicit that no horizontal-scaling plan exists yet to make moving sessions off-heap urgent — the benchmark only adds the ~1.7–2.3 MB/session number to a risk `01-system-overview.md` §5 had already named qualitatively. This phase is a backlog entry, not a scheduled unit of work.

**Promotion trigger:** the day a horizontal-scaling plan exists for this system, `IP-10` moves out of this phase and into whichever phase that plan's own sequencing puts it — it does not need to wait for Phases 1–4 to complete, since it is orthogonal to all of them.

---

## 7. Cross-Phase Rules

Carried over from v00 and unchanged by this document — restated here only as a checklist, not redefined:

- **No code change without a linked issue** carrying the before-number, hypothesis, and after-number (`05-baseline-and-reporting.md` §5).
- **Every fix is verified by re-running the same scenario, at the same scale and seed, on the same host**, compared against the run that produced the finding (`05` §5).
- **Regression bands apply to every scenario a phase's change could plausibly touch, not only its named target.** A Phase 2 change to the student/book/course repositories should be checked against the read-single controls (`BM-STU-005`, `BM-BK-004`, `BM-CRS-003`) as well as the search scenarios it targets — those controls are supposed to stay flat, and confirming they still do is itself part of the verification.
- **New run records land under `docs-v01/Benchmark/result/`**, per this folder's [`README.md`](./README.md) — not appended to `docs-v00/Benchmark/result/`.

---

## 8. Out of Scope (this document)

- Per-recommendation technical approach, file lists, hypotheses, and verification detail — see [`08-hazard-fix-specs.md`](./08-hazard-fix-specs.md).
- Why each hazard matters, the SLO classes, and the evidence behind `06` §6's rankings — see `docs-v00/Benchmark/`, especially [`01-benchmark-strategy.md`](../../docs-v00/Benchmark/benchmark-strategy/01-benchmark-strategy.md) and [`06-conclusions-and-recommendations.md`](../../docs-v00/Benchmark/06-conclusions-and-recommendations.md).
- Filing the GitHub issues this roadmap's items require before implementation, and any future PM-docs backlog entries — not performed by this version (see this folder's [`README.md`](./README.md), "Non-goals").
