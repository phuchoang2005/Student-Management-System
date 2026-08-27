# Management Frontend

Demo UI for the [Student Management System](../management/). Next.js 16 (App Router) · TypeScript ·
React 19 · Chakra UI v3.

Its purpose is to make the finished backend demonstrable: sit down, log in as each of the five roles
in turn, and walk the use cases end to end in a browser. See
[`docs/UI-UX/01-frontend-strategy.md`](../docs/UI-UX/01-frontend-strategy.md) for the full strategy
— stack rationale, screen map, error handling, and the demo script.

## Running it

```bash
make -C management up                      # MySQL, from the repo root
cd management && ./mvnw spring-boot:run    # :8080, seeds the 4 staff demo accounts
cd management-frontend && npm install && npm run dev   # :3000
```

Then open <http://localhost:3000>. The login page lists the seeded demo accounts live.

`BACKEND_ORIGIN` overrides the proxy target if the API is not on `localhost:8080`.

## Two constraints that shape the whole app

**Same-origin or nothing.** Auth is a `JSESSIONID` session cookie and `SecurityConfig` registers no
CORS configuration at all. A browser on `:3000` calling `:8080` directly would have its preflight
rejected and its cookie dropped, so `next.config.ts` rewrites `/api/*` and `/logout` to the backend
and every request stays same-origin. Adding CORS to the backend for a demo's sake was the rejected
alternative.

**`403` is ambiguous.** Anonymous, wrong-role, and must-change-password all produce a `403`, and two
of the three carry no body — the client cannot ask the server "who am I?". So `AuthContext` stores
the login response in `sessionStorage`, `permissions.ts` mirrors the server's rules, and a `403` is
resolved against local state rather than decoded.

## Business keys, not ids

No screen here holds a numeric id. A student is a `studentCode`, a book an `isbn`, a course a
`courseCode` — that is what the API accepts and what it returns. The one exception is the staff
account `id` on `/staff-accounts`, which addresses an `identity` record that has no business key of
its own; it comes from the listing and is never typed.

## Layout

```
src/
├── app/
│   ├── layout.tsx  providers.tsx  page.tsx     # shell, client providers, per-role landing redirect
│   ├── login/  (app)/change-password/
│   └── (app)/                                  # everything behind RequireAuth + AppShell
│       ├── students/  students/[code]/
│       ├── books/     books/[isbn]/
│       ├── courses/   courses/[code]/
│       ├── enrollments/
│       └── staff-accounts/
├── components/                                 # DataTable, Pagination, dialogs, form field, shell
├── lib/api/     { client, endpoints, types }   # the contract, in one place
├── lib/auth/    { AuthContext, RequireAuth, permissions }
├── lib/hooks/   { usePagedResource, useAsyncAction, useResource }
└── theme/system.ts
```

`lib/api/endpoints.ts` *is* the client-side copy of the contract — when the backend changes, exactly
that file moves.

## What each role sees

| | Students | Books | Courses | Enrollments |
| --- | --- | --- | --- | --- |
| **Registrar** | roll + CRUD; a student shows their enrolled courses | — | catalogue; a course shows its roster | look up by student code; enroll / end |
| **Librarian** | roll with search + pagination; a student shows their books on loan | full CRUD, assign / release | — | — |
| **Course Administrator** | no tab — reached only through a roster | — | catalogue + CRUD; a course shows its roster | course → roster → student profile |
| **Student** | own record, shown directly | own books | enrolled courses only | — |
| **System Administrator** | — | — | — | — (staff accounts only) |

The sidebar is rendered from the same list the route guards consult, so what a role can see and
what it can reach never drift apart. The server enforces all of it independently.

## Checks

```bash
npm run typecheck   # tsc --noEmit
npm run build       # production build
```

There are no automated frontend tests: the backend carries the test burden
([`docs/Testing/`](../docs/Testing/)), and duplicating it here buys nothing for a demo. The
verification checklist in the frontend strategy doc is the manual counterpart.

`npm run lint` is currently dead — `next lint` was removed in Next.js 16 and this project has no
ESLint config or dependency. `npm run typecheck` and `npm run build` are the real gates.

## Design system

The UI implements [`docs/UI-UX/02-Japanese-Zen-Design.md`](../docs/UI-UX/02-Japanese-Zen-Design.md)
through Chakra tokens and recipes in `src/theme/system.ts` — there is still no stylesheet of our
own. Geist (self-hosted via the `geist` package), Lucide for icons, and Framer Motion for the small
amount of motion §8 permits. `docs/UI-UX/01-frontend-strategy.md` §7.6 explains the four
non-obvious constraints before you edit the theme.
