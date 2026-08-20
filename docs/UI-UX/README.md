# UI-UX for Student Management

UI/UX documentation for the demo frontend that sits over the completed backend in [`management/`](../../management/).

## Contents

- [01-frontend-strategy.md](./01-frontend-strategy.md) — build strategy for the demo UI: stack decision (React + Vite + plain JS, HTML5/CSS3, no component library), the as-built API contract read from the shipped controllers, the three integration constraints (same-origin cookie, ambiguous `403`, no logout endpoint), screen map, project structure, build order, and an end-to-end demo script covering all 5 roles.

## Relationship to the other doc sets

This set is downstream of everything else: it introduces no new business rules, roles, or endpoints. Every screen maps onto a use case in [BA-docs](../BA-docs/) and an endpoint already implemented and tested. Where the running code differs from [SA-docs/api-specification.md](../SA-docs/api-specification.md) — which was hand-authored before any backend existed — the code is treated as authoritative and the difference is called out explicitly.
