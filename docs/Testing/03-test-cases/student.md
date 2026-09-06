# Test Cases — `student` Module

Testing Documentation — [Test Strategy](../01-test-strategy.md) → [Test Plan](../02-test-plan.md) → Test Cases (`student`) → [Test Data Preparation](../04-test-data-preparation.md).

Covers **UC-1** (Register Student), **UC-2** (Update Student Details), **UC-3** (Remove Student), **UC-13** (View/Search Students), **UC-17** (View Student Detail), and their related user stories US-1.1–1.3, US-5.1. Endpoints: `POST /students`, `GET /students`, `GET /students/{code}`, `PUT /students/{code}`, `DELETE /students/{code}`. Test data references are IDs defined in [04-test-data-preparation.md](../04-test-data-preparation.md). Role/endpoint authorization cases (who *may* call these endpoints) are centralized in [cross-cutting.md](./cross-cutting.md) §1 — this file assumes a correctly-authorized Registrar caller unless a case is specifically testing the identity side-effect of UC-1.

---

## UC-1: Register Student

### TC-STU-001 — Register a student with fully valid data
- **Related UC / Rule:** UC-1 main flow; Student.1–4
- **Priority:** P0 · **Type:** Functional
- **Preconditions:** No student exists with the given code or email.
- **Test Data:** `student-valid-01`
- **Steps:** `POST /api/v1/students` with valid `studentCode`, `firstName`, `lastName`, `email`, `dateOfBirth`.
- **Expected Result:** `201 Created`; response body is the created student record.

### TC-STU-002 — Register rejected: duplicate student code
- **Related UC / Rule:** UC-1 flow 3a; Student.1
- **Priority:** P0 · **Type:** Negative
- **Preconditions:** A student with `studentCode = student-valid-01.studentCode` already exists.
- **Test Data:** `student-valid-01` (pre-seeded), then a second request reusing its code with a different email.
- **Steps:** `POST /api/v1/students` with the already-used code.
- **Expected Result:** `409 Conflict` (`DuplicateCodeException`); no new student or account is created.

### TC-STU-003 — Register rejected: duplicate email
- **Related UC / Rule:** UC-1 flow 3b; Student.2
- **Priority:** P0 · **Type:** Negative
- **Preconditions:** A student with the given email already exists.
- **Test Data:** `student-valid-01` (pre-seeded), then a new code with its email.
- **Steps:** `POST /api/v1/students` with a duplicate email.
- **Expected Result:** `409 Conflict` (`DuplicateEmailException`) — per `api-specification.md` §5.1, duplicate is `409`, distinct from malformed (`400`, see TC-STU-004).

### TC-STU-004 — Register rejected: malformed email
- **Related UC / Rule:** UC-1 flow 3b; Student.2
- **Priority:** P0 · **Type:** Negative
- **Test Data:** `student-invalid-email-01` (e.g. `"not-an-email"`)
- **Steps:** `POST /api/v1/students` with a malformed email string.
- **Expected Result:** `400 Bad Request` (`InvalidEmailException`) — format failure, not a uniqueness conflict.

### TC-STU-005 — Register rejected: blank first name
- **Related UC / Rule:** UC-1 flow 4a; Student.3
- **Priority:** P1 · **Type:** Negative
- **Steps:** `POST /api/v1/students` with `firstName = ""`.
- **Expected Result:** `400 Bad Request` (`DomainValidationException`).

### TC-STU-006 — Register rejected: blank last name
- **Related UC / Rule:** UC-1 flow 4a; Student.3
- **Priority:** P1 · **Type:** Negative
- **Steps:** `POST /api/v1/students` with `lastName = ""`.
- **Expected Result:** `400 Bad Request` (`DomainValidationException`).

### TC-STU-007 — Register rejected: invalid date of birth
- **Related UC / Rule:** UC-1 flow 5a; Student.4
- **Priority:** P1 · **Type:** Negative
- **Test Data:** `student-invalid-dob-01` (e.g. `"2023-02-30"`, a non-existent calendar date)
- **Steps:** `POST /api/v1/students` with an invalid DOB value.
- **Expected Result:** `400 Bad Request` (`DomainValidationException`).

### TC-STU-008 — Registration auto-provisions exactly one user account
- **Related UC / Rule:** UC-1 step 6a; Identity.1–2
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `student-valid-02`
- **Steps:** `POST /api/v1/students`; then query the created account (e.g. attempt login, or via `GET /students/{code}/initial-password` as Registrar).
- **Expected Result:** Exactly one account exists with `username = email` and `role = STUDENT`.

