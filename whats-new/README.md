# What's New

A running log of user-facing changes to this project — what shipped, when, and why. One file per
release; the current in-progress work lives in [`unreleased.md`](unreleased.md) until it merges to
`main` and gets its own dated file.

This is a changelog for people, not a commit log — `git log` already has the commit history, and
the versioned doc set under `docs/` already has the full design/architecture
reasoning. What belongs here is the short version: what changed, why it mattered, and where to look
for the long version.

## Index (newest first)

| Entry | Status | Summary |
| --- | --- | --- |
| [`unreleased.md`](unreleased.md) | In progress (`update-ui` branch) | Frontend redesign: role-aware home page, command palette, account page, and a Japanese Zen v2 pass on the app shell and data tables. |
| [`v0.1.md`](v0.1.md) | Released (2026-09-02, PR #16) | Backend performance remediation: connection-pool sizing, batched enrollment lookups, full-text search, keyset pagination — plus a search-fix regression that was found and corrected before release. |

## Adding an entry

Copy [`TEMPLATE.md`](TEMPLATE.md) to `<version-or-name>.md`, fill it in, and add a row to the index
above (newest first). Keep `unreleased.md` for whatever is currently in progress and not yet merged
to `main`; rename it to a version file (e.g. `v0.2.md`) once that work ships.
