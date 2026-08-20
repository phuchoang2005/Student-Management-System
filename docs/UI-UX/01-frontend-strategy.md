# Frontend Strategy — Demo UI

UI-UX Documentation — Part 1 of 1.

A build strategy for a **demo-grade frontend** over the completed backend in [`management/`](../../management/). It introduces no new business rules, roles, or endpoints: every screen below maps onto a use case already specified in [BA-docs/use-cases.md](../BA-docs/use-cases.md) and an endpoint already implemented and covered by tests.

Unlike [SA-docs/api-specification.md](../SA-docs/api-specification.md), which was hand-authored *before* any backend existed, this document is derived **from the shipped source**. Where the running code and the specification differ, the code is treated as authoritative and the difference is called out explicitly (§4, §9).

---

## 1. Purpose and scope

### 1.1 Why a frontend at all

The backend is feature-complete — all 4 sprints merged, 22 user stories, 25 use cases, RBAC and the must-change-password gate enforced and integration-tested. None of that is *visible*. The purpose of this frontend is to make the finished system demonstrable: a person sits down, logs in as each of the 5 roles in turn, and walks the use cases end to end in a browser.

That is the whole goal. It is **not** a production UI, and the strategy below trades polish for coverage deliberately.

### 1.2 In scope

- All 5 roles: `REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR`, `STUDENT`, `SYSTEM_ADMINISTRATOR`.
- Every implemented endpoint reachable from some screen (§3 is the full inventory).
- The two cross-cutting behaviours that are the most interesting things this backend does: **role-based access control** and the **forced initial password change**.
- An end-to-end demo script (§10) that ties the five modules into one narrative.

### 1.3 Out of scope

Named explicitly so their absence reads as a decision, not an oversight:

| Excluded | Why |
| --- | --- |
| TypeScript | Requested stack is plain JavaScript. |
| A component library | See §2.2. |
| Automated frontend tests | The backend already carries the test burden ([Testing/](../Testing/)); duplicating it here buys nothing for a demo. |
| Production build / deployment | The demo runs on the Vite dev server. |
| Responsive / mobile layout | Demoed on a laptop. A single desktop breakpoint. |
| i18n, accessibility audit, dark mode | Not on the demo path. |
| Optimistic-locking UI (PM-012) | The backend implements it, but no endpoint currently surfaces a version token to the client. |

---

## 2. Stack

### 2.1 The choice

| Layer | Choice |
| --- | --- |
| Markup | HTML5 — one `index.html` shell, single `#root` mount |
| Styling | CSS3 — custom properties for tokens, plain class selectors, two stylesheets |
| Framework | React 19, plain JSX (`.jsx`, no TypeScript) |
| Build/dev | Vite 7 + `@vitejs/plugin-react` |
| Routing | `react-router-dom` 7 |
| Data fetching | Native `fetch`, wrapped once in `src/api/client.js` |
| State | React Context (`AuthContext`) + local component state |

**Total dependency count: 3 runtime, 2 dev.** No UI kit, no state library, no data-fetching library, no CSS framework.

### 2.2 Why no component library

The screens this demo needs are tables, forms, and modals — roughly a dozen components, none of them novel. A component library would add setup cost, a theming layer to learn, and bundle weight, in exchange for widgets we can write in ~200 lines of CSS3 (§7.5). The backend is the artifact being demoed; every hour spent configuring a design system is an hour not spent on the demo path.

*(An earlier draft of this strategy specified Kuma UI. It was dropped: Kuma UI provides styling primitives — `Box`, `Flex`, `Text`, `styled` — but no table, modal, select, or toast, so it would have added a dependency without removing any of the components we actually have to write.)*

### 2.3 Why React state and not a data library

Every screen in §6 follows one of two patterns: *fetch a page and render it*, or *submit a form and show the result*. Two hooks (`usePagedResource`, `useAsyncAction`, §7.4) cover both. There is no cache invalidation problem worth a library here — after any write, the list simply refetches.

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
| `POST` | `/staff-accounts` | `SYSTEM_ADMINISTRATOR` | `{username, role}` | `201 {username, role, initialPassword}` |
| `PATCH` | `/staff-accounts/{id}/status` | `SYSTEM_ADMINISTRATOR` | `{enabled}` | `200 {username, enabled}` |