### TC-STU-009 — Auto-provisioned account starts with an 8-character initial password and must-change-password set
- **Related UC / Rule:** UC-1 step 6a; Identity.3
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `student-valid-02`
- **Steps:** Register the student; inspect the `201` response's returned initial password.
- **Expected Result:** Initial password is present, 8 characters, alphanumeric; a subsequent login with it reports `mustChangePassword: true` (see [identity-auth.md](./identity-auth.md) TC-IDN-001).

### TC-STU-010 — Initial password is returned exactly once at registration
- **Related UC / Rule:** UC-1 step 7; Identity.3, Identity.5
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `student-valid-02`
- **Steps:** Register the student; capture the response. Re-fetch the student via `GET /students/{code}`.
- **Expected Result:** The `201` response is the only place the plaintext password appears in this flow; `GET /students/{code}` never includes it (retrievable afterward only via UC-23, see [identity-auth.md](./identity-auth.md) TC-IDN-009–011).

### TC-STU-011 — `studentCode` at the `VARCHAR(20)` boundary is accepted
- **Related UC / Rule:** UC-1 main flow; `05-database-schema.md` §3.1
- **Priority:** P2 · **Type:** Boundary
- **Test Data:** `student-boundary-code-20chars`
- **Steps:** `POST /api/v1/students` with a 20-character `studentCode`.
- **Expected Result:** `201 Created`.

### TC-STU-012 — `studentCode` exceeding 20 characters is rejected
- **Related UC / Rule:** UC-1 main flow; `05-database-schema.md` §3.1
- **Priority:** P2 · **Type:** Boundary
- **Test Data:** `student-boundary-code-21chars`
- **Steps:** `POST /api/v1/students` with a 21-character `studentCode`.
- **Expected Result:** `400 Bad Request` — application-level validation rejects before it would otherwise overflow the `VARCHAR(20)` column.

---

## UC-2: Update Student Details

### TC-STU-013 — Update name, email, and DOB successfully
- **Related UC / Rule:** UC-2 main flow; Student.2–4
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `student-valid-01` (pre-existing)
- **Steps:** `PUT /api/v1/students/{code}` with new `firstName`, `email`, `dateOfBirth`.
- **Expected Result:** `200 OK`; `GET` on the same student reflects the new values.

### TC-STU-014 — Update rejected: new email collides with another student
- **Related UC / Rule:** UC-2 flow 2a; Student.2
- **Priority:** P0 · **Type:** Negative
- **Test Data:** `student-valid-01`, `student-valid-02` (both pre-existing)
- **Steps:** `PUT /api/v1/students/{student-valid-01.code}` with `email = student-valid-02.email`.
- **Expected Result:** `409 Conflict` (`DuplicateEmailException`); `student-valid-01`'s email is unchanged.

### TC-STU-015 — Update rejected: blank name field
- **Related UC / Rule:** UC-2 flow 3a; Student.3
- **Priority:** P1 · **Type:** Negative
- **Steps:** `PUT /api/v1/students/{code}` with `lastName = ""`.
- **Expected Result:** `400 Bad Request`.

### TC-STU-016 — Update rejected: invalid date of birth
- **Related UC / Rule:** UC-2 flow 4a; Student.4
- **Priority:** P1 · **Type:** Negative
- **Steps:** `PUT /api/v1/students/{code}` with an invalid DOB.
- **Expected Result:** `400 Bad Request`.

### TC-STU-017 — Student code is immutable
- **Related UC / Rule:** UC-2 postcondition; Student.1
- **Priority:** P1 · **Type:** Functional
- **Steps:** `PUT /api/v1/students/{code}` with a body attempting to also change `studentCode`.
- **Expected Result:** Either the field is ignored (student code unchanged in the persisted record) or the request is rejected — whichever the implemented contract specifies; the persisted `studentCode` must not change either way. **Flag during implementation which behavior is chosen**, since `use-cases.md` doesn't specify a request/reject choice explicitly.

### TC-STU-018 — Changing a student's email updates the linked account's username
- **Related UC / Rule:** req.md §3 "Student ↔ User Account"
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `student-valid-01`
- **Steps:** `PUT /api/v1/students/{code}` changing `email` to a new unique address; then attempt login with the new email as username (see [identity-auth.md](./identity-auth.md)).
- **Expected Result:** Login succeeds with the new email as username; the old username no longer authenticates.

### TC-STU-019 — Update rejected: student does not exist
- **Related UC / Rule:** UC-2 preconditions
- **Priority:** P1 · **Type:** Negative
- **Steps:** `PUT /api/v1/students/does-not-exist` with a valid body.
- **Expected Result:** `404 Not Found`.

