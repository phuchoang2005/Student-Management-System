# Student Management System — User Stories

Derived from [req.md](./req.md). Each story follows the format: *As a [role], I want [capability], so that [benefit]*, with acceptance criteria traced back to the business rules they implement.

---

## 1. Student Management

### US-1.1 — Register a student
As a **registrar**, I want to register a new student with their code, name, email, and date of birth, so that the student can be tracked in the system and associated with books and courses.

**Acceptance Criteria**
- Given a student code that does not already exist, when I register a student, then the record is created. *(req.md §4 Student.1)*
- Given an email that already exists on another student, when I try to register, then the registration is rejected. *(req.md §4 Student.2)*
- Given a blank first name or last name, when I try to register, then the registration is rejected. *(req.md §4 Student.3)*
- Given an invalid or malformed date of birth, when I try to register, then the registration is rejected. *(req.md §4 Student.4)*
- Given an email that is not a valid email address, when I try to register, then the registration is rejected. *(req.md §4 Student.2)*
- Given a student is successfully registered, when the record is created, then the system automatically creates exactly one user account for that student, with the username set to their email. *(req.md §4 Identity.1-2)*
- Given the newly created account, when it is created, then a random 8-character initial password is generated and the account is marked as requiring a password change before it can be used for anything else. *(req.md §4 Identity.3)*
- Given a successful registration, when the system confirms creation, then it returns the student record along with the new account's username and initial password; this is the only step at which the initial password is generated, and it stays retrievable afterward only via US-6.3, and only until the student changes it. *(req.md §4 Identity.3, Identity.5)*

### US-1.2 — Update a student's details
As a **registrar**, I want to update a student's name, email, or date of birth, so that their record stays accurate.

**Acceptance Criteria**
- Given an update that changes the email to one already used by another student, when I save, then the update is rejected. *(req.md §4 Student.2)*
- Given an update with a blank first or last name, when I save, then the update is rejected. *(req.md §4 Student.3)*
- Given an update with an invalid date of birth, when I save, then the update is rejected. *(req.md §4 Student.4)*
- Student code is immutable and cannot be changed once assigned. *(req.md §4 Student.1)*

### US-1.3 — Remove a student
As a **registrar**, I want to remove a student from the system, so that records for students who have left are no longer active.

**Acceptance Criteria**
- Given a student who owns books, when the student is removed, then every book they owned becomes unassigned but the books are not deleted. *(req.md §5 "When a student is removed")*
- Given a student with enrollments, when the student is removed, then every enrollment they held is removed, but the courses are not affected. *(req.md §5 "When a student is removed")*
- Given a student is removed, when removal completes, then their associated user account is also removed. *(req.md §5 "When a student is removed", §4 Identity.1)*
- After removal, the student record no longer exists in the system. *(req.md §5 "When a student is removed")*

---

## 2. Book Management

### US-2.1 — Add a book to the catalog
As a **librarian**, I want to add a new book with its ISBN, title, author, and published date, so that it becomes available to be owned by a student.

**Acceptance Criteria**
- Given an ISBN that does not already exist, when I add a book, then the record is created. *(req.md §4 Book.1)*
- Given an ISBN that already exists, when I try to add the book, then the operation is rejected. *(req.md §4 Book.1)*
- A newly added book may be created without an owner. *(req.md §4 Book.3)*

### US-2.2 — Assign a book to a student
As a **librarian**, I want to assign an existing book to a student, so that the system reflects who currently holds it.

**Acceptance Criteria**
- Given a book and an existing student, when I assign the book, then the book's owner is set to that student. *(req.md §3 "Student ↔ Book")*
- Given a book that is already owned by another student, when I assign it to a new student, then the previous ownership link is replaced (a book has at most one owner at a time). *(req.md §4 Book.2)*
- Given a student who does not exist, when I try to assign a book to them, then the operation is rejected. *(req.md §4 Book.4)*

### US-2.3 — Unassign a book (end ownership)
As a **librarian**, I want to clear a book's ownership, so that the book becomes available again without deleting it.

**Acceptance Criteria**
- Given an owned book, when I end its ownership link, then the book continues to exist with no owner. *(req.md §4 Book.5)*

