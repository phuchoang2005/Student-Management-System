# Software Architecture

How the system is built: module composition, the identity and authorization design, the schema, the per-module class and method definitions, and the API contract.

Documents are labelled by the version that introduced them. **v0.0** is the original architecture set; **v0.1** is the increment written after the first benchmark pass. No v0.0 diagram, schema definition, or design decision was edited by v0.1 — `07-benchmark-remediation-impact.md` flags what each `IP-*` fix *would* change, rather than changing it.

---

## Documents

| Document | Version | Covers |
| --- | --- | --- |
| [`01-system-overview.md`](./01-system-overview.md) | v0.0 | Actors, system context, deployment characteristics (§5) |
| [`02-component-diagram.md`](./02-component-diagram.md) | v0.0 | Spring Modulith module composition, inter-module calls/events, RBAC-by-module table |
| [`03-sequence-diagrams.md`](./03-sequence-diagrams.md) | v0.0 | Per-use-case sequence diagrams |
| [`04-authentication-authorization.md`](./04-authentication-authorization.md) | v0.0 | Identity module, `users` schema, login/password/session lifecycle, RBAC summary |
| [`05-database-schema.md`](./05-database-schema.md) | v0.0 | Conceptual MySQL 8 DDL, constraints, cascade behavior |
| [`06-low-level-design.md`](./06-low-level-design.md) | v0.0 | Per-module class diagrams and method tables |
| [`api-specification.md`](./api-specification.md) | v0.0 | OpenAPI contract description and its numbered design decisions |
| [`tactical-ddd-design.md`](./tactical-ddd-design.md) | v0.0 | DDD tactical patterns: aggregates, value objects, domain events |
| [`openapi/`](./openapi/) | v0.0 | The OpenAPI 3.0.3 source |
| [`07-benchmark-remediation-impact.md`](./07-benchmark-remediation-impact.md) | v0.1 | Per-document notes on what each `IP-*` item changes, what's already true today regardless of any fix landing, and two open decisions flagged rather than made |

## What v0.1 added

[`Benchmark/07-improvement-roadmap.md`](../Benchmark/07-improvement-roadmap.md) and [`08-hazard-fix-specs.md`](../Benchmark/08-hazard-fix-specs.md) sequence eleven fixes (`IP-01`…`IP-11`) for the hazards `06-conclusions-and-recommendations.md` found. Several reach into architecture documents that describe the system one way today and would describe it differently once implemented — or, in one case, describe a guarantee the system does not currently keep (`tactical-ddd-design.md` §9's cascade "at-least-once" claim). `07-benchmark-remediation-impact.md` is the register of those consequences.
