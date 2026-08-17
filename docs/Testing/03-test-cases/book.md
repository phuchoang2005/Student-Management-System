# Test Cases — `book` Module

Testing Documentation — [Test Strategy](../01-test-strategy.md) → [Test Plan](../02-test-plan.md) → Test Cases (`book`) → [Test Data Preparation](../04-test-data-preparation.md).

Covers **UC-4** (Add Book), **UC-5** (Assign Book to Student), **UC-6** (Unassign Book), **UC-7** (Remove Book), **UC-14** (View/Search Books), **UC-18** (View Book Detail), and related user stories US-2.1–2.4, US-5.2. Endpoints: `POST /books`, `GET /books`, `GET /books/{isbn}`, `DELETE /books/{isbn}`, `PATCH /books/{isbn}/owner`, `DELETE /books/{isbn}/owner`. Role authorization: see [cross-cutting.md](./cross-cutting.md) §1.

---

## UC-4: Add Book

### TC-BOOK-001 — Add a book with valid data and no owner
- **Related UC / Rule:** UC-4 main flow; Book.1, Book.3
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `book-valid-01` (no owner)
- **Steps:** `POST /api/v1/books` with valid `isbn`, `title`, `author`, `publishedDate`, no `ownerId`.
- **Expected Result:** `201 Created`; book exists with `owner: null`.

### TC-BOOK-002 — Add a book with a valid owner specified
- **Related UC / Rule:** UC-4 main flow; Book.4
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `book-valid-02`, owner = `student-valid-01`
- **Steps:** `POST /api/v1/books` with `ownerId = student-valid-01.id`.
- **Expected Result:** `201 Created`; book's owner is the specified student.

### TC-BOOK-003 — Add rejected: duplicate ISBN
- **Related UC / Rule:** UC-4 flow 2a; Book.1
- **Priority:** P0 · **Type:** Negative
- **Test Data:** `book-valid-01` (pre-seeded)
- **Steps:** `POST /api/v1/books` reusing `book-valid-01.isbn`.
- **Expected Result:** `409 Conflict` (`DuplicateIsbnException`).

### TC-BOOK-004 — Add rejected: specified owner does not exist
- **Related UC / Rule:** UC-4 flow 3a; Book.4
- **Priority:** P0 · **Type:** Negative
- **Steps:** `POST /api/v1/books` with `ownerId = <non-existent student id>`.
- **Expected Result:** `400 Bad Request` (`UnknownStudentException`) — per `api-specification.md` §5.2, an unknown FK reference is malformed input, not a conflict.

### TC-BOOK-005 — `isbn` at the `VARCHAR(20)` boundary is accepted
- **Related UC / Rule:** UC-4 main flow; `05-database-schema.md` §3.3
- **Priority:** P2 · **Type:** Boundary
- **Test Data:** `book-boundary-isbn-20chars` (ISBN-13 with hyphens, sized to fit)
- **Steps:** `POST /api/v1/books` with a 20-character `isbn`.
- **Expected Result:** `201 Created`.

---

## UC-5: Assign Book to Student

### TC-BOOK-006 — Assign an unowned book to an existing student
- **Related UC / Rule:** UC-5 main flow; req.md §3 "Student ↔ Book"
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `book-valid-01` (unowned), `student-valid-01`
- **Steps:** `PATCH /api/v1/books/{isbn}/owner` with `ownerId = student-valid-01.id`.
- **Expected Result:** `200 OK`; book's owner is now `student-valid-01`.

### TC-BOOK-007 — Reassigning an already-owned book replaces the previous owner
- **Related UC / Rule:** UC-5 main flow; Book.2
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `book-owned-by-student-01` (currently owned by `student-valid-01`), reassign to `student-valid-02`
- **Steps:** `PATCH /api/v1/books/{isbn}/owner` with `ownerId = student-valid-02.id`.
- **Expected Result:** `200 OK`; book's owner is now `student-valid-02`; `student-valid-01` no longer shows this book under "my books" ([identity-auth.md](./identity-auth.md) / UC-16 flows, not duplicated here). A book has at most one owner at any time — never both simultaneously.

### TC-BOOK-008 — Assign rejected: target student does not exist
- **Related UC / Rule:** UC-5 flow 2a; Book.4
- **Priority:** P0 · **Type:** Negative
- **Steps:** `PATCH /api/v1/books/{isbn}/owner` with `ownerId = <non-existent student id>`.
- **Expected Result:** `400 Bad Request` (`UnknownStudentException`).

### TC-BOOK-009 — Assign rejected: book does not exist
- **Related UC / Rule:** UC-5 preconditions
- **Priority:** P1 · **Type:** Negative
- **Steps:** `PATCH /api/v1/books/does-not-exist/owner` with a valid `ownerId`.
- **Expected Result:** `404 Not Found`.

---

## UC-6: Unassign Book (End Ownership)

### TC-BOOK-010 — Unassign an owned book
- **Related UC / Rule:** UC-6 main flow; Book.3, Book.5
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `book-owned-by-student-01`
- **Steps:** `DELETE /api/v1/books/{isbn}/owner`.
- **Expected Result:** `200 OK`; book still exists, `owner: null`.

### TC-BOOK-011 — Unassigning an already-unowned book is idempotent
- **Related UC / Rule:** `api-specification.md` §5.7 (explicit deviation resolution)
- **Priority:** P1 · **Type:** Boundary
- **Test Data:** `book-valid-01` (unowned)
- **Steps:** `DELETE /api/v1/books/{isbn}/owner` on a book that already has no owner.
- **Expected Result:** `200 OK` (not `409`) with `owner: null` — deliberately idempotent per the documented design decision, not an error.

