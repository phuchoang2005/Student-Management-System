# Frontend Strategy — Demo UI

UI-UX Documentation — Part 1 of 1.

A build strategy for a **demo-grade frontend** over the completed backend in [`management/`](../../management/). It introduces no new business rules: every screen below maps onto a use case already specified in [BA-docs/use-cases.md](../BA-docs/use-cases.md) and an endpoint already implemented and covered by tests.

Unlike [SA-docs/api-specification.md](../SA-docs/api-specification.md), which was hand-authored *before* any backend existed, this document is derived **from the shipped source**. Where the running code and the specification differ, the code is treated as authoritative and the difference is called out explicitly (§4).

---

## 1. Purpose and scope

### 1.1 Why a frontend at all

The backend is feature-complete — RBAC and the must-change-password gate enforced and integration-tested. None of that is *visible*. The purpose of this frontend is to make the finished system demonstrable: a person sits down, logs in as each of the 5 roles in turn, and walks the use cases end to end in a browser.

That is the whole goal. It is **not** a production UI, and the strategy below trades polish for coverage deliberately.

### 1.2 In scope

- All 5 roles: `REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR`, `STUDENT`, `SYSTEM_ADMINISTRATOR`.
- Every implemented endpoint reachable from some screen (§3 is the full inventory).
- The three cross-cutting behaviours that are the most interesting things this backend does: **per-resource role-based access control**, the **forced initial password change**, and the fact that **no screen ever holds a database id**.
- An end-to-end demo script (§11) that ties the five modules into one narrative.

### 1.3 Out of scope

Named explicitly so their absence reads as a decision, not an oversight:

| Excluded | Why |
| --- | --- |
| Automated frontend tests | The backend already carries the test burden ([Testing/](../Testing/)); duplicating it here buys nothing for a demo. §12 is the manual counterpart. |
| Server-side rendering of data | The session lives in a browser cookie and the app is a client-rendered SPA behind Next's router. Next is used for its routing, dev server, and rewrite proxy, not for RSC data fetching. |
| Production deployment | The demo runs on `next dev`. |
| i18n, formal accessibility audit | Not on the demo path — though Chakra's primitives carry keyboard and ARIA behaviour by default, and every table row that acts as a link is keyboard-activatable. |
| Optimistic-locking UI (PM-012) | The backend implements it, but no endpoint surfaces a version token to the client. |

---

## 2. Stack

### 2.1 The choice

| Layer | Choice |
| --- | --- |
| Framework | Next.js 16, App Router |
| Language | TypeScript 5.9 (strict) |
| UI | React 19 + Chakra UI v3 |
| Styling | Emotion (via Chakra) + an SSR style registry — §7.5 |
| Theming | `next-themes`, `attribute="class"`, following the OS |
| Data fetching | Native `fetch`, wrapped once in `src/lib/api/client.ts` |
| State | React Context (`AuthContext`) + local component state |

### 2.2 Why TypeScript, and why a component library

Both are reversals of an earlier version of this document, which specified plain JavaScript and no UI kit. What changed:

**TypeScript** earns its place because of §2.4: this API is addressed entirely by business keys, and the single most valuable compile-time guarantee here is that a `studentCode` is never confused with an id, or a `courseCode` with an ISBN. `src/lib/api/types.ts` transcribes the response DTOs, and the absence of an `id` field on those types is what makes "the UI cannot use a database id" a fact the compiler checks rather than a convention a reviewer has to spot.

**Chakra UI** earns its place because the screens are no longer just tables and forms. The role rework gave several screens *two* shapes (`/students` renders a profile for a Student and a searchable roll for staff; `/enrollments` is a code lookup for a Registrar and a drill-down for a Course Administrator), and every detail screen now composes a record card with one or two independently paged related tables. Hand-rolling dialogs, focus management, and a coherent light/dark palette across that surface is more work than adopting primitives that already have them — the earlier argument ("roughly a dozen components, none of them novel") stopped being true.

### 2.3 Why React state and not a data library

Every screen follows one of two patterns: *fetch a page and render it*, or *submit a form and show the result*. Three hooks (`usePagedResource`, `useResource`, `useAsyncAction`, §7.3) cover both. There is no cache-invalidation problem worth a library here — after any write, the list simply refetches.

### 2.4 Business keys, not ids