### US-2.4 — Remove a book
As a **librarian**, I want to remove a book from the catalog, so that it no longer appears in the system (e.g., lost or retired).

**Acceptance Criteria**
- Given a book is removed, its ownership link is also removed. *(req.md §5 "When a book is removed")*
- The student who owned it (if any) is unaffected and continues to exist. *(req.md §5 "When a book is removed")*

---

## 3. Course Management

### US-3.1 — Create a course
As a **course administrator**, I want to create a course with a code, name, description, and credit value, so that students can enroll in it.

**Acceptance Criteria**
- Given a course code that does not already exist, when I create the course, then the record is created. *(req.md §4 Course.1)*
- Given a course code that already exists, when I try to create it, then the operation is rejected. *(req.md §4 Course.1)*
- Given a blank course name, when I try to create the course, then the operation is rejected. *(req.md §4 Course.2)*
- Given a credit value that is zero or negative, when I try to create the course, then the operation is rejected. *(req.md §4 Course.3)*

### US-3.2 — Update a course
As a **course administrator**, I want to update a course's name, description, or credits, so that its listing stays current.

**Acceptance Criteria**
- Given an update with a blank name, when I save, then the update is rejected. *(req.md §4 Course.2)*
- Given an update with a non-positive credit value, when I save, then the update is rejected. *(req.md §4 Course.3)*

### US-3.3 — Remove a course
As a **course administrator**, I want to remove a course, so that it is no longer offered.

**Acceptance Criteria**
- Given a course is removed, every enrollment tied to it is also removed. *(req.md §5 "When a course is removed")*
- All students who were enrolled remain in the system, unaffected. *(req.md §5 "When a course is removed")*

---

## 4. Enrollment Management

### US-4.1 — Enroll a student in a course
As a **registrar**, I want to enroll a student in a course, so that their participation is recorded.

**Acceptance Criteria**
- Given a student and course that both exist and no existing enrollment between them, when I enroll by supplying their **student code** and the **course code**, then the enrollment is created. *(req.md §3 "Student ↔ Course", §4 Enrollment.2-3)*
- Given a student already enrolled in the course, when I try to enroll them again, then the operation is rejected as a duplicate. *(req.md §4 Enrollment.1)*
- Given a course that does not exist, when I try to enroll a student in it, then the operation is rejected. *(req.md §4 Enrollment.2)*
- Given a student that does not exist, when I try to enroll them in a course, then the operation is rejected. *(req.md §4 Enrollment.3)*

### US-4.2 — End an enrollment
As a **registrar**, I want to remove a student's enrollment in a course, so that they are no longer counted as taking it.

**Acceptance Criteria**
- Given an active enrollment, when it is ended — again by student code and course code — then only the link between student and course is removed. *(req.md §4 Enrollment.4)*
- Neither the student nor the course is deleted or otherwise affected. *(req.md §4 Enrollment.4)*

### US-4.3 — Enroll a student in several courses at once
As a **registrar**, I want to pick several courses for one student and submit them together, so that enrolling someone into a term's worth of study is one action rather than one action per course.

**Acceptance Criteria**
- Given a student and several courses, when I submit, then the student is enrolled in each of them. *(req.md §4 Enrollment.1–3)*
- Given one of the chosen courses is one the student is already enrolled in, when I submit, then the others are still enrolled and that one is reported as already enrolled. *(req.md §4 Enrollment.1)*
- Given one of the chosen courses does not exist, when I submit, then the others are still enrolled and that one is reported as unknown. *(req.md §4 Enrollment.2)*
- Given the student does not exist, when I submit, then the whole request is rejected and nothing is enrolled. *(req.md §4 Enrollment.3)*
- Given I choose the same course twice, when I submit, then it is enrolled once and reported once.
- The courses reported as enrolled stay enrolled regardless of any other course in the same submission being rejected.

---

## 5. Read Access (Per Actor)

### US-5.1 — Registrar looks up a student
As a **registrar**, I want to search for and view a student's details, so that I can verify their information or investigate an issue.

