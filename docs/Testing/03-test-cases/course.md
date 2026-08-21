# Test Cases — `course` Module

Testing Documentation — [Test Strategy](../01-test-strategy.md) → [Test Plan](../02-test-plan.md) → Test Cases (`course`) → [Test Data Preparation](../04-test-data-preparation.md).

Covers **UC-8** (Create Course), **UC-9** (Update Course), **UC-10** (Remove Course), **UC-15** (View/Search Courses), **UC-19** (View Course Detail), and related user stories US-3.1–3.3, US-5.3. Endpoints: `POST /courses`, `GET /courses`, `GET /courses/{code}`, `PUT /courses/{code}`, `DELETE /courses/{code}`. Role authorization: see [cross-cutting.md](./cross-cutting.md) §1.

---

## UC-8: Create Course

### TC-CRS-001 — Create a course with fully valid data
- **Related UC / Rule:** UC-8 main flow; Course.1–3
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `course-valid-01`
- **Steps:** `POST /api/v1/courses` with valid `courseCode`, `name`, `description`, `credits`.
- **Expected Result:** `201 Created`.

### TC-CRS-002 — Create rejected: duplicate course code
- **Related UC / Rule:** UC-8 flow 2a; Course.1
- **Priority:** P0 · **Type:** Negative
- **Test Data:** `course-valid-01` (pre-seeded)
- **Steps:** `POST /api/v1/courses` reusing its `courseCode`.
- **Expected Result:** `409 Conflict` (`DuplicateCodeException`).

### TC-CRS-003 — Create rejected: blank name
- **Related UC / Rule:** UC-8 flow 3a; Course.2
- **Priority:** P1 · **Type:** Negative
- **Steps:** `POST /api/v1/courses` with `name = ""`.
- **Expected Result:** `400 Bad Request`.

### TC-CRS-004 — Create rejected: credits is zero
- **Related UC / Rule:** UC-8 flow 4a; Course.3
- **Priority:** P1 · **Type:** Boundary/Negative
- **Test Data:** `course-invalid-credits-zero`
- **Steps:** `POST /api/v1/courses` with `credits = 0`.
- **Expected Result:** `400 Bad Request` — `CHECK (credits > 0)` boundary; zero is the first invalid value below the minimum valid value of 1.

### TC-CRS-005 — Create rejected: credits is negative
- **Related UC / Rule:** UC-8 flow 4a; Course.3
- **Priority:** P1 · **Type:** Negative
- **Test Data:** `course-invalid-credits-negative`
- **Steps:** `POST /api/v1/courses` with `credits = -1`.
- **Expected Result:** `400 Bad Request`.

### TC-CRS-006 — Create accepted: credits at the minimum valid value
- **Related UC / Rule:** Course.3
- **Priority:** P2 · **Type:** Boundary
- **Test Data:** `course-boundary-credits-1`
- **Steps:** `POST /api/v1/courses` with `credits = 1`.
- **Expected Result:** `201 Created`.

### TC-CRS-007 — `courseCode` at the `VARCHAR(20)` boundary is accepted
- **Related UC / Rule:** `05-database-schema.md` §3.2
- **Priority:** P2 · **Type:** Boundary
- **Test Data:** `course-boundary-code-20chars`
- **Steps:** `POST /api/v1/courses` with a 20-character `courseCode`.
- **Expected Result:** `201 Created`.

---

## UC-9: Update Course

### TC-CRS-008 — Update name, description, and credits successfully
- **Related UC / Rule:** UC-9 main flow; Course.2–3
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `course-valid-01`
- **Steps:** `PUT /api/v1/courses/{code}` with new `name`, `description`, `credits`.
- **Expected Result:** `200 OK`; changes reflected on subsequent `GET`.

### TC-CRS-009 — Update rejected: blank name
- **Related UC / Rule:** UC-9 flow 2a; Course.2
- **Priority:** P1 · **Type:** Negative
- **Steps:** `PUT /api/v1/courses/{code}` with `name = ""`.
- **Expected Result:** `400 Bad Request`.

