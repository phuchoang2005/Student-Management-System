# Documentation

> [!NOTE]
> Covers **v0.0** (the original specification) and **v0.1** (the post-benchmark increment), merged into one tree.

The specification for the Student Management System. Code comments and Javadoc cite these documents by section, and work is traced by identifier across them — `UC-n` (use case), `US-x.y` (user story), `PM-0xx` (backlog item), `TC-XXX-nnn` (test case), `H*` (performance hazard), `BM-*` (benchmark scenario), `IP-*` (remediation item).

---

## The document sets

| Folder | Covers |
| --- | --- |
| [`BA-docs/`](./BA-docs/) | Business analysis: requirements, 28 use cases, user stories, activity and use-case diagrams, and the non-functional requirements |
| [`SA-docs/`](./SA-docs/) | Software architecture: system overview, module composition, sequence diagrams, auth design, database schema, low-level design, the OpenAPI contract, and the DDD tactical design |
| [`PM-docs/`](./PM-docs/) | Project management: product backlog, sprint plans, sprint backlog — Sprints 0–10 |
| [`Testing/`](./Testing/) | Test strategy, test plan, 211 test cases by module, and test-data preparation |
| [`Benchmark/`](./Benchmark/) | Performance: the benchmark design, the recorded runs, the conclusions, the improvement roadmap, and the verification of that roadmap |
| [`UI-UX/`](./UI-UX/) | Frontend strategy and the Japanese Zen design language the demo UI implements |

Each folder's own `README.md` is its entry point and lists its documents with the version each was introduced in.

## How the two versions relate

**v0.0** is the original set: the system was specified, planned, built, tested, and then benchmarked for the first time. `Benchmark/06-conclusions-and-recommendations.md` closes it with eleven ranked recommendations.

**v0.1** is the increment built on top of those findings. It was written as a *delta*: no v0.0 document was edited, and every v0.1 document cites the v0.0 evidence it rests on rather than restating it. That convention is preserved here — merging the two trees changed paths and merged folder READMEs, not the documents themselves.

What v0.1 added:

- **[`Benchmark/`](./Benchmark/)** — turns `06-conclusions-and-recommendations.md`'s ranked recommendations into a sequenced improvement plan (`07-improvement-roadmap.md`, `08-hazard-fix-specs.md`), with one of the eleven items (`IP-09`, observability) corrected to reflect that it was already implemented; `09-v01-vs-v00-conclusions.md` and the v0.1-era records in `result/` then verify the plan against real runs — including an `IP-02`/`IP-03` regression found and fixed along the way — and `10-customer-performance-summary.md` rolls the verified results up for a non-technical audience.
- **[`BA-docs/`](./BA-docs/)** — adds this system's first non-functional requirements, promoted from the benchmark's proposed SLO classes.
- **[`SA-docs/`](./SA-docs/)** — flags the architecture-documentation consequences of the improvement plan, including one guarantee (`tactical-ddd-design.md` §9's cascade "at-least-once" claim) the system does not currently keep.
- **[`PM-docs/`](./PM-docs/)** — adds Epic K (`PM-040`–`PM-049`) and two new sprints, scheduling the improvement plan's still-open items.

## Building

Every Markdown file here compiles to a styled HTML page:

```sh
make -C docs docs         # compile docs/**/*.md → .html
make -C docs docs-watch   # ...and rebuild on every save
make -C docs docs-clean   # remove the generated HTML
```

The compiler is [`util/md-to-html.js`](../util/md-to-html.js). **The `.html` twins are generated and gitignored — never edit them, edit the `.md`.** `SA-docs/api-specification.html` is the exception in kind, not in rule: it comes from the Redocly pipeline described in `SA-docs/api-specification.md` §4, and is likewise generated from `SA-docs/openapi/`.