`role` on staff creation must be one of `REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR` — `Role.STAFF_ROLES` rejects `SYSTEM_ADMINISTRATOR` and `STUDENT`.

### 3.2 Student — `student/web/StudentController.java`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/students?query&page&size` | `REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR`, `STUDENT` | — | `200 PageResponse<StudentSummary>` |
| `GET` | `/students/{code}` | same | — | `200 StudentDetail` |
| `GET` | `/students/{code}/initial-password` | `REGISTRAR` | — | `200 {username, initialPassword}` · `404` once changed |
| `POST` | `/students` | `REGISTRAR` | `{studentCode, firstName, lastName, email, dateOfBirth}` | `201 StudentRegistration` (**includes `username` + one-time `initialPassword`**) |
| `PUT` | `/students/{code}` | `REGISTRAR` | `{firstName, lastName, email, dateOfBirth}` | `200 StudentResponse` |
| `DELETE` | `/students/{code}` | `REGISTRAR` | — | `204` |

`StudentSummary` = `{id, studentCode, firstName, lastName, email}`.
`StudentDetail` = summary + `{dateOfBirth, createdAt, updatedAt, books[], courses[]}` — see §9 for `books`/`courses`.

### 3.3 Book — `book/web/BookController.java`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/books?query&owner&page&size` | 4 domain roles | — | `200 PageResponse<BookSummary>` |
| `GET` | `/books/{isbn}` | 4 domain roles | — | `200 BookDetail` |
| `POST` | `/books` | `LIBRARIAN` | `{isbn, title, author, publishedDate?, ownerId?}` | `201 BookResponse` |
| `PATCH` | `/books/{isbn}/owner` | `LIBRARIAN` | `{studentId}` | `200 BookResponse` |
| `DELETE` | `/books/{isbn}/owner` | `LIBRARIAN` | — | `200 BookResponse` (idempotent, `ownerId: null`) |
| `DELETE` | `/books/{isbn}` | `LIBRARIAN` | — | `204` |

`owner` is a **numeric student id**, not a student code. `BookDetail.owner` is `null` when unowned, otherwise `{id, studentCode, firstName, lastName, email}`.

### 3.4 Course — `course/web/CourseController.java`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/courses?query&page&size` | 4 domain roles | — | `200 PageResponse<CourseSummary>` |
| `GET` | `/courses/{code}` | 4 domain roles | — | `200 CourseDetail` |
| `POST` | `/courses` | `COURSE_ADMINISTRATOR` | `{courseCode, name, description?, credits}` | `201 CourseResponse` |
| `PUT` | `/courses/{code}` | `COURSE_ADMINISTRATOR` | `{name, description?, credits}` | `200 CourseResponse` |
| `DELETE` | `/courses/{code}` | `COURSE_ADMINISTRATOR` | — | `204` |

`courseCode` is immutable — `PUT` does not accept it.

### 3.5 Enrollment — `enrollment/web/EnrollmentController.java`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/enrollments` | `REGISTRAR` | `{studentId, courseCode}` | `201 {id, studentId, courseCode, enrolledAt}` |
| `GET` | `/enrollments/{studentId}/{courseCode}` | 4 domain roles | — | `200 {student, course, enrolledAt}` |
| `DELETE` | `/enrollments/{studentId}/{courseCode}` | `REGISTRAR` | — | `204` |

Keyed by the **student id + course code pair**, not by an enrollment id.

### 3.6 Self-service — `me/web/MeController.java`