**Acceptance Criteria**
- Given a valid student code or search term, when I look it up, then matching student record(s) are returned. *(req.md §2 Student)*
- Given no student matches, when I search, then an empty result is returned rather than an error.
- Given more matching students than fit on one page, when I search, then I can browse to the next page rather than receiving every match at once.
- Given a list of search results, when I select one student, then the system displays that student's record together with **the courses they are currently enrolled in**. *(req.md §2 Student, §3 "Student ↔ Course")*
- Given that student also holds books, when I view their detail, then their books are **not** shown to me — book ownership is the Librarian's concern, not mine (US-5.2). *(req.md §3 "Student ↔ Book")*
- Given a selected student no longer exists (e.g., removed since the search was run), when I try to view their detail, then the system returns a not-found result rather than an error.
- I never see or supply a database identifier for a student; a student code is what I search on, select, and quote.
- This is a read-only operation; no data is created, changed, or deleted.

### US-5.2 — Librarian looks up books and who holds them
As a **librarian**, I want to search books by title, author, or ISBN, and to look up a student and see what they are holding, so that I can locate a book or chase an overdue one.

**Acceptance Criteria**
- Given a valid ISBN or search term, when I look it up, then matching book record(s) are returned, including current owner if any. *(req.md §2 Book, §3 "Student ↔ Book")*
- Given no book matches, when I search, then an empty result is returned rather than an error.
- Given more matching books than fit on one page, when I search, then I can browse to the next page rather than receiving every match at once.
- Given a list of search results, when I select one book, then the system displays that book's full detail: all book fields and its current owner's summary information, if owned. *(req.md §2 Book, §3 "Student ↔ Book")*
- Given a selected book no longer exists (e.g., removed since the search was run), when I try to view its detail, then the system returns a not-found result rather than an error.
- Given the student roll, when I search it and select one student, then the system displays that student's record together with **the books they are currently holding**. *(req.md §3 "Student ↔ Book")*
- Given that student is also enrolled in courses, when I view their detail, then their courses are **not** shown to me — enrollment is the Registrar's and Course Administrator's concern, not mine.
- I identify a student by their student code, never by a database identifier — including when assigning a book to them.
- This is a read-only operation; no data is created, changed, or deleted.

### US-5.3 — Course Administrator looks up courses
As a **course administrator**, I want to browse and search courses, and view a course's enrolled-student roster, so that I can manage offerings and class lists.

**Acceptance Criteria**
- Given a valid course code or search term, when I look it up, then matching course record(s) are returned. *(req.md §2 Course)*
- Given more matching courses than fit on one page, when I search, then I can browse to the next page rather than receiving every match at once.
- Given a specific course, when I request its roster, then one page of the students currently enrolled in it is returned. *(req.md §3 "Student ↔ Course")*
- Given a course roster with more enrolled students than fit on one page, when I view it, then I can browse to the next page of the roster.
- Given a list of search results, when I select one course, then the system displays that course's record, and — because I am responsible for its enrollments — its roster alongside. *(req.md §2 Course, §3 "Student ↔ Course")*
- Given a roster, when I select one student on it, then the system displays that student's record and the courses they are enrolled in. **This is my only route into a student record**: I do not browse students, because managing them is the Registrar's job.
- Given a selected course no longer exists (e.g., removed since the search was run), when I try to view its detail, then the system returns a not-found result rather than an error.
- I never see or supply a database identifier; course codes and student codes are what I work with.
- This is a read-only operation; no data is created, changed, or deleted.

### US-5.4 — Student views their own record, books, and courses
As a **student**, I want to see my own details, the books I hold, and the courses I am enrolled in, so that I can keep track of my own records.