### TC-STU-020 — Update rejected: concurrent modification (stale version)
- **Related UC / Rule:** `06-low-level-design.md` §10 (optimistic locking)
- **Priority:** P0 · **Type:** Concurrency
- **Preconditions:** Two clients load the same student record.
- **Steps:** Client A updates and saves successfully; Client B then submits an update using the pre-A version token.
- **Expected Result:** Client B's request returns `409 Conflict` (`StaleWriteException`); Client A's change is not overwritten. (General optimistic-locking scenarios are cataloged in [cross-cutting.md](./cross-cutting.md) §3.)

---

## UC-3: Remove Student

### TC-STU-021 — Remove a student with no books or enrollments
- **Related UC / Rule:** UC-3 main flow
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `student-valid-03` (no associations)
- **Steps:** `DELETE /api/v1/students/{code}`.
- **Expected Result:** `200/204`; subsequent `GET /students/{code}` returns `404`.

### TC-STU-022 — Removing a student unassigns (not deletes) their owned books
- **Related UC / Rule:** UC-3 step 2; req.md §5 "When a student is removed"; Book.5
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `student-with-books-01` (owns `book-valid-01`, `book-valid-02`)
- **Steps:** `DELETE /api/v1/students/{code}`; then `GET /api/v1/books/{isbn}` for each formerly-owned book.
- **Expected Result:** Both books still exist (`200 OK`) with `owner: null`.

### TC-STU-023 — Removing a student removes their enrollments without affecting the courses
- **Related UC / Rule:** UC-3 step 3; req.md §5 "When a student is removed"; Enrollment.4
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `student-with-enrollments-01` (enrolled in `course-valid-01`, `course-valid-02`)
- **Steps:** `DELETE /api/v1/students/{code}`; then `GET /api/v1/courses/{code}` for each formerly-enrolled course, and `GET /api/v1/enrollments?courseCode={code}` for its roster.
- **Expected Result:** Courses still exist unchanged; the removed student no longer appears in either roster.

### TC-STU-024 — Removing a student removes their user account
- **Related UC / Rule:** UC-3 step (identity tail); req.md §5; Identity.1
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `student-with-books-01`
- **Steps:** `DELETE /api/v1/students/{code}`; then attempt login with that student's former username/either password.
- **Expected Result:** Login fails with `401 Unauthorized` — the account no longer exists.