The single rule that shapes the API layer: **no screen holds a numeric id**. A student is a `studentCode`, a book an `isbn`, a course a `courseCode`. That is what every endpoint accepts and what every response returns (api-specification.md §5 decision #9).

The one exception is the staff-account `id` on `/staff-accounts`, which addresses an `identity` record with no business key of its own — a username can be renamed, an id cannot. It comes from the listing and is never typed by a human.

This is verifiable rather than aspirational: `grep -rn "studentId\|ownerId" management-frontend/src` returns nothing outside comments.

---

## 3. Integration contract

Derived from the controllers in `management/src/main/java/org/phuchoang/management/*/web/`. Roles come from `shared/security/SecurityConfig.java`.

**Base path:** `/api/v1`. **Auth:** session cookie (`JSESSIONID`).

### 3.1 Identity — `identity/web/`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/auth/login` | public | `{username, password}` | `200 {role, mustChangePassword}` · `401 Error` |
| `POST` | `/auth/password` | any authenticated | `{currentPassword, newPassword, retypeNewPassword}` | `200` (no body) |
| `GET` | `/auth/demo-accounts` | public | — | `200 [{role, username, password}]` |
| `GET` | `/staff-accounts?page&size` | `SYSTEM_ADMINISTRATOR` | — | `200 PageResponse<StaffAccountSummary>` |
| `POST` | `/staff-accounts` | `SYSTEM_ADMINISTRATOR` | `{username, role}` | `201 {username, role, initialPassword}` |
| `PATCH` | `/staff-accounts/{id}/status` | `SYSTEM_ADMINISTRATOR` | `{enabled}` | `200 {username, enabled}` |

`role` on staff creation must be one of `REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR`.

### 3.2 Student — `student/web/StudentController.java`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/students?query&page&size` | `REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR`, `STUDENT`¹ | — | `200 PageResponse<StudentSummary>` |
| `GET` | `/students/{code}` | same | — | `200 StudentDetail` |
| `GET` | `/students/{code}/initial-password` | `REGISTRAR` | — | `200 {username, initialPassword}` · `404` once changed |
| `POST` | `/students` | `REGISTRAR` | `{studentCode, firstName, lastName, email, dateOfBirth}` | `201 StudentRegistration` (**includes `username` + one-time `initialPassword`**) |
| `PUT` | `/students/{code}` | `REGISTRAR` | `{firstName, lastName, email, dateOfBirth}` | `200 StudentResponse` |
| `DELETE` | `/students/{code}` | `REGISTRAR` | — | `204` |

`StudentSummary` = `{studentCode, firstName, lastName, email}`.
`StudentDetail` = summary + `{dateOfBirth, createdAt, updatedAt}` — **no `books`/`courses` fields**; see §4.4.

¹ `COURSE_ADMINISTRATOR` holds the grant for click-through from a course roster only, and gets no Students nav item (§5).

### 3.3 Book — `book/web/BookController.java`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/books?query&ownerStudentCode&page&size` | `LIBRARIAN`, `STUDENT` | — | `200 PageResponse<BookSummary>` |
| `GET` | `/books/{isbn}` | `LIBRARIAN`, `STUDENT` | — | `200 BookDetail` |
| `POST` | `/books` | `LIBRARIAN` | `{isbn, title, author, publishedDate?, ownerStudentCode?}` | `201 BookResponse` |
| `PATCH` | `/books/{isbn}/owner` | `LIBRARIAN` | `{studentCode}` | `200 BookResponse` |
| `DELETE` | `/books/{isbn}/owner` | `LIBRARIAN` | — | `200 BookResponse` (idempotent, `ownerStudentCode: null`) |
| `DELETE` | `/books/{isbn}` | `LIBRARIAN` | — | `204` |

`ownerStudentCode` is a **student code**, never a numeric id. `BookDetail.owner` is `null` when unowned, otherwise `{studentCode, firstName, lastName, email}`.

### 3.4 Course — `course/web/CourseController.java`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/courses?query&page&size` | `REGISTRAR`, `COURSE_ADMINISTRATOR`, `STUDENT` | — | `200 PageResponse<CourseSummary>` |
| `GET` | `/courses/{code}` | same | — | `200 CourseDetail` |
| `POST` | `/courses` | `COURSE_ADMINISTRATOR` | `{courseCode, name, description?, credits}` | `201 CourseResponse` |
| `PUT` | `/courses/{code}` | `COURSE_ADMINISTRATOR` | `{name, description?, credits}` | `200 CourseResponse` |
| `DELETE` | `/courses/{code}` | `COURSE_ADMINISTRATOR` | — | `204` |

`courseCode` is immutable — `PUT` does not accept it. `CourseDetail` has **no `roster` field**; see §4.4.

### 3.5 Enrollment — `enrollment/web/EnrollmentController.java`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/enrollments?studentCode\|courseCode&page&size` | `REGISTRAR`, `COURSE_ADMINISTRATOR` | — | `200 PageResponse<EnrollmentDetail>` |
| `POST` | `/enrollments` | `REGISTRAR` | `{studentCode, courseCode}` | `201 {studentCode, courseCode, enrolledAt}` |
| `GET` | `/enrollments/{studentCode}/{courseCode}` | `REGISTRAR`, `COURSE_ADMINISTRATOR` | — | `200 {student, course, enrolledAt}` |
| `DELETE` | `/enrollments/{studentCode}/{courseCode}` | `REGISTRAR` | — | `204` |

Keyed by the **student code + course code pair**. The list endpoint requires **exactly one** filter — neither or both is a `400`, which is deliberate: with neither it would enumerate every enrollment in the system, and with both the answer is the single enrollment the item endpoint already addresses.

Both filter directions return the same row shape, which is what lets one screen render "this student's courses" and another "this course's roster" off one response.

### 3.6 Self-service — `me/web/MeController.java`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/me/profile` | `STUDENT` | — | `200 {studentCode, firstName, lastName, email, dateOfBirth}` |
| `GET` | `/me/courses?page&size` | `STUDENT` | — | `200 PageResponse<CourseSummary>` |
| `GET` | `/me/books?page&size` | `STUDENT` | — | `200 PageResponse<BookSummary>` |

`/me/profile` is the **only** way a Student learns their own `studentCode`: the login response carries just `{role, mustChangePassword}`, and this API has no session probe. Every `/me` endpoint is scoped by the session principal, never by anything the caller supplies.

### 3.7 Shared envelopes

```jsonc
// PageResponse<T>
{ "page": 0, "size": 20, "totalElements": 42, "totalPages": 3, "content": [ /* T */ ] }

// Error — every non-2xx except 400-with-field-errors
{ "timestamp": "...", "status": 409, "error": "Conflict", "message": "...", "path": "/api/v1/students" }

// ValidationError — 400
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "Validation failed",
  "path": "...", "errors": [ { "field": "email", "message": "must be a well-formed email address" } ] }
```

`page` is 0-based (default `0`); `size` defaults to `20` and is capped at `100`. Out-of-range `page` → `200` with empty `content`.

---

## 4. Four hard constraints

These properties of the running backend drive most of the design decisions in §7. Each is verified against source, not assumed.

### 4.1 Same-origin or nothing

Auth is a session cookie, and `SecurityConfig` registers **no CORS configuration**. A browser on `localhost:3000` calling `localhost:8080` cross-origin would have its cookie dropped and its preflight rejected outright.

**Resolution:** `next.config.ts` rewrites `/api/*` and `/logout` to the backend, so every request uses a relative path and stays same-origin from the browser's point of view:

```ts
async rewrites() {
  const backend = process.env.BACKEND_ORIGIN ?? 'http://localhost:8080';
  return [
    { source: '/api/:path*', destination: `${backend}/api/:path*` },
    { source: '/logout',     destination: `${backend}/logout` },
  ];
}
```

This is why the "add CORS to the backend" alternative was rejected: it would mean editing production security configuration for the sole benefit of a demo. (The previous Vite-based frontend solved the same constraint the same way, with a dev-server proxy.)

### 4.2 `403` is ambiguous

Three completely different conditions all produce a bare `403`:

| Condition | Status | Body |
| --- | --- | --- |
| Not logged in | `403` | Spring's default `Http403ForbiddenEntryPoint` — no useful body |
| Logged in, wrong role | `403` | `Error` envelope |
| Logged in, `mustChangePassword` still true | `403` | **empty** — `MustChangePasswordFilter` writes a status and returns |
| Bad username/password at login | `401` | `Error` envelope |

The anonymous case is verified by `SecurityConfigTest`, which asserts `isForbidden()` — not `isUnauthorized()` — for unauthenticated `GET`s. Only a failed *login* is `401`.

The client therefore **cannot ask the server "who am I?"**. There is no session-probe endpoint for staff roles, and a `403` cannot be decoded on its own.

**Resolution — client-side auth state is the source of truth:**

1. `AuthContext` stores `{role, username, mustChangePassword}` from the login response, mirrored into `sessionStorage` so a page refresh survives.
2. `permissions.ts` mirrors `SecurityConfig`'s per-resource rules as a capability map, so nav items and action buttons are hidden *before* any request is made. A `403` becomes an edge case rather than the normal path.
3. `RequireAuth` resolves a missing session or a pending password change against local state; a genuine wrong-role `403` renders `Forbidden`, and `ErrorBanner` distinguishes the bodyless gate `403` from an ordinary one.
4. If the server session expires while `sessionStorage` still holds state, the first `403` surfaces as a permission message. Acceptable for a demo; noted rather than engineered around.

### 4.3 Logout is not part of the API

No `/api/v1/auth/logout` exists. Spring Security's default `POST /logout` should still be registered (and CSRF is disabled, so no token is needed), but the contract does not promise it, and the must-change-password gate would `403` it for a user who has not yet changed their password.

**Resolution:** `logout()` POSTs `/logout`, **ignores any failure**, then clears `AuthContext` + `sessionStorage` and navigates to `/login`. Client state is authoritative; the server-side session is best-effort.

### 4.4 Related data is never embedded

`GET /students/{code}` returns the student record alone; `GET /courses/{code}` returns the course record alone. A student's books, a student's courses, and a course's roster are each their own paged endpoint.

This is not an omission to work around — it is the point. Each collection carries its own authorization: a Librarian opening a student profile receives that student's **books** and not their course list, a Course Administrator receives their **courses** and not their books, and a Student opening a course receives no roster at all. An embedded array could not express that, and could not be paged either.

**Resolution:** every detail screen composes the record card with whichever related table its role is entitled to (§6). The role check is `permissions.ts`, and the server enforces the same split independently.

---

## 5. Role capability matrix

The single source for nav rendering and button gating. Mirrors `SecurityConfig.filterChain`.

| Capability | REGISTRAR | LIBRARIAN | COURSE_ADMIN | STUDENT | SYSADMIN |
| --- | :-: | :-: | :-: | :-: | :-: |
| `students:read` | ✅ | ✅ | ✅¹ | ✅² | ❌ |
| `students:write` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `students:initial-password` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `books:read` | ❌ | ✅ | ❌ | ✅² | ❌ |
| `books:write` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `courses:read` | ✅ | ❌ | ✅ | ✅ | ❌ |
| `courses:write` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `enrollments:read` | ✅ | ❌ | ✅ | ❌ | ❌ |
| `enrollments:write` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `me:read` | ❌ | ❌ | ❌ | ✅ | ❌ |
| `staff:manage` | ❌ | ❌ | ❌ | ❌ | ✅ |
| `password:change` | ✅ | ✅ | ✅ | ✅ | ✅ |

¹ **Held, but not a destination.** Course Admin needs `students:read` to open a profile from a course roster, and gets no Students nav item. This is the one case where capability and navigation deliberately differ — which is why `NAV_ITEMS` carries an explicit role list rather than deriving visibility from a capability. A capability-driven nav cannot express "reachable, but not somewhere you browse to".

² Transparently scoped server-side to the caller's own records — a Student searching students gets 0 or 1 rows, and another student's detail returns `403`. **The UI needs no special-casing for this**; it is the server's job and it already does it. The Student screens use `/me/*` anyway, which is scoped by session rather than by a code.

**`SYSTEM_ADMINISTRATOR` is denied every domain read**, so its nav shows exactly two items: Staff Accounts and Change Password — the RBAC allow-list made visible.

---

## 6. Screen map

| Route | Visible to | Behaviour, per role |
| --- | --- | --- |
| `/login` | public | `GET /auth/demo-accounts` chips, `POST /auth/login`. UC-21 |
| `/change-password` | any (forced) | `POST /auth/password`. The one route the must-change gate allows. UC-22 |
| `/students` | Registrar, Librarian, Student | **Student:** `GET /me/profile` rendered as a card — no list, no search. **Registrar / Librarian:** search + table + pagination over `GET /students`; Registrar also gets register / edit / delete. UC-1, 2, 3, 13, 16 |
| `/students/[code]` | Registrar, Librarian, Course Admin | Record card, plus: **Librarian** → books on loan (`GET /books?ownerStudentCode=`); **Registrar / Course Admin** → enrolled courses (`GET /enrollments?studentCode=`), each row linking to the course. Registrar also gets the initial-password reveal. UC-17, 23 |
| `/books` | Librarian, Student | **Student:** `GET /me/books`. **Librarian:** search + table + add / delete. UC-4, 7, 14, 16 |
| `/books/[isbn]` | Librarian, Student | Record card; Librarian additionally gets assign (by student code) and release. UC-5, 6, 18 |
| `/courses` | Registrar, Course Admin, Student | **Student:** `GET /me/courses` — enrolled only. **Registrar:** read-only catalogue. **Course Admin:** catalogue + create / edit / delete. UC-8, 9, 10, 15, 16 |
| `/courses/[code]` | Registrar, Course Admin, Student | Record card; **Registrar / Course Admin** additionally get the roster (`GET /enrollments?courseCode=`), each row linking to that student. UC-19 |
| `/enrollments` | Registrar, Course Admin | **Registrar:** type a student code → their courses → enroll / end. **Course Admin:** course list → a course → its roster → a student's profile. UC-11, 12, 20 |
| `/staff-accounts` | SysAdmin | List, create, deactivate / reactivate. UC-24, 25 |

**Coverage: all 25 use cases.** Several UCs (search + detail, create + update + delete) share one screen, and UC-16 is spread across the Student's three tabs rather than living on a page of its own.

### 6.1 Screen anatomy

Three layouts cover every screen:

**List screen** — search input (debounced 300ms) → `DataTable` → `Pagination`. A "New…" button in the header for roles with write access; row actions gated by capability. Rows are clickable *and* keyboard-activatable, because every drill-down in this app is a row click.

**Detail screen** — a `RecordCard` definition list, plus zero, one, or two related tables chosen by role (§4.4).

**Action screen** (`/enrollments` for a Registrar, `/staff-accounts`) — a small form driving a list, because the work starts with typing a key rather than browsing.

---

## 7. Project structure and cross-cutting patterns

```
management-frontend/
├── next.config.ts                 # /api + /logout rewrites → :8080
├── tsconfig.json  package.json
└── src/
    ├── app/
    │   ├── layout.tsx             # server shell
    │   ├── providers.tsx          # 'use client': Emotion registry + Chakra + next-themes + AuthProvider
    │   ├── emotion-registry.tsx    # SSR style registry — see §7.5
    │   ├── page.tsx               # per-role landing redirect
    │   ├── login/page.tsx
    │   ├── change-password/page.tsx  # outside (app) on purpose — see §7.3
    │   └── (app)/                 # everything behind RequireAuth + AppShell
    │       ├── layout.tsx
    │       ├── students/page.tsx        students/[code]/page.tsx
    │       ├── books/page.tsx           books/[isbn]/page.tsx
    │       ├── courses/page.tsx         courses/[code]/page.tsx
    │       ├── enrollments/page.tsx
    │       └── staff-accounts/page.tsx
    ├── components/
    │   ├── AppShell.tsx    DataTable.tsx     Pagination.tsx   PageHeader.tsx
    │   ├── RecordCard.tsx  SearchInput.tsx   ErrorBanner.tsx  Forbidden.tsx
    │   ├── FormDialog.tsx  ConfirmDialog.tsx FormField.tsx
    │   │   StudentFormDialog.tsx  CourseFormDialog.tsx  BookFormDialog.tsx
    │   ├── ui/      { Button, SurfaceCard, RoleBadge, Tooltip, EmptyState, ThemeToggle }
    │   └── motion/  { motion-config, FadeIn, Stagger, PageTransition, Reveal } — §7.6
    ├── lib/api/    { client.ts, endpoints.ts, types.ts }
    ├── lib/auth/   { AuthContext.tsx, RequireAuth.tsx, permissions.ts }
    ├── lib/hooks/  { usePagedResource.ts, useResource.ts, useAsyncAction.ts }
    └── theme/system.ts
```

### 7.1 API client — `src/lib/api/client.ts`

One wrapper, one error type. Every call goes through it.

Responsibilities kept **in** the client: JSON encode/decode, `204`-empty handling, bodyless-`403` tolerance, `ApiError` normalisation, and `ApiError.fieldError(name)` — the single hook that lets any form render inline validation from `ValidationError.errors[]`.

Responsibilities kept **out**: the redirect decision for `403`. That belongs to `RequireAuth` and the page, which know the local auth state (§4.2).

### 7.2 Endpoints and types — `src/lib/api/`

`endpoints.ts` has one named function per operation in §3, so no component ever writes a URL string. `types.ts` transcribes the response DTOs. Together they *are* the client-side copy of the contract — when the backend changes, exactly these two files move.

`types.ts` is also where §2.4 is enforced: the types have no `id` field, so a component cannot reach for one.

### 7.3 Auth — `src/lib/auth/`

`AuthContext` exposes `{session, ready, login, logout, clearMustChange, clearSession}`. `ready` matters under the App Router: the first render happens on the server, where `sessionStorage` does not exist, so without it an authenticated user would be bounced to `/login` on every refresh.

The stored session is read in an **effect**, never during render. `sessionStorage` is browser-only, so a read during render makes the client's first committed tree differ from the server's (which always has `ready = false`) and hydration fails outright — React discards the server HTML and re-renders everything. Because `RequireAuth` renders a spinner whenever `!ready`, the pre-hydration frame is exactly the markup the server sent, and the effect costs one frame rather than correctness.

`RequireAuth` applies three rules in order: no session → `/login`; `mustChangePassword` and not on that page → `/change-password`; capability not held → `Forbidden`. Rule 2 is the client-side mirror of `MustChangePasswordFilter` — it exists so the forced-change flow *feels* like a flow rather than a wall of failed requests.

Rule 2 is also why `/change-password` sits **outside** the `(app)` route group, next to `/login`. Inside the group its parent layout would apply a plain `<RequireAuth>` — which, for a session with `mustChangePassword = true`, resolves to "redirect to `/change-password`" and renders a spinner instead of its children. The page would redirect to the route it is already on and never render, leaving an account provisioned by `User.provisionStaff` unable to clear the flag the backend gates every other endpoint on. Outside the group the page's own `<RequireAuth allowDuringPasswordChange>` is the only guard, and a forced user gets the bare centred form rather than a sidebar full of links `MustChangePasswordFilter` would `403`.

### 7.4 Hooks

`usePagedResource(fetcher, {enabled})` — owns `{query, page, data, loading, error}`, debounces `query`, resets `page` on a query change, and exposes `refetch()`. `enabled` is what lets `/enrollments` hold its fetch until a student code has actually been submitted.

`useResource(fetcher, deps)` — the single-record equivalent, for detail screens.

`useAsyncAction(fn)` — `{run, pending, error, reset}` for form submits, with `error` narrowed to `ApiError` so a form renders `error.fieldError('email')` under the field and `<ErrorBanner>` above it.

### 7.5 Styling

Chakra's default system with a `defineConfig` overlay (`theme/system.ts`) that implements [02-Japanese-Zen-Design.md](02-Japanese-Zen-Design.md) — see §7.6. `next-themes` drives light/dark from the OS, with a toggle in the topbar. Still no stylesheet of our own: the whole design system is tokens and recipes.

Chakra styles through Emotion, which has no first-class App Router support yet, so `app/emotion-registry.tsx` supplies the registry Next's CSS-in-JS guide prescribes: a cache with `compat = true` (which makes Emotion's server branch cache rules and return `null` instead of emitting a `<style>` element inline), plus `useServerInsertedHTML` to re-emit those rules into `<head>`. Without it every SSR page ships ~50 KB of `<style>` tags inside `<body>` that the client never renders — tags React has to reconcile away on hydration, and which are what a hydration mismatch elsewhere in the tree ends up being reported against. The `data-emotion` attributes it writes are load-bearing: Emotion's browser code looks up `style[data-emotion="css-global <name>"]` to adopt a node instead of duplicating it, and `createCache` scans `style[data-emotion]` to learn which rules are already in the document.