### TC-CRS-010 — Update rejected: non-positive credits
- **Related UC / Rule:** UC-9 flow 3a; Course.3
- **Priority:** P1 · **Type:** Negative
- **Steps:** `PUT /api/v1/courses/{code}` with `credits = 0`.
- **Expected Result:** `400 Bad Request`.

### TC-CRS-011 — Course code is immutable
- **Related UC / Rule:** UC-9 postconditions; Course.1
- **Priority:** P1 · **Type:** Functional
- **Steps:** `PUT /api/v1/courses/{code}` attempting to also change `courseCode`.
- **Expected Result:** Persisted `courseCode` is unchanged regardless of implementation choice (ignore vs. reject) — see the equivalent note at [student.md](./student.md) TC-STU-017.

### TC-CRS-012 — Update rejected: course does not exist
- **Related UC / Rule:** UC-9 preconditions
- **Priority:** P1 · **Type:** Negative
- **Steps:** `PUT /api/v1/courses/does-not-exist` with a valid body.
- **Expected Result:** `404 Not Found`.

### TC-CRS-013 — Update rejected: concurrent modification (stale version)
- **Related UC / Rule:** `06-low-level-design.md` §10 (optimistic locking)
- **Priority:** P0 · **Type:** Concurrency
- **Steps:** Two clients load the same course; one updates successfully; the second submits using the stale version.
- **Expected Result:** Second request returns `409 Conflict` (`StaleWriteException`). (General pattern cataloged in [cross-cutting.md](./cross-cutting.md) §3.)

---

## UC-10: Remove Course

### TC-CRS-014 — Remove a course with no enrollments
- **Related UC / Rule:** UC-10 main flow
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `course-valid-02` (no enrollments)
- **Steps:** `DELETE /api/v1/courses/{code}`.
- **Expected Result:** `200/204`; subsequent `GET` returns `404`.

### TC-CRS-015 — Removing a course removes every enrollment tied to it without affecting the students
- **Related UC / Rule:** UC-10 step 2; req.md §5 "When a course is removed"
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `course-with-enrollments-01` (enrolled: `student-valid-01`, `student-valid-02`)
- **Steps:** `DELETE /api/v1/courses/{code}`; then `GET /api/v1/students/{code}` for each formerly-enrolled student.
- **Expected Result:** Course no longer exists; both students still exist, unaffected; neither shows this course under their enrollments.

### TC-CRS-016 — Remove rejected: course does not exist
- **Related UC / Rule:** UC-10 preconditions
- **Priority:** P2 · **Type:** Negative
- **Steps:** `DELETE /api/v1/courses/does-not-exist`.
- **Expected Result:** `404 Not Found`.

---

## UC-15: View/Search Courses

### TC-CRS-017 — Search by exact course code returns the matching record
- **Related UC / Rule:** UC-15 main flow
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `course-valid-01`
- **Steps:** `GET /api/v1/courses?code={code}`.
- **Expected Result:** `200 OK`; one summary (code, name, credits).

### TC-CRS-018 — Search by name term returns matching records
- **Related UC / Rule:** UC-15 main flow
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `course-search-set-01`
- **Steps:** `GET /api/v1/courses?q={term}`.
- **Expected Result:** `200 OK`; matching courses returned.

### TC-CRS-019 — Search with no match returns an empty list
- **Related UC / Rule:** UC-15 flow 2a
- **Priority:** P2 · **Type:** Boundary
- **Steps:** `GET /api/v1/courses?code=does-not-exist`.
- **Expected Result:** `200 OK`; empty array.

---

## UC-19: View Course Detail

### TC-CRS-020 — View full detail; the roster is a separate request
- **Related UC / Rule:** UC-19 main flow; `api-specification.md` §5 decision #10
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `course-with-enrollments-01`
- **Steps:** `GET /api/v1/courses/{code}`, then `GET /api/v1/enrollments?courseCode={code}`.
- **Expected Result:** the first call is `200 OK` with all course fields and **no `roster` field and no `id` field**; the second returns the enrolled students as a page. The roster was moved out of the course response deliberately: it is granted to the Registrar and Course Administrator but not to a Student browsing the catalogue, and embedding it would have handed every reader of a course record the names and email addresses of everyone taking it.

