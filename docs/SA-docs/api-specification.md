# API Specification

A companion artifact to the Solution Architecture document set — not part of the 01–05 numbered series. It is the request/response contract those documents deliberately deferred: `02-component-diagram.md` §5 ("Database schema / column-level design — tracked separately, not duplicated here") and, more directly, `03-sequence-diagrams.md` and `04-authentication-authorization.md`, both of which name request/response DTOs (`StudentResponse`, `BookDetail`, `LoginResponse`, ...) without specifying their fields, noting that shape is "tracked separately in an OpenAPI contract." This document is that contract.

It is hand-authored: no backend implementation exists yet in `management/` to generate a spec from, so every endpoint, schema, and status code below is derived directly from `01-system-overview.md` through `05-database-schema.md` and `../BA-docs/use-cases.md`.

---

## 1. What this is

- `openapi/openapi.yaml` — the source of truth. A modular OpenAPI 3.0.3 document split across `openapi/paths/`, `openapi/components/schemas/`, `openapi/components/responses/`, and `openapi/components/parameters/`, joined with `$ref`.
- `api-specification.html` — a rendered, single-file, fully offline copy (Redoc), generated from the YAML source. Open it directly; no server or internet connection required.

## 2. Viewing it

- **Quick look:** open `api-specification.html` in any browser (double-click it). The spec data and the Redoc rendering engine are both embedded in the file.
- **Editing / tooling:** point any OpenAPI 3.x-aware tool (editor plugin, codegen, mock server) at `openapi/openapi.yaml`.

## 3. Conventions

- **Base path:** every operation is relative to `/api/v1`.
- **Auth:** session-based, not token-based. `POST /auth/login` opens a server-side HTTP session and sets the `JSESSIONID` cookie (`cookieAuth` security scheme). There is no JWT/bearer token anywhere in this API.
- **Roles:** `REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR`, `STUDENT`. Every operation states its allowed roles in its `description` (a leading `**Roles:** ...` line) and in the machine-readable `x-roles` extension, since OpenAPI has no native per-role authorization construct.
- **must-change-password gate:** while `principal.mustChangePassword = true`, every endpoint except `POST /auth/password` responds `403 Forbidden`.
- **Error shape:** every non-2xx response body is `Error` (`{timestamp, status, error, message, path}` — Spring Boot's default shape) or, for `400` responses, `ValidationError` (`Error` plus a per-field `errors` array). No custom envelope is defined elsewhere in the design docs, so this idiomatic default was adopted deliberately.

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
  -o ../api-specification.html --title "Student Management System API" --disableGoogleFont

# 4. Required post-process: build-docs still hardcodes a CDN <script src> for the Redoc
#    rendering engine itself even though the spec data is embedded; this fetches that
#    exact JS once and inlines it so the final file has zero external dependencies.
node scripts/inline-redoc.mjs ../api-specification.html
```

## 5. Design decisions & deviations from the SA docs

Where the sequence-diagram/auth docs left a status code ambiguous or unspecified, it was resolved here rather than carried through as-is:

1. **Duplicate email on student create/update** is `03-sequence-diagrams.md`'s only ambiguous case (annotated `400/409`, unlike the other four uniqueness checks). Resolved: malformed email format → `400`; duplicate email → `409`, consistent with student code / ISBN / course code / enrollment duplicates.
2. **Unknown FK references** (unknown `ownerId` on book create, unknown `studentId`/`courseCode` on enrollment create) are `400` — already consistent in the source docs, codified here as a spec-wide rule: a bad caller-supplied reference is malformed input, not a state conflict.
3. **A Student reading another principal's single-resource record** (e.g. `GET /students/{code}` for someone else) is undocumented upstream. Resolved: `403 Forbidden` — the resource exists and the request is well-formed, only authorization fails.
4. **Student role on search/list endpoints** is not blocked with `403`; results are transparently scoped server-side, returning `200` with 0 or 1 result — consistent with the "no match → `200 []`" pattern every search use case already uses.
5. **`GET /students/{code}/initial-password`** already collapses "password already changed" and "student not found" into one `404` in the source doc. Kept as-is and called out explicitly as intentional information-hiding, not an oversight.
6. **`GET /me/books-and-courses` called by a non-Student role** is undocumented upstream. Resolved: `403 Forbidden`, for consistency with #3.
7. **`DELETE /books/{isbn}/owner` when the book is already unowned** is undocumented upstream. Resolved: idempotent `200` (returns the book with `ownerId: null`) — no other endpoint in this API models an "already in that state" `409`.

## 6. Out of scope

Mirrors `04-authentication-authorization.md` §7: no SSO/OAuth/OIDC, no true forgot-password flow, no MFA, no rate limiting, no API versioning strategy beyond the `/api/v1` prefix, no pagination on list/search endpoints (not specified by any use case — all "View/Search" flows return a flat list). These may warrant their own future revision of this contract once real usage patterns emerge.