A role `Badge` in the topbar makes "who am I logged in as" readable at a glance — which matters more than it sounds during a role-switching demo. It is deliberately *not* colour-keyed per role any more: five hues is four accents more than §5 of the Zen spec allows, and the label already says "Registrar".

### 7.6 The Zen design system

[02-Japanese-Zen-Design.md](02-Japanese-Zen-Design.md) is the visual authority; `theme/system.ts` is its implementation. Four things are worth knowing before editing either:

**One ramp re-colours everything.** Chakra derives `bg`, `fg`, `border`, and every neutral surface from `colors.gray.*`. The overlay repoints that ramp at `sumi`, a warm ink neutral whose 50 and 200 steps are the spec's `#F8F8F6` background and `#E5E5E5` border verbatim. Only `fg`, `bg.panel`, `bg.subtle` and `border` need an explicit override on top.

**The accent cannot be used for text.** The spec's `#6B8E7A` is 3.3:1 on white and fails WCAG AA. `matcha.solid` therefore resolves to the 700 step (7.3:1), and the nominal accent is reserved for fills, borders, and indicators. Do not "correct" it back to 500.

**Heavy weights are unreachable.** §4 permits 400/500/600, so `fontWeights.bold`, `.extrabold` and `.black` are all aliased to 600 rather than deleted — a recipe reaching for `bold` lands inside the spec instead of breaking.