### TC-STU-025 — Full cascade in one transaction (books unassigned + enrollments removed + account removed + student removed)
- **Related UC / Rule:** UC-3 postconditions; req.md §5 (combined)
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `student-full-cascade-01` (owns books, holds enrollments, has a login account)
- **Steps:** `DELETE /api/v1/students/{code}` once; verify all four effects (TC-STU-021 through TC-STU-024's assertions) hold simultaneously from this single call.
- **Expected Result:** All cascade effects observed together, confirming they happen atomically, not as separate manual steps. (The event-driven vs. DB-constraint-level verification split is detailed in [cross-cutting.md](./cross-cutting.md) §4.)

### TC-STU-026 — Remove rejected: student does not exist
- **Related UC / Rule:** UC-3 preconditions
- **Priority:** P2 · **Type:** Negative
- **Steps:** `DELETE /api/v1/students/does-not-exist`.
- **Expected Result:** `404 Not Found`.

---

## UC-13: View/Search Students

### TC-STU-027 — Search by exact student code returns the matching record
- **Related UC / Rule:** UC-13 main flow
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `student-valid-01`
- **Steps:** `GET /api/v1/students?code={code}`.
- **Expected Result:** `200 OK`; response contains one summary (code, name, email).

### TC-STU-028 — Search by partial name/email term returns matching records
- **Related UC / Rule:** UC-13 main flow
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `student-search-set-01` (3 students sharing a last name substring)
- **Steps:** `GET /api/v1/students?q={partial term}`.
- **Expected Result:** `200 OK`; all matching students returned, no non-matching ones included.

### TC-STU-029 — Search with no match returns an empty list, not an error
- **Related UC / Rule:** UC-13 flow 2a
- **Priority:** P2 · **Type:** Boundary
- **Steps:** `GET /api/v1/students?code=does-not-exist`.
- **Expected Result:** `200 OK`; empty array.

---

## UC-17: View Student Detail

### TC-STU-030 — View full detail including owned books and enrollments
- **Related UC / Rule:** UC-17 main flow
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `student-full-cascade-01` (before deletion — reuse a similarly-shaped fixture with books + enrollments intact)
- **Steps:** `GET /api/v1/students/{code}`.
- **Expected Result:** `200 OK`; response includes all student fields, the list of currently-owned books, and the list of current enrollments.

### TC-STU-031 — View detail for a student removed since the search ran
- **Related UC / Rule:** UC-17 flow 2a
- **Priority:** P2 · **Type:** Negative
- **Steps:** Note a student's code; delete that student; then `GET /api/v1/students/{code}`.
- **Expected Result:** `404 Not Found`, not an error about "search stale" — a plain not-found.

---

## UC-13: View/Search Students — Pagination

### TC-STU-032 — Default `page`/`size` are applied when omitted
- **Related UC / Rule:** UC-13 main flow (`api-specification.md` §3 Pagination)
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `student-search-set-01`
- **Steps:** `GET /api/v1/students?q={partial term}` with no `page`/`size` given.
- **Expected Result:** `200 OK`; body is `{content, page, size, totalElements, totalPages}` with `page: 0` and `size: 20`.

### TC-STU-033 — Custom `page`/`size` correctly slices the result set
- **Related UC / Rule:** UC-13 flow 2b
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `student-search-set-01` (3 students sharing a last name substring)
- **Steps:** `GET /api/v1/students?q={term}&page=0&size=2`, then `GET /api/v1/students?q={term}&page=1&size=2`.
- **Expected Result:** First call returns 2 records and `totalElements: 3`, `totalPages: 2`; second call returns the remaining 1 record; no record appears on both pages.

### TC-STU-034 — A page past the last page returns an empty page, not an error
- **Related UC / Rule:** UC-13 flow 2b (boundary)
- **Priority:** P2 · **Type:** Boundary
- **Test Data:** `student-search-set-01`
- **Steps:** `GET /api/v1/students?q={term}&page=99&size=20`.
- **Expected Result:** `200 OK`; `content` is an empty array; `totalElements`/`totalPages` still reflect the real result set.

---

---

## 6. Regressions

Cases that exist because the behaviour they describe was once wrong. Each fails against the defect it names.

### TC-STU-035 — Editing a student opens with the existing date of birth
- **Related UC / Rule:** UC-2 / US-1.2
- **Priority:** P0 · **Type:** Regression
- **Preconditions:** A registered student with a known date of birth.
- **Steps:** As Registrar, open the student list and press Edit on that student.
- **Expected Result:** Every field is pre-filled, **including date of birth**. Submitting a changed name succeeds and both the list and the detail page show it.
- **The defect:** the dialog was typed on `StudentSummary`, which carries no `dateOfBirth`, so the field opened empty. Being `required`, the browser's own constraint check then blocked submission before any handler ran — the Save button appeared to do nothing at all. Note this is *not* reproducible through the API: the `PUT` was always correct.

### TC-STU-036 — A course's description survives an edit
- **Related UC / Rule:** UC-9 / US-3.2
- **Priority:** P0 · **Type:** Regression
- **Steps:** As Course Administrator, edit a course that has a description, changing only its name.
- **Expected Result:** The description is pre-filled and unchanged after saving.
- **The defect:** the same shape as TC-STU-035 on `CourseFormDialog`, and quieter — `description` is not `required`, so the form submitted happily and wrote the blank back, destroying the text rather than refusing to save.

### TC-STU-037 — Registration time does not move when the record is edited
- **Related UC / Rule:** UC-1, UC-2
- **Priority:** P0 · **Type:** Regression
- **Steps:** Register a student and note `createdAt`; update them twice; read `createdAt` again. Cross-check `SELECT created_at FROM students` directly.
- **Expected Result:** Unchanged, and matching the wall clock at registration. The stored column holds UTC.
- **The defect:** reads converted MySQL's zoneless `DATETIME` at the JVM's default zone while writes used UTC, so every read was off by the offset — and because the value read was written back on update, the error compounded once per `version`. See TC-XC-046–048 for the round-trip cases; this one is the user-visible symptom that was reported.

---

## Traceability Summary

| UC / US | Test Case IDs |
| --- | --- |
| UC-1 / US-1.1 | TC-STU-001–012 |
| UC-2 / US-1.2 | TC-STU-013–020, TC-STU-035, TC-STU-037 |
| UC-3 / US-1.3 | TC-STU-021–026 |
| UC-13 / US-5.1 | TC-STU-027–029, TC-STU-032–034 |
| UC-17 / US-5.1 | TC-STU-030–031 |
| UC-9 / US-3.2 (regression only) | TC-STU-036 — see [course.md](./course.md) for UC-9's own coverage |

Role-based access (who may call each endpoint) for this module: see [cross-cutting.md](./cross-cutting.md) §1.
