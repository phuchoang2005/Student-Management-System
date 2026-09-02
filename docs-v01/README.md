# Documentation

>[!NOTE]
>Version 0.1

## What changed in v0.1

- **[`Benchmark/`](./Benchmark/)** — turns `docs-v00/Benchmark/06-conclusions-and-recommendations.md`'s ranked recommendations into a sequenced improvement plan (`07-improvement-roadmap.md`, `08-hazard-fix-specs.md`), with one of the eleven items (`IP-09`, observability) corrected to reflect that it's already implemented; `09-v01-vs-v00-conclusions.md` and `result/` then verify the plan against real runs — including an `IP-02`/`IP-03` regression found and fixed along the way — and `10-customer-performance-summary.md` rolls the verified results up for a non-technical audience.
- **[`BA-docs/`](./BA-docs/)** — adds this system's first non-functional requirements, promoted from the benchmark's proposed SLO classes.
- **[`SA-docs/`](./SA-docs/)** — flags the architecture-documentation consequences of the improvement plan, including one guarantee (`tactical-ddd-design.md` §9's cascade "at-least-once" claim) the system does not currently keep.
- **[`PM-docs/`](./PM-docs/)** — adds Epic K (`PM-040`–`PM-049`) and two new sprints, scheduling the improvement plan's still-open items.

Everything else in this version is unchanged from v0.0 and is cited from `../docs-v00/` rather than copied — see each folder's own `README.md` for the delta convention this version follows.
