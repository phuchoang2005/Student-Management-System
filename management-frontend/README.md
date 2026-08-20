# Student Management Frontend

Demo-grade React UI over the backend in [`management/`](../management/). Built to the plan in
[docs/UI-UX/01-frontend-strategy.md](../docs/UI-UX/01-frontend-strategy.md).

It introduces no new business rules, roles, or endpoints: every screen maps onto a use case in
[BA-docs/use-cases.md](../docs/BA-docs/use-cases.md) and an endpoint already implemented and tested.

## Stack

React 19 · Vite 7 · react-router-dom 7 · plain JavaScript (no TypeScript) · hand-written CSS3.
Three runtime dependencies, two dev. No UI kit, no state library, no data-fetching library.

## Running it

Three terminals, from the repo root:

```bash
make up                                    # MySQL (colima + docker compose)
cd management && ./mvnw spring-boot:run     # :8080 — seeds the 4 staff demo accounts
cd management-frontend && npm install && npm run dev   # :5173
```

Then open <http://localhost:5173>.

`make reset` gives a fresh database between demo runs. Demo-account seeding is idempotent, so a
restart never resets a password that was changed mid-demo.

### Why the Vite proxy matters

Auth is a `JSESSIONID` session cookie and the backend registers **no CORS configuration** at all. A
browser on `:5173` calling `:8080` directly would have its preflight rejected and its cookie dropped.
`vite.config.js` proxies `/api` and `/logout` to `:8080` so every request is same-origin from the
browser's point of view. All API paths in the code are relative for this reason.

## Layout

```
src/
├── api/         client.js (fetch wrapper + ApiError) · endpoints.js (one fn per operation)
├── auth/        AuthContext · RequireAuth · permissions.js (the RBAC matrix as code)
├── hooks/       usePagedResource · useAsyncAction
├── components/  AppShell, DataTable, Pagination, Modal, Field, ErrorBanner, Toast, Badge, …
├── pages/       login, change-password, students, books, courses, enrollments, me, staff
└── styles/      tokens.css (custom properties) · base.css (reset + component classes)
```

`endpoints.js` is the client-side copy of the API contract — when the backend changes, that is the
one file that moves.

## Three things about this backend that shaped the code

**`403` is ambiguous.** Not-logged-in, wrong-role, and must-change-password all return `403`, and two
of the three carry no body — there is no session-probe endpoint, so the client cannot ask "who am I?".
Instead, the login response is stored in `AuthContext` (mirrored to `sessionStorage`) and
`permissions.js` mirrors `SecurityConfig`'s rules so nav items and buttons are hidden *before* any
request is made. A `403` becomes an edge case rather than the normal path.

**Logout is not part of the API.** `logout()` POSTs `/logout`, ignores any failure, then clears local
state. Client state is authoritative; the server session is best-effort.

**Wrong `currentPassword` returns `401`, not `400`.** The only use of `401` outside login. The
change-password form renders it inline on the field — treating it as an expired session would trap a
first-login user in a redirect loop, since that page is the only one they can reach.

## Backend gaps this UI surfaces rather than hides

Three stubs remain in the shipped backend. The UI shows them as gaps, so an empty table is never
mistaken for real data:

| Gap | Treatment |
| --- | --- |
| `StudentDetail.books` always `[]` | **Compensated** — the student detail page calls `GET /books?owner={id}` and shows the real result |
| `StudentDetail.courses` always `[]` | **Disclosed** — a muted note; no staff-facing endpoint exists to compensate with |
| `CourseDetail.roster` always `[]` | **Disclosed** — same |

One more worth knowing during a demo: `GET /auth/demo-accounts` advertises five accounts, but the
seeder skips the `STUDENT` one (a student-role account needs a real `students` row to satisfy the FK
co-invariant). The login page marks that chip as unseeded — reach the student role by registering a
real student instead, as the demo script does.

## Demo script

The run-through that exercises all five roles, from §11 of the strategy doc:

| # | Role | Action |
| --- | --- | --- |
| 1 | — | Open `/login`; the seeded accounts are listed live |
| 2 | `demo.registrar` | Register a student — the one-time initial password is shown; re-read it from the detail page |
| 3 | `demo.courseadmin` | Create course `CS101` |
| 4 | `demo.registrar` | Enroll the new student in `CS101` (needs their numeric id, shown on the detail page) |
| 5 | `demo.librarian` | Add a book, then assign it to the student |
| 6 | **the new student** | Log in (username = their email) → **forced** to `/change-password` → then `/me` shows the book and the course |
| 7 | `demo.sysadmin` | Create a staff account, then deactivate it from the list |
| 8 | `demo.sysadmin` | Note the nav shows only Staff Accounts — a domain read returns `403` |

All demo accounts use the password `Demo#12345`.

Steps 2→6 are the spine: a student created by one role, given a course by a second and a book by a
third, then logging in as themselves and seeing both.

## Not included

Deliberately, per the strategy doc: TypeScript, a component library, automated frontend tests (the
backend carries the test burden), a production build target, responsive/mobile layout, i18n, and
dark mode. This is a demo UI, and it trades polish for use-case coverage.
