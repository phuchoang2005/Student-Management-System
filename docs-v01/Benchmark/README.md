# Benchmark — v0.1

This folder is the **v0.1 delta** over [`../../docs-v00/Benchmark/`](../../docs-v00/Benchmark/) — not a fork of it. Per the versioning convention this repository's `docs-v01/README.md` establishes: whatever this version does not change is linked back into v00, not copied. Only new documents, new sequencing decisions, or notes that revise something v00 states live here.

Nothing under `docs-v00/` is edited by this version. The six accepted runs, the eight-hazard register, the five-part benchmark-strategy design, and the conclusions synthesis are all unchanged, and every document in this folder cites them as its evidence base rather than re-deriving anything from them.

---

## Inherited unchanged from v00

Read these first if you haven't already — nothing in this folder restates them:

| Document | Covers |
| --- | --- |
| [`docs-v00/Benchmark/README.md`](../../docs-v00/Benchmark/README.md) | Reading order, relationship to the other doc sets, hazard → scenario index |
| [`benchmark-strategy/01-benchmark-strategy.md`](../../docs-v00/Benchmark/benchmark-strategy/01-benchmark-strategy.md) | The eight-hazard register (H1–H8), proposed SLOs, tooling choice, environment parity, risk prioritization |
| [`benchmark-strategy/02-benchmark-plan.md`](../../docs-v00/Benchmark/benchmark-strategy/02-benchmark-plan.md) | Harness layout, the warm-up/steady-state/cool-down run protocol, execution sequence, CI integration |
| [`benchmark-strategy/03-benchmark-scenarios.md`](../../docs-v00/Benchmark/benchmark-strategy/03-benchmark-scenarios.md) | The `BM-*` scenario catalog and hazard coverage table |
| [`benchmark-strategy/04-workload-data-preparation.md`](../../docs-v00/Benchmark/benchmark-strategy/04-workload-data-preparation.md) | Dataset scales S1–S4, distributions, determinism, the account cohort |
| [`benchmark-strategy/05-baseline-and-reporting.md`](../../docs-v00/Benchmark/benchmark-strategy/05-baseline-and-reporting.md) | Baseline definition, regression thresholds, the run-record template, the escalation ladder |
| [`benchmark-strategy/06-dashboard-building.md`](../../docs-v00/Benchmark/benchmark-strategy/06-dashboard-building.md) | The six-dashboard Grafana/Prometheus observability design — a sixth strategy document that exists in `docs-v00` but isn't yet in that folder's own "five-part design" reading order; cited here so it isn't missed a second time |
| [`06-conclusions-and-recommendations.md`](../../docs-v00/Benchmark/06-conclusions-and-recommendations.md) | The synthesis this version's plan is built from: verdict, the pool-contention root-cause finding, hazard-by-hazard analysis, and the ranked §6 recommendation table |
| [`result/`](../../docs-v00/Benchmark/result/) | The six accepted run records §6's recommendations are evidenced against |

## What's new in v0.1

`06-conclusions-and-recommendations.md` §6 ranks eleven recommendations P0/P1/P2 but does not sequence them, size their cross-file overlap, or turn them into acceptance-criteria-bearing units of work — and its own §1 says none of them is an authorization to act on its own. This version adds that layer:

| Document | Adds |
| --- | --- |
| [`07-improvement-roadmap.md`](./07-improvement-roadmap.md) | Phases the eleven recommendations into an execution order, with the dependency and file-overlap reasoning for why they're grouped the way they are — none of which v00 states |
| [`08-hazard-fix-specs.md`](./08-hazard-fix-specs.md) | Per-recommendation spec: approach, `BM-*` targets, hypothesis against the SLO classes, verification plan, and a pointer back to its roadmap phase |

Both documents introduce **`IP-01`…`IP-11`**, mapping 1:1 and in order onto `06` §6's existing table (P0 #1–5 → `IP-01`…`IP-05`, P1 #6–9 → `IP-06`…`IP-09`, P2 #10–11 → `IP-10`…`IP-11`). The id is deliberately distinct from `H*` (hazards), `BM-*` (scenarios), and `PM-0xx` (PM-docs backlog items) — it names a unit of remediation work, not a hazard, a measurement, or a scheduled sprint item. Promoting an `IP-*` entry into a GitHub issue (required before any code change — `05-baseline-and-reporting.md` §5) or into a future PM-docs epic is a later step this version does not perform.

## Where future runs go

Every `IP-*` fix is verified by re-running its scenario and recording a new run (`05-baseline-and-reporting.md` §3). Those future runs belong under **`docs-v01/Benchmark/result/`** — not created by this version, since no work against this plan has happened yet — keeping `docs-v00/Benchmark/result/`'s six runs as the closed historical baseline set the recommendations were derived from. Stating this now is deliberate: without it, the natural but wrong move is to keep appending new runs to the v00 folder, mixing the evidence a plan was built from with the evidence of whether the plan worked.

## Non-goals

- **No fix to `util/md-to-html.js`'s hardcoded doc root.** `DOCS_ROOT` is set to `docs-v00` at `util/md-to-html.js:46`; Markdown under `docs-v01/` does not yet compile to `.html` through the existing `make -C docs-v00 docs` pipeline. A known gap, not addressed by this version.
- **No new `PM-0xx` backlog items or PM-docs edits.** See "What's new" above.
- **No GitHub issues filed.** `05-baseline-and-reporting.md` §5 requires one per `IP-*` item before any code change lands; filing them is a follow-up action this documentation does not perform.
