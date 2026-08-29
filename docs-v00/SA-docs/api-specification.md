# API Specification

A companion artifact to the Solution Architecture document set — not part of the 01–05 numbered series. It is the request/response contract those documents deliberately deferred: `02-component-diagram.md` §5 ("Database schema / column-level design — tracked separately, not duplicated here") and, more directly, `03-sequence-diagrams.md` and `04-authentication-authorization.md`, both of which name request/response DTOs (`StudentResponse`, `BookDetail`, `LoginResponse`, ...) without specifying their fields, noting that shape is "tracked separately in an OpenAPI contract." This document is that contract.

It is hand-authored: no backend implementation exists yet in `management/` to generate a spec from, so every endpoint, schema, and status code below is derived directly from `01-system-overview.md` through `05-database-schema.md` and `../BA-docs/use-cases.md`.

---

## 1. What this is

- `openapi/openapi.yaml` — the source of truth. A modular OpenAPI 3.0.3 document split across `openapi/paths/`, `openapi/components/schemas/`, `openapi/components/responses/`, and `openapi/components/parameters/`, joined with `$ref`.
- `openapi.html` — a rendered, single-file, fully offline copy (Redoc), generated from the YAML source. Open it directly; no server or internet connection required.

**Both HTML artifacts under `docs/` are generated and gitignored**, and they come from two different generators, so they are named apart: `openapi.html` is Redoc's render of the contract (§4 below), while `api-specification.html` is this Markdown file compiled by `util/md-to-html.js` (`make docs`). Neither is committed; regenerate rather than edit.

## 2. Viewing it

- **Quick look:** run the pipeline in §4, then open `openapi.html` in any browser (double-click it). The spec data and the Redoc rendering engine are both embedded in the file.
- **Editing / tooling:** point any OpenAPI 3.x-aware tool (editor plugin, codegen, mock server) at `openapi/openapi.yaml`.

## 3. Conventions