| Method | Path | Roles | Request | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/me/books-and-courses` | `STUDENT` | `booksPage`, `booksSize`, `coursesPage`, `coursesSize` | `200 {books: PageResponse, courses: PageResponse}` |

The only endpoint with **prefixed** paging params — it composes two independently paged collections, and Spring resolves only one `page`/`size` pair per request.

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

`page` is 0-based (default `0`); `size` defaults to `20` and is capped at `100`. Out-of-range `page` → `200` with empty `content`; invalid `page`/`size` → `400`.

---

## 4. Three hard constraints

These three properties of the running backend drive most of the design decisions in §7. Each is verified against source, not assumed.

### 4.1 Same-origin or nothing

Auth is a session cookie, and `SecurityConfig` registers **no CORS configuration**. A browser on `localhost:5173` calling `localhost:8080` cross-origin would have its cookie dropped, and the preflight rejected outright.

**Resolution:** `vite.config.js` proxies `/api` to `http://localhost:8080`. Every request uses a relative path (`/api/v1/students`), so from the browser's perspective there is one origin and the cookie is unremarkable.

```js
// vite.config.js
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api':    { target: 'http://localhost:8080', changeOrigin: true },
      '/logout': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
```

This is why the "add CORS to the backend" alternative was rejected: it would mean editing production security configuration for the sole benefit of a demo.

### 4.2 `403` is ambiguous

Three completely different conditions all produce a bare `403`:

| Condition | Status | Body |
| --- | --- | --- |
| Not logged in | `403` | Spring's default `Http403ForbiddenEntryPoint` — no useful body |
| Logged in, wrong role | `403` | `Error` envelope |
| Logged in, `mustChangePassword` still true | `403` | **empty** — `MustChangePasswordFilter` writes a status and returns |
| Bad username/password at login | `401` | `Error` envelope |

The anonymous case is verified by `SecurityConfigTest.java:59-62`, which asserts `isForbidden()` — not `isUnauthorized()` — for unauthenticated `GET`s. Only a failed *login* is `401`.

The client therefore **cannot ask the server "who am I?"**. There is no session-probe endpoint for staff roles, and a `403` cannot be decoded on its own.

**Resolution — client-side auth state is the source of truth:**

1. `AuthContext` stores `{role, mustChangePassword}` from the login response, mirrored into `sessionStorage` so a page refresh survives.
2. `permissions.js` mirrors `SecurityConfig`'s rules as a capability map, so nav items and action buttons are hidden *before* any request is made. A `403` becomes an edge case rather than the normal path.
3. When a `403` does arrive, `client.js` resolves it against local state:
   - no stored session → redirect to `/login`
   - `mustChangePassword === true` → redirect to `/change-password`
   - otherwise → render "You don't have permission for this action."
4. If the server session has expired but `sessionStorage` still holds state, the first `403` clears it and drops the user to `/login`. Acceptable for a demo; noted rather than engineered around.

### 4.3 Logout is not part of the API

No `/api/v1/auth/logout` exists — `grep -rni logout` finds nothing in `management/src` or the SA docs. Spring Security's default `POST /logout` should still be registered (and CSRF is disabled, so no token is needed), but the contract does not promise it, and the must-change-password gate would `403` it for a user who has not yet changed their password.

**Resolution:** `logout()` POSTs `/logout`, **ignores any failure**, then clears `AuthContext` + `sessionStorage` and navigates to `/login`. Client state is authoritative; the server-side session is best-effort.

---

## 5. Role capability matrix

The single source for nav rendering and button gating. Mirrors `SecurityConfig.filterChain`.

| Capability | REGISTRAR | LIBRARIAN | COURSE_ADMIN | STUDENT | SYSADMIN |
| --- | :-: | :-: | :-: | :-: | :-: |
| Read students / books / courses / enrollments | ✅ | ✅ | ✅ | ✅¹ | ❌ |
| Write students | ✅ | ❌ | ❌ | ❌ | ❌ |
| View a student's initial password | ✅ | ❌ | ❌ | ❌ | ❌ |
| Write enrollments | ✅ | ❌ | ❌ | ❌ | ❌ |
| Write books / assign ownership | ❌ | ✅ | ❌ | ❌ | ❌ |
| Write courses | ❌ | ❌ | ✅ | ❌ | ❌ |
| `GET /me/books-and-courses` | ❌ | ❌ | ❌ | ✅ | ❌ |
| Manage staff accounts | ❌ | ❌ | ❌ | ❌ | ✅ |
| Change own password | ✅ | ✅ | ✅ | ✅ | ✅ |