**Acceptance Criteria**
- Given I am signed in, when I open my details, then **my own record is shown to me directly** — I do not search for myself, and I am not presented with a list to pick myself out of. *(req.md §2 Student)*
- Given I have never been told my student code, when I open my details, then it is shown there. Everything else in the system is addressed by a code somebody already knows; my own code is the one I have to be told.
- Given I hold books, when I request "my books", then one page of the books I am currently holding is returned. *(req.md §3 "Student ↔ Book")*
- Given I have active enrollments, when I request "my courses", then one page of the courses I am currently enrolled in is returned. *(req.md §3 "Student ↔ Course")*
- Given I hold no books or no enrollments, when I request either, then an empty list is returned rather than an error.
- Given more books or courses than fit on one page, when I view either list, then I can browse to the next page of that list independently of the other.
- Given my list of books or courses, when I select one entry, then the system displays that book's or course's detail. *(req.md §2 Book, §2 Course)*
- Given I open a course I am taking, when it is displayed, then I see the course itself but **not** the list of other students taking it.
- Given the system identifies me, when it does so, then it uses my sign-in, not anything I type — so there is no code I could supply that would show me somebody else's records.
- This is a read-only operation; no data is created, changed, or deleted.

### US-5.5 — Look up enrollments
As a **registrar or course administrator**, I want to ask either "what is this student taking?" or "who is taking this course?", so that I can answer a question about an enrollment from whichever end I happen to be standing at.

**Acceptance Criteria**
- Given a **student code**, when I look it up, then one page of that student's enrolled courses is returned. As a registrar this is my usual starting point: I know who I am dealing with and want to see their timetable. *(req.md §3 "Student ↔ Course")*
- Given a **course code**, when I look it up, then one page of the students enrolled in it is returned. As a course administrator this is my usual starting point. *(req.md §3 "Student ↔ Course")*
- Given I supply neither code, or both, when I make the request, then it is rejected — neither would be a meaningful question to ask.
- Given I supply a code that matches no student or no course, when I make the request, then I am told the code is invalid, rather than being shown an empty list that would read as "enrolled in nothing."
- Given more enrollments than fit on one page, when I view them, then I can browse to the next page.
- Given a list of enrollments, when I select one entry, then the system displays that enrollment's detail: the student's summary and the course's summary. *(req.md §3 "Student ↔ Course")*
- Given the selected enrollment no longer exists (e.g., it was ended since the list was shown), when I try to view its detail, then the system returns a not-found result rather than an error. *(req.md §4 Enrollment.4)*
- I supply codes only — a student code, a course code. I never see or type a database identifier for either.
- This is a read-only operation; no data is created, changed, or deleted.

> A student is not an actor on this story. A student sees their own enrolled courses through US-5.4, where the system knows who they are from their sign-in rather than from a code they type.

---

## 6. Identity & Access Management

### US-6.1 — Log in
As any **actor** (registrar, librarian, course administrator, or student), I want to log in with my username and password, so that I can access the system with the permissions of my role.

**Acceptance Criteria**
- Given valid credentials, when I log in, then I receive an authenticated session scoped to my role. *(req.md §4 Identity.2)*
- Given an unknown username or a password that doesn't match, when I try to log in, then the attempt is rejected with an authentication error.
- Given my account is still using its system-issued initial password, when I log in, then the system indicates a password change is required, and I can take no other action until I change it. *(req.md §4 Identity.3)*

### US-6.2 — Change my password
As any **actor**, I want to change my password by providing my current password and a new one, so that I can secure my account or satisfy a required password change.

**Acceptance Criteria**
- Given my current password and a matching retyped new password, when I submit a new password that meets the minimum policy and differs from the current one, then my password is updated and any "must change password" state is cleared. *(req.md §4 Identity.3-5)*
- Given a retyped new password that doesn't match the new password, when I submit, then the request is rejected.
- Given an incorrect current password, when I submit, then the request is rejected with an authentication error.
- Given a new password that fails the minimum policy or is identical to the current password, when I submit, then the request is rejected.
- Once my password is changed, it is no longer recoverable by anyone, including the registrar. *(req.md §4 Identity.4)*
- There is no recovery path for an actor who cannot authenticate at all (forgotten password, no active session) — that scenario is handled operationally, outside the system.

### US-6.3 — View a student's initial password
As a **registrar**, I want to view a student's system-issued initial password, so that I can share it with the student before their first login.

**Acceptance Criteria**
- Given a student whose account still uses its initial password, when I request it, then the system returns that password. *(req.md §4 Identity.5)*
- Given a student who has already changed their password, when I request it, then the system indicates the initial password is no longer available to anyone, and no password is returned. *(req.md §4 Identity.4-5)*
- This is a read-only operation; no data is created, changed, or deleted.