---

## UC-7: Remove Book

### TC-BOOK-012 — Remove an unowned book
- **Related UC / Rule:** UC-7 main flow
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `book-valid-01`
- **Steps:** `DELETE /api/v1/books/{isbn}`.
- **Expected Result:** `200/204`; subsequent `GET /books/{isbn}` returns `404`.

### TC-BOOK-013 — Removing an owned book leaves the owning student unaffected
- **Related UC / Rule:** UC-7 postconditions; req.md §5 "When a book is removed"
- **Priority:** P0 · **Type:** Functional
- **Test Data:** `book-owned-by-student-01`, owner = `student-valid-01`
- **Steps:** `DELETE /api/v1/books/{isbn}`; then `GET /api/v1/students/{student-valid-01.code}`.
- **Expected Result:** Book no longer exists; `student-valid-01` still exists, unaffected (no error, no missing account).

### TC-BOOK-014 — Remove rejected: book does not exist
- **Related UC / Rule:** UC-7 preconditions
- **Priority:** P2 · **Type:** Negative
- **Steps:** `DELETE /api/v1/books/does-not-exist`.
- **Expected Result:** `404 Not Found`.

---

## UC-14: View/Search Books

### TC-BOOK-015 — Search by exact ISBN returns the matching record
- **Related UC / Rule:** UC-14 main flow
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `book-valid-01`
- **Steps:** `GET /api/v1/books?isbn={isbn}`.
- **Expected Result:** `200 OK`; one summary (ISBN, title, author, owner name if any).

### TC-BOOK-016 — Search by title/author term, optionally filtered by owner
- **Related UC / Rule:** UC-14 main flow
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `book-search-set-01`
- **Steps:** `GET /api/v1/books?q={term}&ownerId={id}`.
- **Expected Result:** `200 OK`; only books matching both the term and the owner filter are returned.

### TC-BOOK-017 — Search with no match returns an empty list
- **Related UC / Rule:** UC-14 flow 2a
- **Priority:** P2 · **Type:** Boundary
- **Steps:** `GET /api/v1/books?isbn=does-not-exist`.
- **Expected Result:** `200 OK`; empty array.

---

## UC-18: View Book Detail

### TC-BOOK-018 — View full detail of an owned book
- **Related UC / Rule:** UC-18 main flow
- **Priority:** P1 · **Type:** Functional
- **Test Data:** `book-owned-by-student-01`
- **Steps:** `GET /api/v1/books/{isbn}`.
- **Expected Result:** `200 OK`; response includes all book fields and the current owner's summary information.

### TC-BOOK-019 — View full detail of an unowned book
- **Related UC / Rule:** UC-18 main flow; Book.3
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `book-valid-01`
- **Steps:** `GET /api/v1/books/{isbn}`.
- **Expected Result:** `200 OK`; owner field absent/null, no error.

### TC-BOOK-020 — View detail for a book removed since the search ran
- **Related UC / Rule:** UC-18 flow 2a
- **Priority:** P2 · **Type:** Negative
- **Steps:** Note a book's ISBN; delete it; then `GET /api/v1/books/{isbn}`.
- **Expected Result:** `404 Not Found`.

---

## UC-14: View/Search Books — Pagination

### TC-BOOK-021 — Default `page`/`size` are applied when omitted
- **Related UC / Rule:** UC-14 main flow (`api-specification.md` §3 Pagination)
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `book-search-set-01`
- **Steps:** `GET /api/v1/books?q={term}` with no `page`/`size` given.
- **Expected Result:** `200 OK`; body is `{content, page, size, totalElements, totalPages}` with `page: 0` and `size: 20`.

### TC-BOOK-022 — Custom `page`/`size` correctly slices the result set
- **Related UC / Rule:** UC-14 flow 2b
- **Priority:** P2 · **Type:** Functional
- **Test Data:** `book-search-set-01`
- **Steps:** `GET /api/v1/books?q={term}&page=0&size=2`, then `GET /api/v1/books?q={term}&page=1&size=2`.
- **Expected Result:** Each call returns at most 2 records; `totalElements`/`totalPages` are consistent across both calls; no record appears on both pages.

### TC-BOOK-023 — A page past the last page returns an empty page, not an error
- **Related UC / Rule:** UC-14 flow 2b (boundary)
- **Priority:** P2 · **Type:** Boundary
- **Test Data:** `book-search-set-01`
- **Steps:** `GET /api/v1/books?q={term}&page=99&size=20`.
- **Expected Result:** `200 OK`; `content` is an empty array; `totalElements`/`totalPages` still reflect the real result set.

---

## Traceability Summary

| UC / US | Test Case IDs |
| --- | --- |
| UC-4 / US-2.1 | TC-BOOK-001–005 |
| UC-5 / US-2.2 | TC-BOOK-006–009 |
| UC-6 / US-2.3 | TC-BOOK-010–011 |
| UC-7 / US-2.4 | TC-BOOK-012–014 |
| UC-14 / US-5.2 | TC-BOOK-015–017, TC-BOOK-021–023 |
| UC-18 / US-5.2, US-5.4 | TC-BOOK-018–020 |

Note: UC-18 is also reachable by a Student viewing their own book via UC-16 ("my books") — see [identity-auth.md](./identity-auth.md) / [cross-cutting.md](./cross-cutting.md) §1 for the Student-scoped read path.
