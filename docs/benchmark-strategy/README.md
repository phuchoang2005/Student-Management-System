# Benchmark Documentation

Performance strategy, scenario catalog, workload datasets, and reporting protocol for the Student Management System. Unlike every other doc set in this repository, this one was written **after implementation** — and that ordering is the point. The hazards it targets are not risks a designer anticipated; they are properties of code that now exists, each citable by file and line.

This is documentation only — **no benchmark code is included here.** The five documents below specify a harness (k6 scenarios under a `bench/` directory, a deterministic dataset generator, `make bench` targets) precisely enough to build, on the same basis that [`Testing/`](../Testing/) specified a test suite before one existed and [`PM-docs/`](../PM-docs/) specified an implementation plan before there was an implementation.

Every document under `docs/` compiles to a styled HTML page via `make docs` (`util/md-to-html.js`). The HTML is generated and gitignored — the Markdown here is the source.

---

## Reading Order

1. **[01-benchmark-strategy.md](./01-benchmark-strategy.md)** — what kind of performance measurement this system needs and why: scope, the eight-hazard register that grounds everything else, metrics, the first proposed SLOs, tooling choice, environment parity, and risk prioritization.
2. **[02-benchmark-plan.md](./02-benchmark-plan.md)** — how a run is conducted so that two runs are comparable: harness layout, the warm-up/steady-state/cool-down protocol, execution sequence, entry/exit criteria, and the deliberately narrow CI integration.
3. **[03-benchmark-scenarios.md](./03-benchmark-scenarios.md)** — the scenario catalog: ~30 `BM-<MODULE>-<NNN>` entries across student, book, course, enrollment, identity, `me`, and cross-cutting, plus the four JMH microbenchmarks — each traced to a hazard, a use case, and an SLO class.
4. **[04-workload-data-preparation.md](./04-workload-data-preparation.md)** — the datasets: four scales (S1–S4), the skewed distributions that keep the results honest, deterministic seeding, bulk generation, the account cohort, and reset strategy.
5. **[05-baseline-and-reporting.md](./05-baseline-and-reporting.md)** — what happens to the numbers afterwards: baseline definition, regression thresholds as deltas, the run-record template, and the escalation ladder for diagnosing a red run.

Recorded runs live in **[`result/`](./result/)** — see that folder's README for the index and conventions.

---

## Relationship to the Other Doc Sets

This set introduces no new business rules, use cases, endpoints, or test cases. It measures what is already built and specified elsewhere.

| Source | What it contributes here |
| --- | --- |
| `management/src/main/java/**` | **The primary source.** The hazard register (`01` §3) cites implemented code by file and line; nothing in it is inferred from the specification. |
| [Testing/01-test-strategy.md](../Testing/01-test-strategy.md) | §1.3's exclusion of load testing, which `01` §2.1 reverses under a stated rationale; also §5's entry/exit shape, §6's P0/P1/P2 prioritization, and §7's defect scheme, all reused here |
| [Testing/04-test-data-preparation.md](../Testing/04-test-data-preparation.md) | §5.4 deferred bulk data generation and §7 fixed the synthetic-data/PII rule — `04` extends the first and inherits the second unchanged |
| [SA-docs/01-system-overview.md](../SA-docs/01-system-overview.md) | §5's deployment characteristics: one process, one connection pool, in-memory sessions — which fix what is worth measuring (H7) and what is out of scope (clustered load) |
| [SA-docs/api-specification.md](../SA-docs/api-specification.md) | Exact endpoint paths and verbs for every scenario; §5's design decisions, which explain why H4's per-course transactions are deliberate rather than defects |
| [SA-docs/05-database-schema.md](../SA-docs/05-database-schema.md) | The constraints every generated dataset must satisfy (`04` §1.1) |
| [BA-docs/use-cases.md](../BA-docs/use-cases.md) | The UC each scenario traces to, extending the existing traceability chain |

**Traceability.** The existing chain is `req.md` rule → UC → US → TC. This set adds a parallel one: **hazard (H1–H8) → `BM-*` scenario → UC → endpoint**. Every scenario names the hazard it exposes, or is explicitly labelled a control.

---

## Hazard → Scenario Index

The eight hazards from [01-benchmark-strategy.md](./01-benchmark-strategy.md) §3, and the scenarios in [03-benchmark-scenarios.md](./03-benchmark-scenarios.md) that measure each.

| Hazard | Summary | Priority | Scenarios |
| --- | --- | --- | --- |
| **H1** | Leading-wildcard `LIKE` search across 3–4 columns — a full table scan, run twice per paged request (rows + `COUNT(*)`) | P0 | BM-STU-002, BM-STU-003, BM-BK-001, BM-CRS-001, BM-XC-004 |
| **H2** | N+1 in enrollment listing — one course lookup per row, up to 100 per page | P0 | BM-ENR-001, BM-ENR-002, BM-ENR-003, BM-ME-002, BM-XC-003, BM-XC-004, BM-JMH-004 |
| **H3** | Deep `OFFSET` paging — MySQL generates and discards `offset` rows before returning any | P0 | BM-STU-004, BM-STU-001, BM-XC-004 |
| **H4** | Batch enrollment commits up to 50 separate transactions per request — deliberate, unquantified | P1 | BM-ENR-005, BM-ENR-006, BM-ENR-007, BM-ENR-008 |
| **H5** | Login is CPU-bound on BCrypt (strength 10, framework default); pools untuned | P1 | BM-IDN-001, BM-IDN-002, BM-STU-006, BM-JMH-001, BM-JMH-002 |
| **H6** | Cascade cleanup runs on a core-2/max-4 pool with a 50-slot queue, with persisted event publications | P2 | BM-XC-001, BM-XC-002, BM-CRS-004 |
| **H7** | Sessions are heap-resident and uncapped — the stated blocker for horizontal scaling | P2 | BM-XC-002, BM-IDN-004 |
| **H8** | No actuator, no Micrometer — every result is unattributable black-box latency | prerequisite | Not a scenario. Step 0 of [02](./02-benchmark-plan.md) §3. |

Two things that look like hazards and are **not** — a redundant index and a nonexistent N+1 — are documented in [01](./01-benchmark-strategy.md) §3.1, so they do not get "fixed."

---

## Status

Nothing has been measured yet. This set is the design; the first run establishes the S1 baseline ([02](./02-benchmark-plan.md) §3, step 1) and is recorded in [`result/`](./result/), which is empty until then.

Scheduling and estimates for building the harness live in [`PM-docs/01-product-backlog.md`](../PM-docs/01-product-backlog.md) §8c (Epic J, PM-029–039, 55h), decomposed into tasks in [`PM-docs/04-sprint-backlog.md`](../PM-docs/04-sprint-backlog.md) §§10–11 as Sprints 7 and 8. If the hazard register or the scenario catalog here changes, review those two sections for drift.
