# Software Architecture — v0.1

This folder is the **v0.1 delta** over [`../../docs-v00/SA-docs/`](../../docs-v00/SA-docs/) — not a fork of it. `docs-v00/SA-docs/` has no `README.md` of its own; this one exists because `docs-v01/` needs an entry point that states the delta convention, not because v00 had one to extend.

Nothing under `docs-v00/SA-docs/` is edited by this version.

---

## Inherited unchanged from v00

| Document | Covers |
| --- | --- |
| [`01-system-overview.md`](../../docs-v00/SA-docs/01-system-overview.md) | Actors, system context, deployment characteristics (§5) |
| [`02-component-diagram.md`](../../docs-v00/SA-docs/02-component-diagram.md) | Spring Modulith module composition, inter-module calls/events, RBAC-by-module table |
| [`03-sequence-diagrams.md`](../../docs-v00/SA-docs/03-sequence-diagrams.md) | Per-use-case sequence diagrams |
| [`04-authentication-authorization.md`](../../docs-v00/SA-docs/04-authentication-authorization.md) | Identity module, `users` schema, login/password/session lifecycle, RBAC summary |
| [`05-database-schema.md`](../../docs-v00/SA-docs/05-database-schema.md) | Conceptual MySQL 8 DDL, constraints, cascade behavior |
| [`06-low-level-design.md`](../../docs-v00/SA-docs/06-low-level-design.md) | Per-module class diagrams and method tables |
| [`api-specification.md`](../../docs-v00/SA-docs/api-specification.md) | OpenAPI contract description and 13 numbered design decisions |
| [`tactical-ddd-design.md`](../../docs-v00/SA-docs/tactical-ddd-design.md) | DDD tactical patterns: aggregates, value objects, domain events |
| [`openapi/`](../../docs-v00/SA-docs/openapi/) | The OpenAPI 3.0.3 source |

## What's new in v0.1

`docs-v01/Benchmark/07-improvement-roadmap.md` and `08-hazard-fix-specs.md` sequence eleven fixes (`IP-01`…`IP-11`) for the hazards `06-conclusions-and-recommendations.md` found. Several reach into architecture documents that describe the system one way today and would describe it differently once implemented — or, in one case, describe a guarantee the system does not currently keep:

| Document | Adds |
| --- | --- |
| [`07-benchmark-remediation-impact.md`](./07-benchmark-remediation-impact.md) | Per-document notes on what each `IP-*` item changes, what's already true today regardless of any fix landing, and two open decisions flagged rather than made |

## Non-goals

- No SA-docs diagram, schema definition, or design decision is edited. Every change `07-benchmark-remediation-impact.md` describes is future work — none of the eleven fixes has landed yet.