¹ Transparently scoped server-side to the caller's own records — a Student searching students gets 0 or 1 rows, and another student's detail page returns `403`. **The UI needs no special-casing for this**; it is the server's job and it already does it.

**`SYSTEM_ADMINISTRATOR` is denied every domain read.** This is a deliberate allow-list in `SecurityConfig`, not an oversight — so the sysadmin's nav shows exactly two items: Staff Accounts, and Change Password.

---

## 6. Screen map

| Route | Visible to | Endpoints used | Covers |
| --- | --- | --- | --- |
| `/login` | public | `GET /auth/demo-accounts`, `POST /auth/login` | UC-21 |
| `/change-password` | any (forced) | `POST /auth/password` | UC-22 |
| `/students` | 4 domain roles | `GET /students`; Registrar: `POST`, `PUT`, `DELETE` | UC-1, 2, 3, 13 |
| `/students/:code` | 4 domain roles | `GET /students/{code}`, `GET /books?owner=`, Registrar: `GET .../initial-password` | UC-17, 23 |
| `/books` | 4 domain roles | `GET /books`; Librarian: `POST`, `PATCH/DELETE .../owner`, `DELETE` | UC-4, 5, 6, 7, 14 |
| `/books/:isbn` | 4 domain roles | `GET /books/{isbn}` | UC-18 |
| `/courses` | 4 domain roles | `GET /courses`; Course Admin: `POST`, `PUT`, `DELETE` | UC-8, 9, 10, 15 |
| `/courses/:code` | 4 domain roles | `GET /courses/{code}` | UC-19 |
| `/enrollments` | 4 domain roles (lookup); Registrar (write) | `GET/POST/DELETE /enrollments` | UC-11, 12, 20 |
| `/me` | STUDENT | `GET /me/books-and-courses` | UC-16 |
| `/staff-accounts` | SYSADMIN | `POST /staff-accounts`, `PATCH .../status` | UC-24, 25 |

**Coverage: all 25 use cases.** Every numbered UC in [use-cases.md](../BA-docs/use-cases.md) has a screen that reaches it — 11 routes, because several UCs (search + detail, create + update + delete) share one screen.

### 6.1 Screen anatomy

Three layouts cover every screen:

**List screen** — search input (debounced 300ms) → `DataTable` → `Pagination`. A "New…" button in the header for roles with write access. Row actions (Edit / Delete) rendered per-row, gated by capability.

**Detail screen** — a definition-list card of the record's fields, plus related-data sections beneath.

**Action screen** (`/enrollments`, `/staff-accounts`) — small forms rather than lists, because neither resource has a list endpoint.

---

## 7. Project structure and cross-cutting patterns

```
management-frontend/
├── index.html                     # HTML5 shell, single #root
├── vite.config.js                 # /api + /logout proxy → :8080
├── package.json
└── src/
    ├── main.jsx                   # createRoot + BrowserRouter + AuthProvider
    ├── App.jsx                    # route table
    ├── styles/
    │   ├── tokens.css             # CSS3 custom properties: color, space, radius, type
    │   └── base.css               # reset + component classes
    ├── api/
    │   ├── client.js              # fetch wrapper → ApiError
    │   └── endpoints.js           # one function per endpoint, grouped by module
    ├── auth/
    │   ├── AuthContext.jsx        # {role, mustChangePassword} + login/logout
    │   ├── RequireAuth.jsx        # route guard + must-change redirect
    │   └── permissions.js         # §5 matrix as code
    ├── hooks/
    │   ├── usePagedResource.js    # search + page state for any PageResponse endpoint
    │   └── useAsyncAction.js      # submit state: pending / error / success
    ├── components/
    │   ├── AppShell.jsx           # sidebar (role-filtered) + topbar + <Outlet/>
    │   ├── DataTable.jsx  Pagination.jsx  EmptyState.jsx
    │   ├── Modal.jsx      Field.jsx       ErrorBanner.jsx
    │   └── Toast.jsx      Badge.jsx       ConfirmDialog.jsx
    └── pages/
        ├── LoginPage.jsx  ChangePasswordPage.jsx  ForbiddenPage.jsx
        ├── students/   StudentListPage.jsx  StudentDetailPage.jsx  StudentFormModal.jsx
        ├── books/      BookListPage.jsx     BookDetailPage.jsx     BookFormModal.jsx
        ├── courses/    CourseListPage.jsx   CourseDetailPage.jsx   CourseFormModal.jsx
        ├── enrollments/EnrollmentPage.jsx
        ├── me/         MyBooksAndCoursesPage.jsx
        └── staff/      StaffAccountsPage.jsx
```