**Motion has one vocabulary.** `components/motion/motion-config.ts` owns every duration and easing in the app, and `useZenTransition()` returns a zero-duration transition under `prefers-reduced-motion`. There are no inline `transition={{...}}` objects anywhere in `src/` — that is what keeps §8's ban on bounce, spring, rotation and zoom enforceable rather than aspirational.

Three primitives are new and should be reached for before composing Chakra directly: `ui/Button.tsx` (which is why no page passes `size="xs"`), `ui/SurfaceCard.tsx` (the one card), and `ui/EmptyState.tsx` (§13's icon + explanation + action).

---

## 8. Error handling

| Status | Meaning here | UI |
| --- | --- | --- |
| `400` | Validation, or an unresolvable business key | Inline under each field via `ApiError.fieldError(field)`; `ErrorBanner` for the summary |
| `401` | Bad credentials (login), wrong current password (change password) | Inline on the form |
| `403` | Anonymous / wrong role / must-change | Resolved against local state — §4.2 |
| `404` | Not found, **or** initial password already changed | Detail pages: empty state. Initial-password: the deliberate information-hiding message, worded as "already chosen their own" rather than "not found" |
| `409` | Duplicate code / ISBN / email / enrollment | `ErrorBanner` with the server's `message` verbatim — it is already user-readable |
| `5xx` | Unexpected | Generic banner + `console.error` |

The `409` row matters for the demo: the backend's conflict messages are specific ("Student code 'S001' is already in use."), so passing `message` straight through is both the least code and the best output.

---

## 9. Build order

| Phase | Work | Est. |
| --- | --- | --- |
| 1 | Scaffold Next + TS + Chakra; rewrites; `client.ts` + `endpoints.ts` + `types.ts`; `AuthContext` + `RequireAuth` + `permissions.ts`; login + change-password; `AppShell` with role-filtered nav | 3h |
| 2 | `DataTable`, `Pagination`, `RecordCard`, `FormDialog`, `ConfirmDialog`, `FormField`, `ErrorBanner`; the three hooks; theme | 2h |
| 3 | Students (both shapes, detail with role-branching related tables) + Books | 3h |
| 4 | Courses (both shapes, detail with roster) + Enrollments (both shapes) | 3h |
| 5 | Staff accounts; demo-account chips; typecheck + build pass | 2h |

**~13 hours.** Phases 1–2 are the load-bearing ones; 3–5 are repetitions of the same two patterns.

**Order rationale:** Phase 1 ends with a runnable app that can log in and switch roles — the riskiest integration (the cookie through the rewrite proxy) is proven first, before any screen is built on top of it.

---

## 10. Demo script

The run-through that exercises all 5 roles and ties the modules into one story.

**Start:**
```bash
make up                                    # MySQL
cd management && ./mvnw spring-boot:run    # :8080, seeds the 4 staff demo accounts
cd management-frontend && npm run dev      # :3000
```

| # | Role | Action | Demonstrates |
| --- | --- | --- | --- |
| 1 | — | Open `/login`; the seeded accounts are listed live from `GET /auth/demo-accounts` | PM-017 |
| 2 | `demo.registrar` | Register a student — the one-time `initialPassword` is shown; re-read it from the detail page | UC-1, UC-23 |
| 3 | `demo.courseadmin` | Create course `CS101` | UC-8 |
| 4 | `demo.registrar` | Enrollments tab → type the **student code** → enroll in `CS101`. Note there is no id to look up anywhere, and no Books item in the nav | UC-11, §2.4 |
| 5 | `demo.librarian` | Add a book, assign it by student code; then Students → that student → **their books on loan**. Note: no Courses, no Enrollments in the nav | UC-4, UC-5, UC-17 |
| 6 | `demo.courseadmin` | Enrollments → `CS101` → **the roster** → click the student → their profile. Note there is no Students nav item — the roster is the only way in | UC-19, UC-17, §5 ¹ |
| 7 | **the new student** | Log in (username = their email) → **forced** to `/change-password` → then: Students shows *their own record directly*, Courses shows only `CS101`, Books shows the assigned book, and there is no Enrollments tab at all | UC-21, UC-22, UC-16 |
| 8 | `demo.sysadmin` | Create a staff account (initial password shown once), then deactivate it | UC-24, UC-25 |
| 9 | `demo.sysadmin` | Note the nav shows only Staff Accounts — a domain read returns `403` | RBAC allow-list |

Steps 2→7 are the narrative spine: a student created by one role, given a course by a second and a book by a third, then logging in as themselves and seeing exactly their own half of each. Steps 4–6 are the per-resource RBAC in action; step 7 is both the must-change-password gate and the Student's narrowed surface.

**Reset between runs:** `make reset`. Demo-account seeding is idempotent, so a restart never resets a password changed during a demo.

---

## 11. Verification checklist

Run against a live stack after implementation:

- [ ] `npm run typecheck` and `npm run build` both pass.
- [ ] Login sets `JSESSIONID` through the rewrite — DevTools → Application → Cookies shows it on `localhost:3000`.
- [ ] A hard refresh on any deep route keeps the session, with no flash of the login screen.
- [ ] A newly registered student is blocked on **every** route until the password is changed, and released immediately after — no re-login.
- [ ] The same holds for a staff account a SysAdmin just created: signing in lands on the bare `/change-password` form (not a spinner), and submitting it releases the session.
- [ ] A hard reload of `/change-password` and of one deep route logs **no** hydration error and no "Encountered a script tag" warning — check in a clean profile, since browser extensions produce look-alike warnings.
- [ ] View source on that reload: the `data-emotion` `<style>` tags are in `<head>` and there are none in `<body>`.
- [ ] Each role's sidebar matches §5 exactly: Registrar has no Books, Librarian has no Courses or Enrollments, Course Admin has no Students, Student has no Enrollments, SysAdmin has two items.
- [ ] Registrar `GET /api/v1/books` → `403`; Librarian `GET /api/v1/courses` → `403`; Student `GET /api/v1/enrollments?studentCode=…` → `403`.
- [ ] The Student's Students tab renders their own record with no list and no search box.
- [ ] A Librarian opening a student sees books and **no** course list; a Registrar opening the same student sees courses and **no** book list.
- [ ] A Course Administrator reaches a student profile only by clicking a course roster row.
- [ ] `GET /api/v1/enrollments` with no filter → `400`; with both filters → `400`.
- [ ] A duplicate student code surfaces the backend's `409` message in `ErrorBanner`; an invalid email surfaces the `400` `errors[]` entry inline under the email field.
- [ ] Pagination: with >20 students, page 2 loads and `totalPages` is respected.
- [ ] `grep -rn "studentId\|ownerId" management-frontend/src` returns nothing outside comments.
- [ ] Logout clears local state and returns to `/login` even if `POST /logout` fails.

---

## 12. References

- [BA-docs/use-cases.md](../BA-docs/use-cases.md) — the 25 UCs each screen covers
- [BA-docs/user-stories.md](../BA-docs/user-stories.md) — acceptance criteria
- [SA-docs/api-specification.md](../SA-docs/api-specification.md) — the contract; §3 above is the as-built version
- [SA-docs/02-component-diagram.md](../SA-docs/02-component-diagram.md) §4 — what each per-resource read grant is *for*
- [SA-docs/04-authentication-authorization.md](../SA-docs/04-authentication-authorization.md) §6.1 — the read matrix §5 above mirrors
- `management/src/main/java/org/phuchoang/management/shared/security/SecurityConfig.java` — the authoritative RBAC rules
