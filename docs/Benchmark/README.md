# Benchmark Plan & Result

Everything about this system's performance: how it was going to be measured, what the measurements said, what was decided in response, and whether the response worked.

Documents are labelled by the version that introduced them. **v0.0** designed the benchmark, ran it, and synthesized the findings; **v0.1** turned those findings into a sequenced fix plan and then verified the plan against real runs. Nothing carrying a v0.0 label was edited by v0.1 — the six accepted v0.0-era runs, the eight-hazard register, the benchmark-strategy design, and the conclusions synthesis are all unchanged, and every v0.1 document cites them as its evidence base rather than re-deriving anything from them.

---

## Reading order

**v0.0 — design and measurement**

- **[`benchmark-strategy/`](./benchmark-strategy/)** — the six-part design: strategy, plan, scenarios, workload data, baseline & reporting, and the Grafana/Prometheus dashboard design. Read that folder's [`README.md`](./benchmark-strategy/README.md) first.
- **[`result/`](./result/)** — the recorded runs, one file per run, v0.0-era and v0.1-era alike. See that folder's [`README.md`](./result/README.md) for the index and conventions.
- **[`06-conclusions-and-recommendations.md`](./06-conclusions-and-recommendations.md)** — synthesis of the six accepted v0.0-era runs: where performance is bad, why, what is not actually bad, and eleven prioritized recommendations. Read this after `result/` if you want the "so what," not just the numbers.

**v0.1 — plan, verification, and rollup**

- **[`07-improvement-roadmap.md`](./07-improvement-roadmap.md)** — phases `06` §6's eleven recommendations into an execution order, with the dependency and file-overlap reasoning for why they're grouped the way they are.
- **[`08-hazard-fix-specs.md`](./08-hazard-fix-specs.md)** — per-recommendation spec: approach, `BM-*` targets, hypothesis against the SLO classes, verification plan, and a pointer back to its roadmap phase.
- **[`09-v01-vs-v00-conclusions.md`](./09-v01-vs-v00-conclusions.md)** — the actual verification: compares the v0.1-era runs against the v0.0 baselines per `IP-*` item, including the `IP-02`/`IP-03` regression found in the first run and the fix verified in the second.
- **[`10-customer-performance-summary.md`](./10-customer-performance-summary.md)** — a non-technical rollup of the verified results, written for a customer/stakeholder audience rather than an engineering one.

## The `IP-*` ids

`06-conclusions-and-recommendations.md` §6 ranks eleven recommendations P0/P1/P2 but does not sequence them, size their cross-file overlap, or turn them into acceptance-criteria-bearing units of work — and its own §1 says none of them is an authorization to act on its own. `07` and `08` are that layer, and they introduce **`IP-01`…`IP-11`**, mapping 1:1 and in order onto `06` §6's existing table (P0 #1–5 → `IP-01`…`IP-05`, P1 #6–9 → `IP-06`…`IP-09`, P2 #10–11 → `IP-10`…`IP-11`).

The id is deliberately distinct from `H*` (hazards), `BM-*` (scenarios), and `PM-0xx` (backlog items): it names a unit of remediation work, not a hazard, a measurement, or a scheduled sprint item. The `IP-*` items that were still open are scheduled as Epic K in [`PM-docs/epic-k-product-backlog.md`](../PM-docs/epic-k-product-backlog.md) and [`epic-k-sprint-plan.md`](../PM-docs/epic-k-sprint-plan.md) — one `PM-04x` per item, `IP-09` excluded because it was already delivered.

## Where future runs go

Every `IP-*` fix is verified by re-running its scenario and recording a new run (`benchmark-strategy/05-baseline-and-reporting.md` §3). New records go in [`result/`](./result/) alongside the existing ones, tagged with the era they measure. **The six v0.0-era runs are a closed set** — they are the evidence the recommendations were derived from, and appending post-fix numbers to them would mix the evidence a plan was built from with the evidence of whether the plan worked. `result/README.md`'s index keeps the two eras separated by section for exactly that reason.

## Non-goals

- **No GitHub issues filed.** `benchmark-strategy/05-baseline-and-reporting.md` §5 requires one per `IP-*` item before any code change lands; filing them is a follow-up action this documentation does not perform.
- **No per-item Sprint Backlog decomposition.** Epic K's items are scheduled and estimated, but not broken down into the Domain/Port-Internal/Application/Web/Test sub-tasks `PM-docs/04-sprint-backlog.md` gives every other epic — `08-hazard-fix-specs.md`'s Approach/Targets/Verification fields already cover the same ground at the specification level.