### 7.1 API client — `src/api/client.js`

One wrapper, one error type. Every call goes through it.

```js
export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? `Request failed (${status})`);
    this.status = status;
    this.errors = body?.errors ?? [];      // ValidationError field errors
  }
  fieldError(name) {
    return this.errors.find((e) => e.field === name)?.message;
  }
}

async function request(method, path, { body, params } = {}) {
  const url = params ? `${path}?${new URLSearchParams(clean(params))}` : path;
  const res = await fetch(url, {
    method,
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return null;
  const payload = await res.json().catch(() => null);   // 403 gate has no body
  if (!res.ok) throw new ApiError(res.status, payload);
  return payload;
}
```

Responsibilities kept **in** the client: JSON encode/decode, `204`-empty handling, bodyless-`403` tolerance, `ApiError` normalisation, and `ApiError.fieldError(name)` — the single hook that lets any form render inline validation from `ValidationError.errors[]`.

Responsibilities kept **out**: the redirect decision for `403`. That belongs to `RequireAuth` and the page, which know the local auth state (§4.2).

### 7.2 Endpoints — `src/api/endpoints.js`

One named function per operation in §3, so no component ever writes a URL string:

```js
export const students = {
  search:  (query, page, size = 20) => request('GET', '/api/v1/students', { params: { query, page, size } }),
  get:     (code) => request('GET', `/api/v1/students/${encodeURIComponent(code)}`),
  initialPassword: (code) => request('GET', `/api/v1/students/${encodeURIComponent(code)}/initial-password`),
  register:(body) => request('POST', '/api/v1/students', { body }),
  update:  (code, body) => request('PUT', `/api/v1/students/${encodeURIComponent(code)}`, { body }),
  remove:  (code) => request('DELETE', `/api/v1/students/${encodeURIComponent(code)}`),
};
```

Same shape for `books`, `courses`, `enrollments`, `me`, `auth`, `staffAccounts`. This file *is* the client-side copy of the contract — when the backend changes, exactly one file moves.

### 7.3 Auth — `src/auth/`

`AuthContext` exposes `{ session, login, logout, clearMustChange }` where `session` is `{role, mustChangePassword} | null`, persisted to `sessionStorage`.

`RequireAuth` wraps every protected route and applies three rules in order:

1. no `session` → `<Navigate to="/login" />`
2. `session.mustChangePassword` and route ≠ `/change-password` → `<Navigate to="/change-password" />`
3. route's required capability not in `permissions[session.role]` → `<ForbiddenPage />`

Rule 2 is the client-side mirror of `MustChangePasswordFilter`. It exists so the forced-change flow *feels* like a flow rather than a wall of failed requests — the server enforcement remains the real guarantee.

On a successful `POST /auth/password`, `clearMustChange()` flips the local flag. The backend does the same thing to the live session in `AuthController.clearMustChangePassword`, so no re-login is needed on either side.

### 7.4 Hooks

`usePagedResource(fetcher)` — owns `{query, page, data, loading, error}`, debounces `query`, resets `page` to 0 on a query change, and exposes `refetch()` for after a write. Drives **all four** list screens plus both halves of `/me`, since every one of them returns the same `PageResponse` envelope.

