# Test Cases — `enrollment` Module

Testing Documentation — [Test Strategy](../01-test-strategy.md) → [Test Plan](../02-test-plan.md) → Test Cases (`enrollment`) → [Test Data Preparation](../04-test-data-preparation.md).

Covers **UC-11** (Enroll Student in Course), **UC-12** (End Enrollment), **UC-20** (View Enrollment Detail), and related user stories US-4.1–4.2, US-5.5. Endpoints: `POST /enrollments`, `GET /enrollments/{studentId}/{courseCode}`, `DELETE /enrollments/{studentId}/{courseCode}`. Note: `Enrollment` has no update use case (`06-low-level-design.md` §7) — there is no `PUT` endpoint and no optimistic-locking case for this aggregate. Role authorization: see [cross-cutting.md](./cross-cutting.md) §1. Student self-service enrollment/withdrawal is explicitly out of scope (UC-11/UC-12 note) — a Student may only read via UC-16/UC-20.

---

## UC-11: Enroll Student in Course

### TC-ENR-001 — Enroll an existing student in an existing course
- **Related UC / Rule:** UC-11 main flow; Enrollment.1–3
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `student-valid-01`, `course-valid-01` (no prior enrollment between them)
- **Steps:** `POST /api/v1/enrollments` with `{studentId, courseCode}`.
- **Expected Result:** `201 Created`; an active enrollment links the two.

### TC-ENR-002 — Enroll rejected: duplicate enrollment
- **Related UC / Rule:** UC-11 flow 4a; Enrollment.1
- **Priority:** P0 · **Type:** Negative
- **Preconditions:** `student-valid-01` is already enrolled in `course-valid-01`.
- **Steps:** `POST /api/v1/enrollments` with the same `{studentId, courseCode}` pair again.
- **Expected Result:** `409 Conflict` (`DuplicateEnrollmentException`).

### TC-ENR-003 — Enroll rejected: course does not exist
- **Related UC / Rule:** UC-11 flow 3a; Enrollment.2
- **Priority:** P0 · **Type:** Negative
- **Steps:** `POST /api/v1/enrollments` with `courseCode = does-not-exist`.
- **Expected Result:** `400 Bad Request` (`UnknownCourseException`) — unknown FK reference, per `api-specification.md` §5.2.

### TC-ENR-004 — Enroll rejected: student does not exist
- **Related UC / Rule:** UC-11 flow 2a; Enrollment.3
- **Priority:** P0 · **Type:** Negative
- **Steps:** `POST /api/v1/enrollments` with `studentId = <non-existent>`.
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
- **Expected Result:** Both enrollments succeed; the course's roster (UC-19/UC-15) shows both.

---

## UC-12: End Enrollment

### TC-ENR-007 — End an active enrollment
- **Related UC / Rule:** UC-12 main flow; Enrollment.4
- **Priority:** P0 · **Type:** Functional
- **Preconditions:** `student-valid-01` is enrolled in `course-valid-01`.
- **Steps:** `DELETE /api/v1/enrollments/{studentId}/{courseCode}`.
- **Expected Result:** `200/204`; the (student, course) pair is no longer an active enrollment.

### TC-ENR-008 — Ending an enrollment leaves both the student and the course unaffected
- **Related UC / Rule:** UC-12 postconditions; Enrollment.4
- **Priority:** P0 · **Type:** Functional
- **Steps:** After TC-ENR-007, `GET` both the student and the course.
- **Expected Result:** Both records still exist and are otherwise unchanged — only the link was removed.

### TC-ENR-009 — End enrollment rejected: no active enrollment exists for the pair
- **Related UC / Rule:** UC-12 preconditions
- **Priority:** P1 · **Type:** Negative
- **Steps:** `DELETE /api/v1/enrollments/{studentId}/{courseCode}` for a pair with no active enrollment.
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
- **Steps:** `GET /api/v1/enrollments/{studentId}/{courseCode}`.
- **Expected Result:** `200 OK`; response includes the linked student's summary and the linked course's summary.

### TC-ENR-012 — View detail for an enrollment ended since the list was shown
- **Related UC / Rule:** UC-20 flow 2a; Enrollment.4
- **Priority:** P2 · **Type:** Negative
- **Steps:** Note an active enrollment; end it; then `GET /api/v1/enrollments/{studentId}/{courseCode}`.
- **Expected Result:** `404 Not Found`.

---

## Traceability Summary

| UC / US | Test Case IDs |
| --- | --- |
| UC-11 / US-4.1 | TC-ENR-001–006 |
| UC-12 / US-4.2 | TC-ENR-007–010 |
| UC-20 / US-5.5 | TC-ENR-011–012 |

Cross-module cascade behavior — enrollments removed automatically when their student or course is deleted — is covered in [student.md](./student.md) TC-STU-023/025, [course.md](./course.md) TC-CRS-015, and [cross-cutting.md](./cross-cutting.md) §4, not repeated here.