---

## 7. Staff Identity Administration

### US-7.1 — Create a staff account
As a **system administrator**, I want to create a staff account with a chosen role, so that a new registrar, librarian, or course administrator can log in.

**Acceptance Criteria**
- Given a username and a role of registrar, librarian, or course administrator, when I create the account, then it's created in a "must change password" state and I receive its initial password once. *(req.md §4 Identity.3, Identity.6)*
- Given a username already in use, when I try to create the account, then the request is rejected. *(req.md §4 Identity.2)*
- Given a role of system administrator, when I try to create the account, then the request is rejected — system administrator accounts are never created through the application. *(req.md §4 Identity.6)*

### US-7.2 — Deactivate or reactivate a staff account
As a **system administrator**, I want to disable or re-enable a staff account, so that I can revoke or restore a staff member's access without deleting their account.

**Acceptance Criteria**
- Given an active staff account, when I disable it, then it can no longer log in. *(req.md §4 Identity.7)*
- Given a disabled staff account, when I re-enable it, then it can log in again. *(req.md §4 Identity.7)*
- Disabling or re-enabling an account does not affect any data the account holder previously created.

### US-7.3 — See who is signed in
As a **system administrator**, I want to see which accounts are signed in right now, so that I know who currently has access before I decide whether to act.

**Acceptance Criteria**
- Given people are signed in, when I open the active sessions view, then I see each one's username, role, and when they were last active. *(req.md §4 Identity.8)*
- Given I am signed in, when I open the view, then my own session is marked as mine.
- Given the server has just restarted, when I open the view, then the list is empty — the record of who is signed in does not survive a restart, and nobody was signed out by it.
- The view never shows anything that could be used to impersonate a session.

### US-7.4 — End someone's session
As a **system administrator**, I want to end a signed-in session, so that I can cut off access immediately without disabling the account.

**Acceptance Criteria**
- Given a listed session other than my own, when I end it, then its holder is refused on their next request and returned to sign-in. *(req.md §4 Identity.8)*
- Given I have ended a session, when I look again, then it is no longer listed.
- Given a session I ended, when its holder signs in again, then they are allowed in — ending a session is not disabling an account. *(req.md §4 Identity.7, Identity.8)*
- Given my own session, when I try to end it, then the request is rejected and I am pointed at signing out instead.

---

## Traceability Summary

| User Story | Business Rule Source |
| ---------- | --------------------- |
| US-1.1 | req.md §4 Student 1–4; §4 Identity.1–3, Identity.5 |
| US-1.2 | req.md §4 Student 1–4 |
| US-1.3 | req.md §5 "When a student is removed"; §4 Identity.1 |
| US-2.1 | req.md §4 Book.1 |
| US-2.2 | req.md §4 Book.2, Book.4 |
| US-2.3 | req.md §4 Book.5 |
| US-2.4 | req.md §5 "When a book is removed" |
| US-3.1, US-3.2 | req.md §4 Course 1–3 |
| US-3.3 | req.md §5 "When a course is removed" |
| US-4.1 | req.md §4 Enrollment 1–3 |
| US-4.2 | req.md §4 Enrollment.4 |
| US-4.3 | req.md §4 Enrollment 1–3 |
| US-5.1 | req.md §2 Student, §3 Student↔Book, Student↔Course (read-only) |
| US-5.2 | req.md §2 Book, §3 Student↔Book (read-only) |
| US-5.3 | req.md §2 Course, §3 Student↔Course (read-only) |
| US-5.4 | req.md §2 Book, §2 Course, §3 Student↔Book, Student↔Course (read-only) |
| US-5.5 | req.md §3 Student↔Course, §4 Enrollment.4 (read-only) |
| US-6.1 | req.md §4 Identity.2–3 |
| US-6.2 | req.md §4 Identity.3–5 |
| US-6.3 | req.md §4 Identity.4–5 (read-only) |
| US-7.1 | req.md §4 Identity.2–3, Identity.6 |
| US-7.2 | req.md §4 Identity.7 |
| US-7.3 | req.md §4 Identity.8 (read-only) |
| US-7.4 | req.md §4 Identity.8 |