`useAsyncAction(fn)` — owns `{run, pending, error, reset}` for form submits. Its `error` is an `ApiError`, so a form renders `error.fieldError('email')` under the field and `<ErrorBanner error={error}/>` above it. Together these give every form inline `400` validation and a top-level `409`/`404` message without per-form code.

### 7.5 Styling

`tokens.css` defines the palette, spacing scale, radii, and type scale as custom properties; `base.css` consumes them. Components take semantic class names (`.card`, `.data-table`, `.btn`, `.btn--danger`, `.badge`, `.field__error`). Two files, no build step beyond Vite's own CSS handling.

A role `Badge` in the topbar — colour-keyed per role — makes "who am I logged in as" readable at a glance, which matters more than it sounds during a role-switching demo.

---

## 8. Error handling

| Status | Meaning here | UI |
| --- | --- | --- |
| `400` | Validation | Inline under each field via `ApiError.fieldError(field)`; `ErrorBanner` for the summary |
| `401` | Bad credentials (login only) | Inline message on the login form |
| `403` | Anonymous / wrong role / must-change | Resolved against local state — §4.2 |
| `404` | Not found, **or** initial password already changed | Detail pages: `EmptyState`. Initial-password: the deliberate information-hiding message |
| `409` | Duplicate code / ISBN / email / enrollment | `ErrorBanner` with the server's `message` verbatim — it is already user-readable |
| `5xx` | Unexpected | Generic banner + `console.error` |

The `409` row matters for the demo: the backend's conflict messages are specific ("Student 'S001' already exists"), so passing `message` straight through is both the least code and the best output.

---

## 9. Backend gaps — surfaced, not faked

Three stubs remain in the shipped backend. The UI **must show them as gaps** rather than render an empty table that looks like real data.

| Gap | Source | UI treatment |
| --- | --- | --- |
| `StudentDetail.books` is always `[]` | `StudentService.java:195` — hardcoded `List.of()` | **Compensate.** Call `GET /books?owner={student.id}` and render the real result. No backend change needed. |
| `StudentDetail.courses` is always `[]` | `StudentService.java:196` | **Disclose.** No staff-facing endpoint exists — `EnrollmentLookup.findByStudent` is reachable only through `/me`. Render: *"Enrollments are not exposed on this endpoint (US-5.5 composition pending)."* |
| `CourseDetail.roster` is always `[]` | `CourseService.java:139` | **Disclose.** Same note, for the course roster. |
| No `GET /staff-accounts` list | Only `POST` and `PATCH .../status` exist | **Disclose.** `PATCH /staff-accounts/{id}/status` needs a numeric user id that `POST` never returns, so the deactivate form takes a typed id with a note explaining why. |

Each disclosure is a small muted note in the section where the data would be — enough that a viewer understands the boundary of what shipped.

Two further notes worth carrying into the demo:

