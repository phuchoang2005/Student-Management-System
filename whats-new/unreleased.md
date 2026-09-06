# Unreleased — Frontend redesign (Japanese Zen v2)

| | |
| --- | --- |
| Status | In progress — uncommitted on `update-ui`, not yet merged to `main` |
| Date | as of 2026-09-06 |
| Reference | `docs/UI-UX/02-Japanese-Zen-Design.md` (updated alongside this work) |

## Summary

A pass on the demo UI's navigation and information architecture: one shared landing page instead of
a role-specific redirect, a command palette for jumping straight to a record by its business key,
and a rail-based app shell that responds down to phone width. The screenshots in the root
[`README.md`](../README.md#screenshots) were recaptured against this UI.

## Highlights

- **One front door.** Every role used to land somewhere different after login; all five now land on
  `/home`, which shows only what that role's own nav already reaches — see `AppShell.tsx`'s
  `navItemsFor()` — plus a "where you were" list of recently opened records for that session.
- **Command palette (⌘K), "Find a record."** Every record here is addressed by a business key a
  person reads off paper or off another screen; the palette jumps straight to it by code, scoped to
  what the signed-in role may read, instead of "pick a section, wait for a list, find the search box."
- **Account page.** "Who am I signed in as," change password, and sign out now have a destination
  (`/account`) instead of being loose links in the sidebar.
- **Student picker.** Replaces the old "type a student code and press Look up" field on
  `/enrollments` (and the Librarian's assign-owner field) with type-ahead search by code, name, or
  email.
- **Responsive app shell.** The sidebar becomes a drawer below `md`; the previous layout had no
  breakpoints at all and wasn't usable on a phone.
- **Redesigned login board.** Each role now shows its actual sidebar preview next to its demo
  account, so picking a role means seeing what you're about to get, not just a username to copy.
- **New UI primitives:** `Breadcrumb`, `DetailLayout`, and `ui/{Key, RecordSkeleton, RowAction,
  StatusDot, TableSkeleton, toaster}` — supporting loading states, record headers, and toasts
  consistently across screens that previously each rolled their own.

## Screenshots

`assests/images-demo/login.png`, `registrar-role.png`, `librarian-role.png`, `system-admin-role.png`
— recaptured with Playwright against this redesign.

## Details

- [`docs/UI-UX/02-Japanese-Zen-Design.md`](../docs/UI-UX/02-Japanese-Zen-Design.md) — the design system this implements.
- [`docs/UI-UX/01-frontend-strategy.md`](../docs/UI-UX/01-frontend-strategy.md) — the frontend strategy this pass updated.
- `management-frontend/src/components/AppShell.tsx` — the shell rewrite, with the reasoning in its doc comment.
