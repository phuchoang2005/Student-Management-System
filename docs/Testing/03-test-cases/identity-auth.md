# Test Cases — `identity` Module (Authentication & Access)

Testing Documentation — [Test Strategy](../01-test-strategy.md) → [Test Plan](../02-test-plan.md) → Test Cases (`identity`) → [Test Data Preparation](../04-test-data-preparation.md).

Covers **UC-16** (View Own Books, Courses & Enrollments), **UC-21** (Login), **UC-22** (Change Password), **UC-23** (View Student's Initial Password), **UC-24** (Create Staff Account), **UC-25** (Deactivate/Reactivate Staff Account), the demo-accounts convenience, and related user stories US-5.4, US-6.1–6.3, US-7.1–7.2. Endpoints: `POST /auth/login`, `POST /auth/password`, `GET /auth/demo-accounts`, `GET /students/{code}/initial-password`, `GET /me/books-and-courses`, `POST /staff-accounts`, `PATCH /staff-accounts/{id}/status`. Account auto-provisioning at registration (UC-1's identity tail) is tested in [student.md](./student.md) TC-STU-008–010; this file covers the account once it exists. The full RBAC matrix and the must-change-password gate as a cross-cutting concern are in [cross-cutting.md](./cross-cutting.md) §1–2, §9.

---

## UC-21: Login

### TC-IDN-001 — Login with valid credentials succeeds
- **Related UC / Rule:** UC-21 main flow; Identity.2
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `staff-registrar-01` (pre-seeded, password already changed — see [04-test-data-preparation.md](../04-test-data-preparation.md) §1)
- **Steps:** `POST /api/v1/auth/login` with valid `username`/`password`.
- **Expected Result:** `200 OK` with `{role, mustChangePassword}`; a `JSESSIONID` cookie is set.

### TC-IDN-002 — Login rejected: unknown username
- **Related UC / Rule:** UC-21 flow 2a
- **Priority:** P0 · **Type:** Security-Negative
- **Steps:** `POST /api/v1/auth/login` with a username that doesn't exist.
- **Expected Result:** `401 Unauthorized`; response does not reveal whether the username or the password was the problem (generic "invalid username or password").

### TC-IDN-003 — Login rejected: wrong password for a known username
- **Related UC / Rule:** UC-21 flow 2a
- **Priority:** P0 · **Type:** Security-Negative
- **Steps:** `POST /api/v1/auth/login` with a valid username and an incorrect password.
- **Expected Result:** `401 Unauthorized`; same generic error shape as TC-IDN-002 (no username-enumeration signal).

### TC-IDN-004 — Login with an initial-password account reports `mustChangePassword: true`
- **Related UC / Rule:** UC-21 step 4; Identity.3
- **Priority:** P0 · **Type:** Functional
- **Test Data:** A freshly-registered student's account (chain from [student.md](./student.md) TC-STU-009), never yet logged in with a changed password.
- **Steps:** `POST /api/v1/auth/login` with the auto-generated initial credentials.
- **Expected Result:** `200 OK`; `mustChangePassword: true` in the response.