- **`demo.student` is listed but never seeded.** `IdentityService.seedDemoAccounts` skips the `STUDENT` entry, because a student-role account requires a real `students` row (`chk_users_student_role`'s FK co-invariant). Logging in as it fails until a matching student is registered by hand. The login page's demo-account chips should mark it accordingly — the demo script (§10) reaches the student role by registering a real one instead.
- **`studentId` is not in the login response.** It is never needed: `/me` derives it server-side, and student-scoped searches are scoped server-side too.

---

## 10. Build order

| Phase | Work | Est. |
| --- | --- | --- |
| 1 | Scaffold; Vite proxy; `client.js` + `endpoints.js`; `AuthContext` + `RequireAuth` + `permissions.js`; `LoginPage` + `ChangePasswordPage`; `AppShell` with role-filtered nav | 3h |
| 2 | `DataTable`, `Pagination`, `Modal`, `Field`, `ErrorBanner`, `Toast`, `EmptyState`, `Badge`; `usePagedResource`, `useAsyncAction`; `tokens.css` + `base.css` | 2h |
| 3 | Students (list, detail, register/edit/delete, initial-password) + Books (list, detail, add/assign/unassign/delete) | 3h |
| 4 | Courses (list, detail, CRUD) + Enrollments + `/me` | 3h |
| 5 | Staff accounts; demo-account chips on login; gap disclosures (§9); CSS pass | 2h |

**~13 hours.** Phases 1–2 are the load-bearing ones; 3–5 are repetitions of the same two patterns and go quickly.

**Order rationale:** Phase 1 ends with a runnable app that can log in and switch roles — the riskiest integration (the cookie through the proxy) is proven first, before any screen is built on top of it.

---

## 11. Demo script

The run-through that exercises all 5 roles and ties the modules into one story.

**Start:**
```bash
make up                                  # MySQL
cd management && ./mvnw spring-boot:run   # :8080, seeds the 4 staff demo accounts
cd management-frontend && npm run dev     # :5173
```

| # | Role | Action | Demonstrates |
| --- | --- | --- | --- |
| 1 | — | Open `/login`; the seeded accounts are listed live from `GET /auth/demo-accounts` | PM-017 |
| 2 | `demo.registrar` | Register a student — the one-time `initialPassword` is shown; re-read it from the detail page | UC-1, UC-23 |
| 3 | `demo.courseadmin` | Create course `CS101` | UC-8 |
| 4 | `demo.registrar` | Enroll the new student in `CS101` | UC-11 |
| 5 | `demo.librarian` | Add a book, then assign it to the student | UC-4, UC-5 |
| 6 | **the new student** | Log in (username = their email, password from step 2) → **forced** to `/change-password` → then `/me` shows the assigned book and enrolled course | UC-21, UC-22, UC-16 |
| 7 | `demo.sysadmin` | Create a staff account (initial password shown once), then deactivate it | UC-24, UC-25 |
| 8 | `demo.sysadmin` | Note the nav shows only Staff Accounts — a domain read returns `403` | RBAC allow-list |

Steps 2→6 are the narrative spine: a student created by one role, given a course by a second and a book by a third, then logging in as themselves and seeing both. Step 6 is also the must-change-password gate in action, and step 8 is the RBAC allow-list in action.

**Reset between runs:** `make reset` (drops the volume and restarts MySQL). Demo-account seeding is idempotent, so a restart never resets a password that was changed during a demo.

---

## 12. Verification checklist

Run against a live stack after implementation:

- [ ] Login sets `JSESSIONID` through the proxy — DevTools → Application → Cookies shows it on `localhost:5173`.
- [ ] A hard refresh on any deep route keeps the session (restored from `sessionStorage`).
- [ ] A newly registered student is blocked on **every** route until the password is changed, and is released immediately after — no re-login.
- [ ] `demo.sysadmin` sees only Staff Accounts in the nav; `GET /api/v1/students` returns `403`.
- [ ] A duplicate student code surfaces the backend's `409` message in `ErrorBanner`.
- [ ] An invalid email surfaces the `400` `errors[]` entry inline under the email field.
- [ ] A student searching students gets exactly their own row; another student's detail page returns `403` → `ForbiddenPage`.
- [ ] Pagination: with >20 students, page 2 loads and `totalPages` is respected.
- [ ] Student detail shows real owned books (via `?owner=`) and the disclosure note for courses.
- [ ] Logout clears local state and returns to `/login` even if `POST /logout` fails.

---

## 13. References

- [BA-docs/use-cases.md](../BA-docs/use-cases.md) — the 25 UCs each screen covers
- [BA-docs/user-stories.md](../BA-docs/user-stories.md) — acceptance criteria
- [SA-docs/api-specification.md](../SA-docs/api-specification.md) — the hand-authored contract; §3 above is the as-built version
- [SA-docs/04-authentication-authorization.md](../SA-docs/04-authentication-authorization.md) — session auth, RBAC matrix, must-change-password gate
- [PM-docs/01-product-backlog.md](../PM-docs/01-product-backlog.md) — what shipped, and in which sprint
- `management/src/main/java/org/phuchoang/management/shared/security/SecurityConfig.java` — the authoritative RBAC rules mirrored in §5
