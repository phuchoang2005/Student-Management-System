# Test Cases — `enrollment` Module

Testing Documentation — [Test Strategy](../01-test-strategy.md) → [Test Plan](../02-test-plan.md) → Test Cases (`enrollment`) → [Test Data Preparation](../04-test-data-preparation.md).

Covers **UC-11** (Enroll Student in Course), **UC-12** (End Enrollment), **UC-20** (View Enrollment Detail), and related user stories US-4.1–4.2, US-5.5. Endpoints: `GET /enrollments?studentCode|courseCode`, `POST /enrollments`, `GET /enrollments/{studentCode}/{courseCode}`, `DELETE /enrollments/{studentCode}/{courseCode}` — all keyed on **business codes**, never a surrogate id (`api-specification.md` §5 decision #9). Note: `Enrollment` has no update use case (`06-low-level-design.md` §7) — there is no `PUT` endpoint and no optimistic-locking case for this aggregate. Role authorization: see [cross-cutting.md](./cross-cutting.md) §1. Student self-service enrollment/withdrawal is explicitly out of scope (UC-11/UC-12 note), and a Student cannot read these endpoints either: role STUDENT holds no grant on `/enrollments/**` at all, and reads its own courses through `GET /me/courses` (UC-16) instead — see [cross-cutting.md](./cross-cutting.md) TC-XC-045.

---

## UC-11: Enroll Student in Course

### TC-ENR-001 — Enroll an existing student in an existing course
- **Related UC / Rule:** UC-11 main flow; Enrollment.1–3
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `student-valid-01`, `course-valid-01` (no prior enrollment between them)
- **Steps:** `POST /api/v1/enrollments` with `{studentCode, courseCode}`.
- **Expected Result:** `201 Created`; an active enrollment links the two.

### TC-ENR-002 — Enroll rejected: duplicate enrollment
- **Related UC / Rule:** UC-11 flow 4a; Enrollment.1
- **Priority:** P0 · **Type:** Negative
- **Preconditions:** `student-valid-01` is already enrolled in `course-valid-01`.
- **Steps:** `POST /api/v1/enrollments` with the same `{studentCode, courseCode}` pair again.
- **Expected Result:** `409 Conflict` (`DuplicateEnrollmentException`).

### TC-ENR-003 — Enroll rejected: course does not exist
- **Related UC / Rule:** UC-11 flow 3a; Enrollment.2
- **Priority:** P0 · **Type:** Negative
- **Steps:** `POST /api/v1/enrollments` with `courseCode = does-not-exist`.
- **Expected Result:** `400 Bad Request` (`UnknownCourseException`) — unknown FK reference, per `api-specification.md` §5.2.

### TC-ENR-004 — Enroll rejected: student does not exist
- **Related UC / Rule:** UC-11 flow 2a; Enrollment.3
- **Priority:** P0 · **Type:** Negative
- **Steps:** `POST /api/v1/enrollments` with `studentCode = <non-existent>`.
- **Expected Result:** `400 Bad Request` (`UnknownStudentException`).

### TC-ENR-005 — A student may hold multiple distinct enrollments simultaneously
- **Related UC / Rule:** req.md §3 "Student ↔ Course — Enrollment" (one student may enroll in many courses)
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `student-valid-01`, `course-valid-01`, `course-valid-02`
- **Steps:** Enroll the same student in two different courses.
- **Expected Result:** Both enrollments succeed and coexist; neither is treated as a duplicate of the other.

### TC-ENR-006 — A course may have multiple students enrolled simultaneously
- **Related UC / Rule:** req.md §3 "Student ↔ Course — Enrollment" (one course may have many students)
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `student-valid-01`, `student-valid-02`, `course-valid-01`
- **Steps:** Enroll two different students in the same course.
- **Expected Result:** Both enrollments succeed; `GET /api/v1/enrollments?courseCode={code}` — the course's roster — shows both.

---

## UC-12: End Enrollment

### TC-ENR-007 — End an active enrollment
- **Related UC / Rule:** UC-12 main flow; Enrollment.4
- **Priority:** P0 · **Type:** Functional
- **Preconditions:** `student-valid-01` is enrolled in `course-valid-01`.
- **Steps:** `DELETE /api/v1/enrollments/{studentCode}/{courseCode}`.
- **Expected Result:** `200/204`; the (student, course) pair is no longer an active enrollment.

### TC-ENR-008 — Ending an enrollment leaves both the student and the course unaffected
- **Related UC / Rule:** UC-12 postconditions; Enrollment.4
- **Priority:** P0 · **Type:** Functional
- **Steps:** After TC-ENR-007, `GET` both the student and the course.
- **Expected Result:** Both records still exist and are otherwise unchanged — only the link was removed.

### TC-ENR-009 — End enrollment rejected: no active enrollment exists for the pair
- **Related UC / Rule:** UC-12 preconditions
- **Priority:** P1 · **Type:** Negative
- **Steps:** `DELETE /api/v1/enrollments/{studentCode}/{courseCode}` for a pair with no active enrollment.
- **Expected Result:** `404 Not Found`.

### TC-ENR-010 — A student may re-enroll in a course after a prior enrollment there was ended
- **Related UC / Rule:** Enrollment.1 (uniqueness is on the *active* pairing, not historical); UC-11 + UC-12 combined
- **Priority:** P1 · **Type:** Functional
- **Steps:** Enroll, end the enrollment, then enroll the same (student, course) pair again.
- **Expected Result:** The second enrollment succeeds — ending an enrollment fully clears the pairing, it doesn't leave a residual record blocking re-enrollment.

---

## UC-20: View Enrollment Detail

### TC-ENR-011 — View full detail of an active enrollment
- **Related UC / Rule:** UC-20 main flow
- **Priority:** P1 · **Type:** Functional
- **Preconditions:** `student-valid-01` is enrolled in `course-valid-01`.
- **Steps:** `GET /api/v1/enrollments/{studentCode}/{courseCode}`.
- **Expected Result:** `200 OK`; response includes the linked student's summary and the linked course's summary.

### TC-ENR-012 — View detail for an enrollment ended since the list was shown
- **Related UC / Rule:** UC-20 flow 2a; Enrollment.4
- **Priority:** P2 · **Type:** Negative
- **Steps:** Note an active enrollment; end it; then `GET /api/v1/enrollments/{studentCode}/{courseCode}`.
- **Expected Result:** `404 Not Found`.

---

---

## 6. Enroll a student in several courses at once (UC-26 / US-4.3)

Covers **UC-26**. Endpoint: `POST /api/v1/enrollments/batch`.

The cases below all exist to pin one property: **a rejected course costs only itself.** TC-ENR-023 is the load-bearing one — it is what fails if `EnrollmentBatchService` is ever folded back into `EnrollmentService` as a plain loop, because Spring's proxy would be bypassed by self-invocation and all courses would share one transaction.

### TC-ENR-022 — Several courses are enrolled in one request
- **Related UC / Rule:** UC-26; Enrollment.1–3
- **Priority:** P0 · **Type:** Functional
- **Preconditions:** A student and three courses exist; the student is enrolled in none of them.
- **Test Data:** Registrar session (see [04-test-data-preparation.md](../04-test-data-preparation.md) §1)
- **Steps:** `POST /api/v1/enrollments/batch` with `{"studentCode":"...","courseCodes":["A","B","C"]}`.
- **Expected Result:** `200 OK`; `requested = 3`, `enrolled = 3`, `failed = 0`; every `results[].status` is `ENROLLED` with an `enrolledAt` and no `message`. A follow-up `GET /api/v1/enrollments?studentCode=` reports `totalElements = 3`.

### TC-ENR-023 — A rejected course does not undo the ones that succeeded
- **Related UC / Rule:** UC-26; `api-specification.md` §5 decision #12
- **Priority:** P0 · **Type:** Functional
- **Preconditions:** A student and two real courses exist.
- **Steps:** `POST /api/v1/enrollments/batch` with `courseCodes` = `[<real>, <nonexistent>, <real>]`. Then read the enrollments back in a **separate** request.
- **Expected Result:** `200 OK`; `enrolled = 2`, `failed = 1`; `results[1].status = UNKNOWN_COURSE` with a `message` and no `enrolledAt`. The separate read reports `totalElements = 2` — the two successful enrollments are committed and were not rolled back. Reading them back in a second request is the point: within the same response the counts could be reported by a transaction that later rolled back.

### TC-ENR-024 — A course the student is already in is reported, not fatal
- **Related UC / Rule:** UC-26; Enrollment.1
- **Priority:** P0 · **Type:** Negative
- **Steps:** Enroll the student in course A. Then `POST /api/v1/enrollments/batch` with `["A","B"]`.
- **Expected Result:** `200 OK`; `enrolled = 1`, `failed = 1`; `results[0].status = ALREADY_ENROLLED`, `results[1].status = ENROLLED`. Note this is **not** the `409` the single-enrollment endpoint gives — a duplicate is an outcome inside a successful request here.

### TC-ENR-025 — A course repeated within one request is collapsed
- **Related UC / Rule:** UC-26
- **Priority:** P1 · **Type:** Boundary
- **Steps:** `POST /api/v1/enrollments/batch` with `["A","A"]`.
- **Expected Result:** `200 OK`; `requested = 1`, `enrolled = 1`, `results` has exactly one entry. Reporting `ENROLLED` then `ALREADY_ENROLLED` for the same code would be accurate about what happened and indistinguishable from a defect.

### TC-ENR-026 — An unknown student rejects the whole request
- **Related UC / Rule:** UC-26; Enrollment.3; `api-specification.md` §5 decision #2
- **Priority:** P0 · **Type:** Negative
- **Steps:** `POST /api/v1/enrollments/batch` naming a student code that resolves to nothing, with one valid course.
- **Expected Result:** `400 Bad Request` in the `Error` envelope, with **no** `results` field. Nothing is enrolled. The student is the subject of the request rather than one of its items, so an unknown one leaves every course unanswerable — unlike an unknown course, which is a per-course outcome (TC-ENR-023).

### TC-ENR-027 — An empty or oversized course list is rejected
- **Related UC / Rule:** UC-26
- **Priority:** P1 · **Type:** Boundary
- **Steps:** `POST /api/v1/enrollments/batch` with `courseCodes: []`; then again with 51 codes.
- **Expected Result:** `400 Bad Request` both times, in the standard `ValidationError` envelope with `courseCodes` in `errors[]`. The cap is 50 because each course costs its own transaction and round trip.

### TC-ENR-028 — Only the Registrar may enroll in bulk
- **Related UC / Rule:** `04-authentication-authorization.md` §6
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As Course Administrator: `POST /api/v1/enrollments/batch`.
- **Expected Result:** `403 Forbidden`. The existing `POST /api/v1/enrollments/**` matcher already covers `/batch`; this case pins that it does, so the sub-path cannot be added later without inheriting the rule.

---

## Traceability Summary

| UC / US | Test Case IDs |
| --- | --- |
| UC-11 / US-4.1 | TC-ENR-001–006 |
| UC-12 / US-4.2 | TC-ENR-007–010 |
| UC-20 / US-5.5 | TC-ENR-011–012, TC-ENR-016–021 |
| UC-26 / US-4.3 | TC-ENR-022–028 |

Cross-module cascade behavior — enrollments removed automatically when their student or course is deleted — is covered in [student.md](./student.md) TC-STU-023/025, [course.md](./course.md) TC-CRS-015, and [cross-cutting.md](./cross-cutting.md) §4, not repeated here.

---

## 5. List enrollments, by student or by course

`GET /api/v1/enrollments` is the list both the detail cases above are reached from. Exactly one of `studentCode` or `courseCode` is required, and both directions return the same row shape (`{student, course, enrolledAt}`), because they are the same rows viewed from different ends.

### TC-ENR-016 — Filtering by `studentCode` lists that student's enrolled courses
- **Related UC / Rule:** UC-11, UC-20; `03-sequence-diagrams.md` §5.4
- **Priority:** P0 · **Type:** Functional
- **Steps:** As Registrar, enroll one student in two courses, then `GET /api/v1/enrollments?studentCode={code}`.
- **Expected Result:** `200 OK`; `totalElements = 2`; every row's `student.studentCode` equals the filter; the two `course.courseCode` values are both present. No `id` field appears on either side of any row.

### TC-ENR-017 — Filtering by `courseCode` lists that course's roster
- **Related UC / Rule:** UC-19, UC-20; `03-sequence-diagrams.md` §5.4
- **Priority:** P0 · **Type:** Functional
- **Steps:** As Course Administrator, enroll two students in one course, then `GET /api/v1/enrollments?courseCode={code}`.
- **Expected Result:** `200 OK`; `totalElements = 2`; every row's `course.courseCode` equals the filter; both students appear.

### TC-ENR-018 — Neither filter, or both filters, is rejected
- **Related UC / Rule:** `api-specification.md` §5 decision #2
- **Priority:** P0 · **Type:** Negative
- **Steps:** As Registrar: `GET /api/v1/enrollments` with no parameters; then again with both `studentCode` and `courseCode`.
- **Expected Result:** `400 Bad Request` both times, with both field names in `errors[]`. This is deliberate rather than defaulting to something: with neither filter the endpoint would enumerate every enrollment in the system, which no use case asks for; with both, the answer is the single enrollment `GET /enrollments/{studentCode}/{courseCode}` already addresses.

### TC-ENR-019 — An unresolvable filter code is rejected, not answered with an empty page
- **Related UC / Rule:** `api-specification.md` §5 decision #2
- **Priority:** P1 · **Type:** Negative
- **Steps:** As Registrar: `GET /api/v1/enrollments?studentCode=<no such student>`; then `?courseCode=<no such course>`.
- **Expected Result:** `400 Bad Request` in both cases. A code supplied as a *reference* is malformed input when it resolves to nothing — distinct from the `404` the same unresolvable code produces on `GET /enrollments/{studentCode}/{courseCode}`, where it is part of the *address* of one enrollment (see TC-ENR-014's note).

### TC-ENR-020 — The roster pages
- **Related UC / Rule:** `api-specification.md` §3 (pagination)
- **Priority:** P2 · **Type:** Functional
- **Steps:** Enroll two students in one course, then `GET /api/v1/enrollments?courseCode={code}&size=1`.
- **Expected Result:** `200 OK`; `content` has 1 entry; `totalElements = 2`; `totalPages = 2`.

### TC-ENR-021 — Only the Registrar and Course Administrator may list enrollments
- **Related UC / Rule:** `04-authentication-authorization.md` §6.1
- **Priority:** P0 · **Type:** Security-RBAC
- **Steps:** As Librarian, then as a real logged-in Student: `GET /api/v1/enrollments?studentCode={code}`.
- **Expected Result:** `403 Forbidden` for both — including the Student asking about their own code.