- **Base path:** every operation is relative to `/api/v1`.
- **Auth:** session-based, not token-based. `POST /auth/login` opens a server-side HTTP session and sets the `JSESSIONID` cookie (`cookieAuth` security scheme). There is no JWT/bearer token anywhere in this API.
- **Roles:** `SYSTEM_ADMINISTRATOR`, `REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR`, `STUDENT`. Every operation states its allowed roles in its `description` (a leading `**Roles:** ...` line) and in the machine-readable `x-roles` extension, since OpenAPI has no native per-role authorization construct. **Read access is granted per resource, not as one blanket "domain read"** — see `04-authentication-authorization.md` §6.1 for the full matrix and `02-component-diagram.md` §4 for what each grant is for.
- **must-change-password gate:** while `principal.mustChangePassword = true`, every endpoint except `POST /auth/password` responds `403 Forbidden`.
- **Error shape:** every non-2xx response body is `Error` (`{timestamp, status, error, message, path}` — Spring Boot's default shape) or, for `400` responses, `ValidationError` (`Error` plus a per-field `errors` array). No custom envelope is defined elsewhere in the design docs, so this idiomatic default was adopted deliberately.
- **Pagination:** every list endpoint — `GET /students`, `GET /books`, `GET /courses`, `GET /enrollments`, `GET /me/books`, `GET /me/courses` — returns a page, not a bare array: `{content: [...], page, size, totalElements, totalPages}` (`PageMeta`), mirroring Spring Data's `Page<T>` JSON shape. Paged uniformly via `page` (0-based, default 0) and `size` (default 20, max 100). An out-of-range `page` returns `200` with empty `content`, matching every search use case's existing "no match → `200 []`" pattern; `size` outside 1–100 is clamped by `spring.data.web.pageable.max-page-size`. No endpoint takes prefixed paging parameters: an earlier `GET /me/books-and-courses` composed two independently paged collections into one response and needed `booksPage`/`coursesPage` variants to do it, and splitting it into `/me/books` and `/me/courses` removed both the special case and the reason for it.

## 4. Regenerating the HTML

Requires Node.js (`@redocly/cli` is invoked via `npx`, no local install needed):

```bash
cd openapi

# 1. Lint the modular source
npx --yes @redocly/cli lint openapi.yaml

# 2. Bundle into one file (build artifact only — not committed, see openapi/.gitignore)
npx --yes @redocly/cli bundle openapi.yaml -o .build/openapi.bundled.yaml --ext yaml

# 3. Render HTML
npx --yes @redocly/cli build-docs .build/openapi.bundled.yaml \
  -o ../openapi.html --title "Student Management System API" --disableGoogleFont

# 4. Required post-process: build-docs still hardcodes a CDN <script src> for the Redoc
#    rendering engine itself even though the spec data is embedded; this fetches that
#    exact JS once and inlines it so the final file has zero external dependencies.
node scripts/inline-redoc.mjs ../openapi.html
```

## 5. Design decisions & deviations from the SA docs

Where the sequence-diagram/auth docs left a status code ambiguous or unspecified, it was resolved here rather than carried through as-is:

1. **Duplicate email on student create/update** is `03-sequence-diagrams.md`'s only ambiguous case (annotated `400/409`, unlike the other four uniqueness checks). Resolved: malformed email format → `400`; duplicate email → `409`, consistent with student code / ISBN / course code / enrollment duplicates.
2. **Unknown FK references** (unknown `ownerId` on book create, unknown `studentId`/`courseCode` on enrollment create) are `400` — already consistent in the source docs, codified here as a spec-wide rule: a bad caller-supplied reference is malformed input, not a state conflict.
3. **A Student reading another principal's single-resource record** (e.g. `GET /students/{code}` for someone else) is undocumented upstream. Resolved: `403 Forbidden` — the resource exists and the request is well-formed, only authorization fails. This applies to the two resources a Student is granted at all (`/students`, `/books`); it never arises for `/enrollments`, which role STUDENT cannot reach.
4. **Student role on search/list endpoints** is not blocked with `403`; results are transparently scoped server-side, returning `200` with 0 or 1 result — consistent with the "no match → `200 []`" pattern every search use case already uses. Scoping applies only where a Student holds a grant: `GET /students` and `GET /books`. `GET /enrollments` is not scoped but *withdrawn* (decision #10).
5. **`GET /students/{code}/initial-password`** already collapses "password already changed" and "student not found" into one `404` in the source doc. Kept as-is and called out explicitly as intentional information-hiding, not an oversight.
6. **`GET /me/**` called by a non-Student role** is undocumented upstream. Resolved: `403 Forbidden`, for consistency with #3.
7. **`DELETE /books/{isbn}/owner` when the book is already unowned** is undocumented upstream. Resolved: idempotent `200` (returns the book with `ownerId: null`) — no other endpoint in this API models an "already in that state" `409`.
8. **Pagination defaults/cap and invalid-input handling** are not specified by any use case, so they're fixed here: default page size `20`, capped at `100`; default `page` `0`. An out-of-range `page` returns an empty page (decision #3's "no match" pattern); an oversized `size` is clamped by Spring's `max-page-size` rather than rejected, uniformly across every list endpoint.

9. **Surrogate ids never cross the HTTP boundary.** `students.id`, `books.id`, `courses.id`, `enrollments.id`, and the `books.owner_id` / `enrollments.student_id` FKs are database concerns. Every request path, query parameter, and body field names a record by its **business key** — `studentCode`, `isbn`, `courseCode` — and no response carries an `id` a caller could send back. Two consequences: `POST /enrollments` takes `{studentCode, courseCode}` and its paths read `/enrollments/{studentCode}/{courseCode}`; `PATCH /books/{isbn}/owner` takes `{studentCode}`, `GET /books` filters on `?ownerStudentCode=`, and a book's owner is rendered as `ownerStudentCode`. There are two deliberate exceptions, both for resources with no business key of their own, and both addressed by a value a prior listing supplied rather than one a caller knows.

   The first is `PATCH /staff-accounts/{id}/status`: an `identity` record has no business key — a username can be renamed, an id cannot — and the id comes from `GET /staff-accounts`.

   The second is `DELETE /sessions/{handle}`, whose handle comes from `GET /sessions`. It is not a surrogate id and is worth distinguishing: a session's identifier is not a database concern but a *bearer credential*, so it is not merely kept off the wire, it must be — see decision #13.

   The single `StudentCode` → `StudentId` translation point is `StudentLookup.idOf` (`06-low-level-design.md` §4.8). An unresolvable code is a `400` when supplied as a *reference* (`POST /enrollments`, `GET /enrollments?studentCode=`, `POST /books`, `PATCH /books/{isbn}/owner`), per decision #2 — but a `404` when it is part of the *address* of one enrollment (`GET`/`DELETE /enrollments/{studentCode}/{courseCode}`), because an enrollment whose student does not exist cannot exist either, and answering differently would leak whether the student does.

10. **Related collections are separate endpoints, not embedded fields.** `GET /students/{code}` returns the student record alone and `GET /courses/{code}` the course record alone. A student's books come from `GET /books?ownerStudentCode=`, their courses from `GET /enrollments?studentCode=`, and a course's roster from `GET /enrollments?courseCode=`. Two reasons, both load-bearing: each collection is independently paged, which an embedded array cannot be; and each carries its own authorization, so a Librarian opening a student profile does not receive that student's course list, and a Student opening a course does not receive the roster. This supersedes the `books`/`courses` fields on `StudentDetail` and the `roster` field on `CourseDetail`, which the shipped backend returned as permanently empty arrays.

11. **One aggregate is embedded on a course, against decision #10: `enrolledCount`.** `CourseSummary` and `CourseDetail` carry how many students are enrolled, while the roster itself stays a separate endpoint. The tension is real and resolved this way deliberately: #10's two reasons are paging and authorization, and neither applies to a scalar. A count cannot be paged, and it discloses nothing a roster would — "how many" is not "who", so it is safe on the shape a Student browsing the catalogue receives, where the roster explicitly is not.

    It is not on `CourseResponse` (create/update), where the count is trivially zero or unchanged and computing it would cost a query for a value the caller did not ask about. And because it is read outside any enrolling transaction, it is a snapshot rather than a guarantee — it must not be used as a capacity check.

    Note the module consequence recorded in `02-component-diagram.md` §2.2: `course` reads `enrollments` in SQL, not through a Java call, because `enrollment` already depends on `course` and the reverse edge would close a cycle.

12. **`POST /enrollments/batch` reports per-course outcomes at `200`, and is the one request in this API that is not a single transaction.** Enrolling a student into five courses when they are already in one of them enrolls the other four and says so, rather than rejecting all five.

    Three sub-decisions:

    - **`200`, not `207 Multi-Status`.** 207 is a WebDAV code (RFC 4918) defined against a `DAV:multistatus` XML body; pairing it with a bespoke JSON shape would be a status-code pun, and it conveys nothing the body does not. It is also 2xx either way, so a client gains nothing while risking a proxy or fetch wrapper that special-cases an unfamiliar status. The request succeeded; the per-course statuses are its answer. That holds even when every course was rejected — nothing was wrong with the *request*.
    - **An unknown student is still a whole-request `400`, while an unknown course is a per-course outcome.** The student is the subject of the request rather than one of its items: an unknown one leaves every course unanswerable, so there is nothing to report per course. This keeps decision #2 intact for the student reference.
    - **Successes are durable independently.** Each course commits on its own, so a rejection later in the list does not undo an earlier success. This is the deviation from the rest of the API worth stating loudly, since every other endpoint here is atomic. Retrying the whole request is safe: the courses that succeeded answer `ALREADY_ENROLLED`.

    Duplicate course codes within one request are collapsed, keeping the first occurrence — answering `ENROLLED` and then `ALREADY_ENROLLED` for the same code would be accurate about what happened and indistinguishable from a defect to whoever read it. `requested` therefore counts distinct courses and can be below the number submitted.

13. **A session is addressed by a digest of its identifier, never by the identifier.** `GET /sessions` publishes a SHA-256 hex digest as each session's `handle`, and `DELETE /sessions/{handle}` resolves it server-side.

    The reason is that a session identifier is not an opaque key like a surrogate id — it is a credential. Anything holding one can present it as a `JSESSIONID` cookie and become that user, so putting live identifiers on an administrator's screen would spread them into browser history, screenshots, and logs, each of which is a full account takeover. A digest is stable enough to address a session and preimage-resistant enough that none of those disclose anything.

    Two shape consequences: `/sessions` is not paged (the underlying registry is an in-memory snapshot with no stable ordering to offset into, so pages would overlap and skip), and the row carries no `mustChangePassword` or `enabled` — the registry's copy of those goes stale after a password change, and account state is `/staff-accounts`'s answer. See `04-authentication-authorization.md` §3c.

14. **Cursor-based Prev/Next-only pagination supersedes decision #8, for `/students`, `/books`, `/courses`, `/enrollments`, `/me/books`, and `/me/courses` (Sprint 9, PM-045).** These six endpoints drop `page`, `totalElements`, and `totalPages` entirely; the response envelope is `{content, nextCursor}`, where `nextCursor` is an opaque, server-issued token (or `null` on the last page) that the caller echoes back as the `cursor` query parameter to fetch the next page. There is no way to jump to an arbitrary page or ask "how many pages" — only "give me the next one" — because no use case ever asked for either (`non-functional-requirements.md` §3.1 confirms every paginating UC describes "return the next page" and nothing more). `size`'s default of `20` and cap of `100` are unchanged from decision #8, and an oversized `size` is still clamped rather than rejected.

    This is a genuine, deliberate product change, not only an internal optimization: a client can no longer render "Page 3 of 12" for these six endpoints. It was accepted because it is what let the backend replace `OFFSET`/`LIMIT` scans with keyset (seek) pagination and drop a redundant `COUNT(*)` per request, fixing two of the benchmark epic's confirmed hazards (H1, H3 — `docs-v01/Benchmark/`) with one shape change instead of two partial ones.

    `GET /staff-accounts` is the one remaining `page`-based listing, deliberately unconverted: it is not named by any Sprint 9 item and converting it was out of scope for that sprint.

15. **`POST /auth/login` is bounded by a concurrency bulkhead, answering `429` when saturated (Sprint 10, PM-048).** `06-conclusions-and-recommendations.md` (`docs-v01/Benchmark/`) named hazard H5: BCrypt strength 10 puts a ~95ms floor under every login attempt, that work runs synchronously on the same Tomcat thread pool every other endpoint shares, and nothing bounded how many threads a login burst could occupy at once. `docs-v01/Benchmark/08-hazard-fix-specs.md` `IP-08` left the mechanism open between a dedicated thread pool and a queue-then-`429` cutoff; the mechanism actually built is a non-blocking, semaphore-gated permit count (`app.security.login-bulkhead.permits`, default `20`) — a request past the bound is rejected immediately, before the authentication manager (and BCrypt) ever run, rather than queued. Queuing was rejected because a queue sitting in front of BCrypt is itself a resource risk under the exact burst this exists to contain; `429` fails fast instead.

    This is not general API rate limiting — no other endpoint is bounded this way, and the permit count is not per-client, only a single shared pool for the whole login endpoint. It does not touch BCrypt's own work factor, which is a deliberate security property (H5), not a defect. See `docs-v01/PM-docs/epic-k-sprint-plan.md` (PM-048) for the full hazard/verification history.

## 6. Out of scope

Mirrors `04-authentication-authorization.md` §9: no SSO/OAuth/OIDC, no true forgot-password flow, no MFA, no API versioning strategy beyond the `/api/v1` prefix. Pagination is specified — see §3. There is no general per-client API rate limiting; `POST /auth/login` alone is the one exception, bounded by the concurrency bulkhead in decision #15 above, which exists to isolate BCrypt's cost from the rest of the application rather than to throttle request rate.