### TC-IDN-005 — Login request body must be JSON (not form-encoded)
- **Related UC / Rule:** `06-low-level-design.md` §11.2 (`JsonUsernamePasswordAuthenticationFilter`)
- **Priority:** P2 · **Type:** Functional
- **Steps:** `POST /api/v1/auth/login` with a malformed/non-JSON body.
- **Expected Result:** Login fails cleanly (`400` or the filter's `AuthenticationServiceException` path resulting in `401`) rather than a raw stack trace or `500`.

---

## UC-22: Change Password

### TC-IDN-006 — Change password successfully with matching retype and a policy-compliant new password
- **Related UC / Rule:** UC-22 main flow; Identity.3–5
- **Priority:** P0 · **Type:** Functional
- **Preconditions:** Authenticated session (any role).
- **Steps:** `POST /api/v1/auth/password` with `{currentPassword, newPassword, retypeNewPassword}`, `newPassword` 8+ chars and different from current.
- **Expected Result:** `200 OK`; `mustChangePassword` clears if it was set; subsequent login with the old password fails, login with the new one succeeds.

### TC-IDN-007 — Change password rejected: retyped password doesn't match
- **Related UC / Rule:** UC-22 flow 2a
- **Priority:** P1 · **Type:** Negative
- **Steps:** `POST /api/v1/auth/password` with `newPassword != retypeNewPassword`.
- **Expected Result:** `400 Bad Request`; password unchanged.

### TC-IDN-008 — Change password rejected: current password is wrong
- **Related UC / Rule:** UC-22 flow 3a
- **Priority:** P0 · **Type:** Security-Negative
- **Steps:** `POST /api/v1/auth/password` with an incorrect `currentPassword`.
- **Expected Result:** `401 Unauthorized`; password unchanged.

### TC-IDN-009 — Change password rejected: new password below minimum length (7 chars)
- **Related UC / Rule:** UC-22 flow 4a; `04-authentication-authorization.md` §5.2 rule 1
- **Priority:** P1 · **Type:** Boundary
- **Test Data:** `password-boundary-7chars`
- **Steps:** `POST /api/v1/auth/password` with a 7-character `newPassword`.
- **Expected Result:** `400 Bad Request`.

### TC-IDN-010 — Change password accepted: new password at exactly the minimum length (8 chars)
- **Related UC / Rule:** `04-authentication-authorization.md` §5.2 rule 1
- **Priority:** P1 · **Type:** Boundary
- **Test Data:** `password-boundary-8chars`
- **Steps:** `POST /api/v1/auth/password` with an 8-character `newPassword` that differs from current.
- **Expected Result:** `200 OK`.

### TC-IDN-011 — Change password accepted: new password at exactly the maximum length (72 chars)
- **Related UC / Rule:** `04-authentication-authorization.md` §5.2 rule 5 (BCrypt 72-byte truncation boundary)
- **Priority:** P1 · **Type:** Boundary
- **Test Data:** `password-boundary-72chars`
- **Steps:** `POST /api/v1/auth/password` with a 72-character `newPassword`.
- **Expected Result:** `200 OK`; a subsequent login with the exact same 72-character string succeeds (confirms no silent truncation mismatch).

### TC-IDN-012 — Change password rejected: new password exceeds maximum length (73 chars)
- **Related UC / Rule:** `04-authentication-authorization.md` §5.2 rule 5
- **Priority:** P1 · **Type:** Boundary
- **Test Data:** `password-boundary-73chars`
- **Steps:** `POST /api/v1/auth/password` with a 73-character `newPassword`.
- **Expected Result:** `400 Bad Request` — rejected explicitly rather than silently truncated by BCrypt.

### TC-IDN-013 — Change password rejected: new password identical to current password
- **Related UC / Rule:** UC-22 flow 4a; `04-authentication-authorization.md` §5.2 rule 3
- **Priority:** P1 · **Type:** Negative
- **Steps:** `POST /api/v1/auth/password` with `newPassword = currentPassword`.
- **Expected Result:** `400 Bad Request` — prevents a no-op "change" that would still clear the must-change-password gate.

### TC-IDN-014 — Changing password clears `initial_password_encrypted` permanently
- **Related UC / Rule:** UC-22 postconditions; Identity.4
- **Priority:** P0 · **Type:** Functional
- **Preconditions:** Student account still on its initial password.
- **Steps:** Change the password successfully; then (as Registrar) `GET /api/v1/students/{code}/initial-password`.
- **Expected Result:** `404 Not Found` (`PasswordNoLongerAvailableException`) — see TC-IDN-018; the original password is unrecoverable by anyone, including the Registrar.

### TC-IDN-015 — A changed session's must-change-password state updates without forcing re-login
- **Related UC / Rule:** `04-authentication-authorization.md` §5.1 (session principal refresh)
- **Priority:** P2 · **Type:** Functional
- **Steps:** In one authenticated session, submit a successful password change; immediately call a normal endpoint (no new login).
- **Expected Result:** The same session, without re-authenticating, is no longer blocked by the must-change-password gate ([cross-cutting.md](./cross-cutting.md) §2).

---

## UC-23: View Student's Initial Password

### TC-IDN-016 — Registrar views a still-active initial password
- **Related UC / Rule:** UC-23 main flow; Identity.5
- **Priority:** P0 · **Type:** Functional
- **Preconditions:** Student's account has never had its password changed.
- **Steps:** As Registrar, `GET /api/v1/students/{code}/initial-password`.
- **Expected Result:** `200 OK` with `{username, initialPassword}`; the returned password matches the one originally issued at registration.

### TC-IDN-017 — View initial password rejected: password already changed
- **Related UC / Rule:** UC-23 flow 2a; Identity.4, Identity.5
- **Priority:** P0 · **Type:** Security-Negative
- **Preconditions:** Student has already changed their password (chain from TC-IDN-006).
- **Steps:** As Registrar, `GET /api/v1/students/{code}/initial-password`.
- **Expected Result:** `404 Not Found` — indicates unavailability without distinguishing "changed" from "never existed" (see TC-IDN-018).

### TC-IDN-018 — Initial-password endpoint collapses "already changed" and "student not found" into the same response
- **Related UC / Rule:** `api-specification.md` §5.5 (intentional information-hiding, explicitly called out as deliberate, not an oversight)
- **Priority:** P1 · **Type:** Security
- **Steps:** Compare the response for (a) a student who changed their password and (b) a student code that never existed.
- **Expected Result:** Both return `404 Not Found` with an indistinguishable body — confirms the deliberate design decision rather than an accidental information leak either way.

---

## UC-16: View Own Books, Courses & Enrollments (Student self-service read)

### TC-IDN-019 — Student views their own owned books and enrolled courses
- **Related UC / Rule:** UC-16 main flow
- **Priority:** P1 · **Type:** Functional
- **Test Data:** A Student-role account whose linked student owns books and holds enrollments.
- **Steps:** As Student, `GET /api/v1/me/books-and-courses`.
- **Expected Result:** `200 OK`; response contains summaries of exactly the books owned by and courses enrolled in by *this* student — never another student's.

### TC-IDN-020 — Student with no books or enrollments sees empty lists, not an error
- **Related UC / Rule:** UC-16 flows 2a/3a
- **Priority:** P2 · **Type:** Boundary
- **Steps:** As a Student with no associations, `GET /api/v1/me/books-and-courses`.
- **Expected Result:** `200 OK`; both lists empty.

### TC-IDN-021 — `GET /me/books-and-courses` called by a non-Student role
- **Related UC / Rule:** `api-specification.md` §5.6 (explicit deviation resolution)
- **Priority:** P1 · **Type:** Security-RBAC
- **Steps:** As Registrar/Librarian/Course Administrator, `GET /api/v1/me/books-and-courses`.
- **Expected Result:** `403 Forbidden` — this endpoint is Student-only by design, even though the role can read everything via other endpoints.

### TC-IDN-022 — `books` and `courses` page independently
- **Related UC / Rule:** UC-16 flows 2b/3b (`api-specification.md` §3 Pagination)
- **Priority:** P2 · **Type:** Functional
- **Test Data:** A Student-role account whose linked student owns at least 3 books and holds at least 1 active enrollment.
- **Steps:** As Student, `GET /api/v1/me/books-and-courses?booksPage=1&booksSize=2&coursesPage=0&coursesSize=20`.
- **Expected Result:** `200 OK`; `books.page` is `1` (the second page of the caller's books) while `courses.page` is `0` — moving one collection's page does not affect the other's.

### TC-IDN-023 — Empty `books`/`courses` pages are still `200`, not an error
- **Related UC / Rule:** UC-16 flows 2a/3a, extended to the paginated envelope
- **Priority:** P2 · **Type:** Boundary
- **Steps:** As a Student with no owned books and no active enrollments, `GET /api/v1/me/books-and-courses`.
- **Expected Result:** `200 OK`; both `books` and `courses` are `{content: [], page: 0, size: 20, totalElements: 0, totalPages: 0}`.

---

## UC-24: Create Staff Account

### TC-IDN-024 — System Administrator creates a staff account successfully
- **Related UC / Rule:** UC-24 main flow; Identity.3, Identity.6
- **Priority:** P0 · **Type:** Functional
- **Steps:** As SYSTEM_ADMINISTRATOR, `POST /api/v1/staff-accounts` with `{username, role: "LIBRARIAN"}`.
- **Expected Result:** `201 Created` with `{username, role, initialPassword}`; the account can log in (TC-IDN-001-style) and reports `mustChangePassword: true`.

### TC-IDN-025 — Create staff account rejected: non-System-Administrator caller
- **Related UC / Rule:** `06-low-level-design.md` §11.1; see also [cross-cutting.md](./cross-cutting.md) TC-XC-039
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As REGISTRAR, `POST /api/v1/staff-accounts` with a valid body.
- **Expected Result:** `403 Forbidden`; no account created.

### TC-IDN-026 — Create staff account rejected: requested role is SYSTEM_ADMINISTRATOR
- **Related UC / Rule:** UC-24 flow 3a; Identity.6
- **Priority:** P0 · **Type:** Security-Negative
- **Steps:** As SYSTEM_ADMINISTRATOR, `POST /api/v1/staff-accounts` with `{username, role: "SYSTEM_ADMINISTRATOR"}`.
- **Expected Result:** `400 Bad Request` — a System Administrator account can never be created through the API, per `04-authentication-authorization.md` §3a.

### TC-IDN-027 — Create staff account rejected: username already in use
- **Related UC / Rule:** UC-24 flow 2a; Identity.2
- **Priority:** P0 · **Type:** Negative
- **Preconditions:** A user (staff or student) already exists with the requested username.
- **Steps:** As SYSTEM_ADMINISTRATOR, `POST /api/v1/staff-accounts` with that username.
- **Expected Result:** `409 Conflict`; no account created or overwritten.

---

## UC-25: Deactivate/Reactivate Staff Account

### TC-IDN-028 — System Administrator disables an active staff account
- **Related UC / Rule:** UC-25 main flow; Identity.7
- **Priority:** P0 · **Type:** Functional
- **Preconditions:** An enabled staff account (chain from TC-IDN-024).
- **Steps:** As SYSTEM_ADMINISTRATOR, `PATCH /api/v1/staff-accounts/{id}/status` with `{enabled: false}`.
- **Expected Result:** `200 OK` with `{username, enabled: false}`.

### TC-IDN-029 — System Administrator re-enables a disabled staff account
- **Related UC / Rule:** UC-25 main flow; Identity.7
- **Priority:** P0 · **Type:** Functional
- **Preconditions:** A disabled staff account (chain from TC-IDN-028).
- **Steps:** As SYSTEM_ADMINISTRATOR, `PATCH /api/v1/staff-accounts/{id}/status` with `{enabled: true}`.
- **Expected Result:** `200 OK` with `{username, enabled: true}`; the account can log in again (TC-IDN-030).

### TC-IDN-030 — Login rejected: disabled account
- **Related UC / Rule:** `04-authentication-authorization.md` §4.1 (`enabled = false` branch); Identity.7
- **Priority:** P0 · **Type:** Security-Negative
- **Preconditions:** A disabled staff account (chain from TC-IDN-028) with otherwise-correct credentials.
- **Steps:** `POST /api/v1/auth/login` with the disabled account's correct username and password.
- **Expected Result:** `401 Unauthorized`, with the same generic error shape as TC-IDN-002/003 — the response does not reveal that the account exists and is merely disabled, as opposed to the credentials being wrong.

---

## Demo Accounts (development/testing convenience)

### TC-IDN-031 — Demo-accounts endpoint returns exactly the 5 fixed identities when enabled
- **Related UC / Rule:** `04-authentication-authorization.md` §8; see also [cross-cutting.md](./cross-cutting.md) TC-XC-042
- **Priority:** P1 · **Type:** Functional
- **Preconditions:** Built/run with `app.demo-accounts.enabled=true` (test profile).
- **Steps:** `GET /api/v1/auth/demo-accounts` with no session cookie.
- **Expected Result:** `200 OK`; an array of exactly 5 entries, one per role (`SYSTEM_ADMINISTRATOR`, `REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR`, `STUDENT`), each `{role, username, password}`. Every returned `{username, password}` pair successfully logs in via TC-IDN-001's flow.

### TC-IDN-032 — Demo-accounts route does not exist when disabled
- **Related UC / Rule:** `04-authentication-authorization.md` §8; `06-low-level-design.md` §11.4; see also [cross-cutting.md](./cross-cutting.md) TC-XC-042; P0 risk per [01-test-strategy.md](../01-test-strategy.md) §6
- **Priority:** P0 · **Type:** Security
- **Preconditions:** Built/run with the `prod` profile (`app.demo-accounts.enabled=false`).
- **Steps:** `GET /api/v1/auth/demo-accounts` with no session cookie.
- **Expected Result:** `404 Not Found`, identical in shape to any other unmapped path — not `403`. Confirms `DemoAccountsController` is never registered as a bean in this profile, not merely access-blocked, and that none of the 5 demo accounts' seed rows exist in a `prod`-migrated database.

---

## Traceability Summary

| UC / US | Test Case IDs |
| --- | --- |
| UC-21 / US-6.1 | TC-IDN-001–005 |
| UC-22 / US-6.2 | TC-IDN-006–015 |
| UC-23 / US-6.3 | TC-IDN-016–018 |
| UC-16 / US-5.4 | TC-IDN-019–023 |
| UC-24 / US-7.1 | TC-IDN-024–027 |
| UC-25 / US-7.2 | TC-IDN-028–030 |
| Demo accounts | TC-IDN-031–032 |

Account auto-provisioning (UC-1 tail): [student.md](./student.md) TC-STU-008–010. Full RBAC matrix, must-change-password gate, and staff-account/demo-account RBAC: [cross-cutting.md](./cross-cutting.md) §1–2, §9.