### TC-CRS-021 — A Student may open a course but never receives its roster
- **Related UC / Rule:** UC-19; `04-authentication-authorization.md` §6.1
- **Priority:** P0 · **Type:** Security-RBAC
- **Test Data:** `course-with-enrollments-01`
- **Steps:** As a real logged-in Student: `GET /api/v1/courses/{code}`, then `GET /api/v1/enrollments?courseCode={code}`.
- **Expected Result:** `200 OK` for the course record (the catalogue is not personal data); `403 Forbidden` for the roster.

### TC-CRS-022 — View detail for a course removed since the search ran
- **Related UC / Rule:** UC-19 flow 2a
- **Priority:** P2 · **Type:** Negative
- **Steps:** Note a course's code; delete it; then `GET /api/v1/courses/{code}`.
- **Expected Result:** `404 Not Found`.

---

## UC-15: View/Search Courses — Pagination

### TC-CRS-023 — Default `page`/`size` are applied when omitted
- **Related UC / Rule:** UC-15 main flow (`api-specification.md` §3 Pagination)
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `course-search-set-01`
- **Steps:** `GET /api/v1/courses?q={term}` with no `page`/`size` given.
- **Expected Result:** `200 OK`; body is `{content, page, size, totalElements, totalPages}` with `page: 0` and `size: 20`.

### TC-CRS-024 — Custom `page`/`size` correctly slices the result set
- **Related UC / Rule:** UC-15 flow 2b
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `course-search-set-01`
- **Steps:** `GET /api/v1/courses?q={term}&page=0&size=2`, then `GET /api/v1/courses?q={term}&page=1&size=2`.
- **Expected Result:** Each call returns at most 2 records; `totalElements`/`totalPages` are consistent across both calls; no record appears on both pages.

### TC-CRS-025 — A page past the last page returns an empty page, not an error
- **Related UC / Rule:** UC-15 flow 2b (boundary)
- **Priority:** P2 · **Type:** Boundary
- **Test Data:** `course-search-set-01`
- **Steps:** `GET /api/v1/courses?q={term}&page=99&size=20`.
- **Expected Result:** `200 OK`; `content` is an empty array; `totalElements`/`totalPages` still reflect the real result set.

## UC-19: View Course Detail — Roster Pagination

The roster is now its own endpoint, so its pagination cases live with it: see [enrollment.md](./enrollment.md) TC-ENR-017 and TC-ENR-020. The two cases below are what remains specific to the course side.

### TC-CRS-026 — Course detail takes no paging parameters
- **Related UC / Rule:** UC-19; `api-specification.md` §5 decision #10
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `course-with-enrollments-01`
- **Steps:** `GET /api/v1/courses/{code}?page=1&size=2`.
- **Expected Result:** `200 OK` with the course record; the paging parameters are simply ignored, because this response has no paginated field left. They previously paged the embedded `roster`.

### TC-CRS-027 — A course with no enrollments has an empty roster, not an error
- **Related UC / Rule:** UC-19 (boundary)
- **Priority:** P2 · **Type:** Boundary
- **Test Data:** `course-valid-02`
- **Steps:** `GET /api/v1/courses/{code}`, then `GET /api/v1/enrollments?courseCode={code}`.
- **Expected Result:** `200 OK` for both; the second returns `content: []` with `totalElements: 0` — not `404`, since the course itself exists.

---

## Traceability Summary

| UC / US | Test Case IDs |
| --- | --- |
| UC-8 / US-3.1 | TC-CRS-001–007 |
| UC-9 / US-3.2 | TC-CRS-008–013 |
| UC-10 / US-3.3 | TC-CRS-014–016 |
| UC-15 / US-5.3 | TC-CRS-017–019, TC-CRS-023–025 |
| UC-19 / US-5.3, US-5.4 | TC-CRS-020–022, TC-CRS-026–027 |
