# Student Management System — Use Cases

Derived from [req.md](./req.md) and [user-stories.md](./user-stories.md). Actors and flows describe interaction between a user and the system; business rule references trace back to the source invariants.

---

## Actors

| Actor | Description |
| ----- | ----------- |
| **System Administrator** | Provisions and disables staff user accounts (Registrar, Librarian, Course Administrator). Has no access to student, book, course, or enrollment data. |
| **Registrar** | Manages student records and enrollments. Reads students, courses, and enrollments — not books. |
| **Librarian** | Manages the book catalog and book ownership assignments. Reads books and students (to see who holds what) — not courses or enrollments. |
| **Course Administrator** | Manages course offerings and reads their rosters. Reads courses, enrollments, and — only by clicking through a roster — a student's record. Does not browse students, and does not read books. |
| **Student** | May look up their own record, their own books, and their own enrolled courses. Cannot enroll or end their own enrollment — that is Registrar-only — and does not use the enrollment lookups at all. |

**Each actor reads what its own work needs, and no more.** The read scopes above are not incidental: they are why UC-17 shows a Librarian a student's books but a Registrar that student's courses, and why UC-19 shows a roster to staff but not to a Student browsing the catalogue.

**No actor ever handles a database identifier.** Every code in this document — student code, course code, ISBN — is a business key a person can read out loud. Internal record ids exist, but no actor sees or supplies one.

---

## UC-1: Register Student

- **Actor:** Registrar
- **Preconditions:** None.
- **Trigger:** Registrar submits a new student's code, first name, last name, email, and date of birth.

**Main Flow**
1. Registrar enters student code, first name, last name, email, and date of birth.
2. System validates that the student code is unique. *(Student.1)*
3. System validates that the email is unique and well-formed. *(Student.2)*
4. System validates that first name and last name are non-blank. *(Student.3)*
5. System validates that date of birth is a real, valid date. *(Student.4)*
6. System creates the student record.
6a. System automatically creates a user account for the student: username is set to the student's email; a random 8-character password is generated and set as the account's initial password; the account is marked as requiring a password change before further use. *(Identity.1–3)*
7. System confirms creation, returning the student record along with the new account's username and initial password. This is the only step at which the initial password is generated; it remains retrievable afterward only through UC-23, and only until the student changes it.

**Alternate / Exception Flows**
- **3a.** Student code already exists → system rejects with a duplicate-code error; return to step 1.
- **3b.** Email already exists or is malformed → system rejects with a validation error; return to step 1.
- **4a.** First or last name is blank → system rejects with a validation error; return to step 1.
- **5a.** Date of birth is invalid → system rejects with a validation error; return to step 1.

**Postconditions:** A new, uniquely identified student exists in the system, along with exactly one user account for that student, holding a system-issued initial password that must be changed before the account can be used normally.

**Related Rules:** Identity.1–3.

---

## UC-2: Update Student Details

- **Actor:** Registrar
- **Preconditions:** The student record exists.
- **Trigger:** Registrar submits changes to a student's name, email, and/or date of birth.

**Main Flow**
1. Registrar selects an existing student and submits updated fields.
2. System validates any updated email is unique across other students and well-formed. *(Student.2)*
3. System validates first/last name remain non-blank if changed. *(Student.3)*
4. System validates date of birth remains valid if changed. *(Student.4)*
5. System saves the updated record.

**Alternate / Exception Flows**
- **2a.** New email collides with another student → reject; return to step 1.
- **3a.** Name field is blank → reject; return to step 1.
- **4a.** Date of birth invalid → reject; return to step 1.

**Postconditions:** Student record reflects updated values; student code is unchanged.

---

## UC-3: Remove Student

- **Actor:** Registrar
- **Preconditions:** The student record exists.
- **Trigger:** Registrar requests deletion of a student.

**Main Flow**
1. Registrar selects a student to remove.
2. System locates all books owned by the student and clears their ownership link (books remain in catalog). *("When a student is removed")*
3. System locates all enrollments held by the student and removes them (courses remain). *("When a student is removed")*
4. System deletes the student record.
5. System confirms removal.

**Postconditions:** Student no longer exists; previously owned books are unassigned but intact; previously enrolled courses are unaffected.

**Related Rules:** req.md §5 "When a student is removed"; §4 Book.5; §4 Enrollment.4.

---

## UC-4: Add Book

- **Actor:** Librarian
- **Preconditions:** None.
- **Trigger:** Librarian submits ISBN, title, author, and published date for a new book, optionally with an owner.

**Main Flow**
1. Librarian enters book details.
2. System validates ISBN is unique. *(Book.1)*
3. If an owner is specified, system validates the student exists. *(Book.4)*
4. System creates the book record (owned or unowned). *(Book.3)*
5. System confirms creation.

**Alternate / Exception Flows**
- **2a.** ISBN already exists → reject; return to step 1.
- **3a.** Specified owner does not exist → reject; return to step 1.

**Postconditions:** A new book exists, optionally linked to an owning student.

---

## UC-5: Assign Book to Student

- **Actor:** Librarian
- **Preconditions:** The book and the target student both exist.
- **Trigger:** Librarian requests to assign (or reassign) a book to a student.

**Main Flow**
1. Librarian selects a book and a target student.
2. System validates the student exists. *(Book.4)*
3. System sets the book's owner to the target student, replacing any prior owner (a book has at most one owner). *(Book.2)*
4. System confirms the assignment.

**Alternate / Exception Flows**
- **2a.** Target student does not exist → reject; return to step 1.

**Postconditions:** The book's owner is the target student; any previous owner no longer has the book.

---

## UC-6: Unassign Book (End Ownership)

- **Actor:** Librarian
- **Preconditions:** The book exists and currently has an owner.
- **Trigger:** Librarian requests to clear a book's ownership.

**Main Flow**
1. Librarian selects an owned book.
2. System clears the ownership link.
3. Book remains in the catalog as unassigned. *(Book.3, Book.5)*
4. System confirms the change.

**Postconditions:** Book exists with no owner.

---

## UC-7: Remove Book

- **Actor:** Librarian
- **Preconditions:** The book exists.
- **Trigger:** Librarian requests deletion of a book.

**Main Flow**
1. Librarian selects a book to remove.
2. System removes the ownership link, if any.
3. System deletes the book record.
4. System confirms removal.

**Postconditions:** Book no longer exists; the previous owner (if any) is unaffected. *("When a book is removed")*

---

## UC-8: Create Course

- **Actor:** Course Administrator
- **Preconditions:** None.
- **Trigger:** Administrator submits course code, name, description, and credits.

**Main Flow**
1. Administrator enters course details.
2. System validates course code is unique. *(Course.1)*
3. System validates name is non-blank. *(Course.2)*
4. System validates credits is a positive number. *(Course.3)*
5. System creates the course record.
6. System confirms creation.

**Alternate / Exception Flows**
- **2a.** Course code already exists → reject; return to step 1.
- **3a.** Name is blank → reject; return to step 1.
- **4a.** Credits is zero or negative → reject; return to step 1.

**Postconditions:** A new, uniquely identified course exists.

---

## UC-9: Update Course

- **Actor:** Course Administrator
- **Preconditions:** The course exists.
- **Trigger:** Administrator submits changes to name, description, and/or credits.

**Main Flow**
1. Administrator selects a course and submits updated fields.
2. System validates name remains non-blank if changed. *(Course.2)*
3. System validates credits remains positive if changed. *(Course.3)*
4. System saves the updated record.

**Alternate / Exception Flows**
- **2a.** Name is blank → reject; return to step 1.
- **3a.** Credits is non-positive → reject; return to step 1.

**Postconditions:** Course record reflects updated values; course code is unchanged.

---

## UC-10: Remove Course

- **Actor:** Course Administrator
- **Preconditions:** The course exists.
- **Trigger:** Administrator requests deletion of a course.

**Main Flow**
1. Administrator selects a course to remove.
2. System removes every enrollment tied to the course. *("When a course is removed")*
3. System deletes the course record.
4. System confirms removal.

**Postconditions:** Course no longer exists; all previously enrolled students remain in the system, unaffected.

---

## UC-11: Enroll Student in Course

- **Actor:** Registrar
- **Preconditions:** The student and course both exist.

> Student self-service enrollment is out of scope — a Student may view their own enrolled courses (UC-16) but cannot create an enrollment, and has no access to the enrollment lookups in UC-20.
- **Trigger:** Actor requests to enroll a student in a course.

**Main Flow**
1. Actor selects a student and a course.
2. System validates the student exists. *(Enrollment.3)*
3. System validates the course exists. *(Enrollment.2)*
4. System validates no existing enrollment for this (student, course) pair. *(Enrollment.1)*
5. System creates the enrollment.
6. System confirms enrollment.

**Alternate / Exception Flows**
- **2a.** Student does not exist → reject; return to step 1.
- **3a.** Course does not exist → reject; return to step 1.
- **4a.** Student already enrolled in the course → reject as duplicate; return to step 1.

**Postconditions:** An active enrollment links the student and course.

---

## UC-12: End Enrollment

- **Actor:** Registrar
- **Preconditions:** An active enrollment exists for the (student, course) pair.

> Student self-service withdrawal is out of scope — a Student may view their own enrolled courses (UC-16) but cannot end an enrollment, and has no access to the enrollment lookups in UC-20.
- **Trigger:** Actor requests to withdraw a student from a course.

**Main Flow**
1. Actor selects the student's enrollment in a course.
2. System removes the enrollment link only. *(Enrollment.4)*
3. System confirms removal.

**Postconditions:** Student is no longer enrolled in the course; both the student and course records are unaffected.

---

## UC-13: View/Search Students

- **Actor:** Registrar
- **Preconditions:** None.
- **Trigger:** Registrar looks up a student by code or searches by name/email.

**Main Flow**
1. Registrar enters a student code, or a name/email search term.
2. System looks up matching student record(s) and returns a summary for each (student code, name, email).
3. System returns one page of the matching record(s).

**Alternate / Exception Flows**
- **2a.** No student matches the given code or search term → system returns an empty result.
- **2b.** More records match than fit on one page → Registrar may request the next page rather than receiving every match at once.

**Extension Points**
- **Select a result:** Registrar selects one student from the results → continues at **UC-17: View Student Detail**.

**Postconditions:** No data is changed; matching student summaries are displayed to the Registrar.

**Related User Story:** US-5.1.

---

## UC-14: View/Search Books

- **Actor:** Librarian
- **Preconditions:** None.
- **Trigger:** Librarian looks up a book by ISBN or searches by title/author, optionally filtered by owner.

**Main Flow**
1. Librarian enters an ISBN, or a title/author search term, optionally with an owner filter.
2. System looks up matching book record(s) and returns a summary for each (ISBN, title, author, owner name if any).
3. System returns one page of the matching record(s).

**Alternate / Exception Flows**
- **2a.** No book matches the given ISBN or search term → system returns an empty result.
- **2b.** More records match than fit on one page → Librarian may request the next page rather than receiving every match at once.

**Extension Points**
- **Select a result:** Librarian selects one book from the results → continues at **UC-18: View Book Detail**.

**Postconditions:** No data is changed; matching book summaries, including ownership status, are displayed to the Librarian.

**Related User Story:** US-5.2.

---

## UC-15: View/Search Courses

- **Actor:** Course Administrator
- **Preconditions:** None.
- **Trigger:** Administrator looks up a course by course code or searches by name, and may request its enrolled-student roster.

**Main Flow**
1. Administrator enters a course code, or a name search term.
2. System looks up matching course record(s) and returns a summary for each (course code, name, credits).
3. System returns one page of the matching record(s).

**Alternate / Exception Flows**
- **2a.** No course matches the given code or search term → system returns an empty result.
- **2b.** More records match than fit on one page → Administrator may request the next page rather than receiving every match at once.

**Extension Points**
- **Select a result:** Administrator selects one course from the results → continues at **UC-19: View Course Detail** (which includes the full enrolled-student roster).

**Postconditions:** No data is changed; matching course summaries are displayed to the Administrator.

**Related User Story:** US-5.3.

---

## UC-16: View Own Record, Books & Courses

- **Actor:** Student
- **Preconditions:** The student record exists and the Student actor is looking up their own data.
- **Trigger:** Student requests to view their own details, the books they hold, or the courses they are enrolled in.

**Main Flow**
1. Student selects "my details", "my books", or "my courses."
2. System identifies the student **from the authenticated session**, not from any identifier the Student supplies — this is the only lookup in the system with no caller-supplied key.
3. System returns the requested view:
   - *my details* → the student's own record (including their student code, which nothing else tells them);
   - *my books* → one page of summaries of the books they hold;
   - *my courses* → one page of summaries of the courses they are enrolled in.

**Alternate / Exception Flows**
- **3a.** Student holds no books → system returns an empty page for books.
- **3b.** Student has no active enrollments → system returns an empty page for courses.
- **3c.** More results exist than fit on one page → Student may request the next page. Each view pages on its own, so moving through books never disturbs the courses view.
- **3d.** The student record was removed while the session was still open → system reports that the record no longer exists.

**Extension Points**
- **Select a book:** Student selects one held book → continues at **UC-18: View Book Detail**.
- **Select a course:** Student selects one enrolled course → continues at **UC-19: View Course Detail**.

**Postconditions:** No data is changed; the Student sees their own record and their own current books and course enrollments.

**Note on enrollments.** A Student reads their enrolled courses *here*, and has no access to the enrollment lookups in UC-20 at all. The distinction is deliberate: this use case derives the answer from who is logged in, while UC-20 derives it from a student code the caller types — which is a code a Student could substitute for someone else's. Withdrawing the access is stronger than checking it.

**Related User Story:** US-5.4.

---

## UC-17: View Student Detail

- **Actors:** Registrar, Librarian, Course Administrator
- **Extends:** UC-13 (View/Search Students) for the Registrar and Librarian; **UC-19 (View Course Detail)** for the Course Administrator, which is its only way in
- **Preconditions:** A student search has returned at least one result, or a course roster is on screen.
- **Trigger:** An actor selects a student from a list.

**Main Flow**
1. The actor selects one student.
2. System retrieves that student's record.
3. System additionally retrieves **the side of the student's associations that the actor is responsible for**, and only that side:
   - **Librarian** → the books the student currently holds;
   - **Registrar** and **Course Administrator** → the courses the student is currently enrolled in.
4. System displays the record together with that one related list.

**Alternate / Exception Flows**
- **2a.** The selected student no longer exists (e.g., removed after the search ran) → system returns a not-found result; the actor returns to the previous list.
- **3a.** The student holds no books / has no enrollments → system shows an empty list, not an error.
- **3b.** More related records exist than fit on one page → the actor may request the next page.

**Postconditions:** No data is changed; the actor sees the selected student's record plus the one associated list their role covers.

**Note on the split.** Step 3 is the whole point of separating the two lists rather than showing both to everyone. A Librarian has no business seeing a student's timetable, and a Course Administrator has none seeing what they have borrowed. The Course Administrator reaches this use case only by clicking a name on a course roster — it has no student-browsing entry point at all, because browsing students is not part of its job.

**Related User Stories:** US-5.1, US-5.2, US-5.3.

---

## UC-18: View Book Detail

- **Actor:** Librarian (also reachable by Student via UC-16)
- **Extends:** UC-14 (View/Search Books), UC-16 (View Own Record, Books & Courses)
- **Preconditions:** A book search or "my books" list has returned at least one result.
- **Trigger:** Actor selects a book from the results.

**Main Flow**
1. Actor selects one book from the list of results.
2. System retrieves the full record for the selected book: ISBN, title, author, published date, and current owner's summary information, if owned.
3. System displays the full book detail.

**Alternate / Exception Flows**
- **2a.** The selected book no longer exists (e.g., removed after the results were shown) → system returns a not-found result; Actor returns to the results list.

**Postconditions:** No data is changed; the Actor sees the selected book's complete detail, including current ownership.

**Related User Story:** US-5.2, US-5.4.

---

## UC-19: View Course Detail

- **Actors:** Course Administrator, Registrar, Student (the latter via UC-16)
- **Extends:** UC-15 (View/Search Courses), UC-16 (View Own Record, Books & Courses)
- **Preconditions:** A course search or "my courses" list has returned at least one result.
- **Trigger:** Actor selects a course from the results.

**Main Flow**
1. Actor selects one course from the list of results.
2. System retrieves the record for the selected course: course code, name, description, credits.
3. **If the actor is a Course Administrator or Registrar**, system additionally retrieves one page of the currently enrolled-student roster.
4. System displays the course detail, with the roster when step 3 applied.

**Alternate / Exception Flows**
- **2a.** The selected course no longer exists (e.g., removed after the results were shown) → system returns a not-found result; Actor returns to the results list.
- **3a.** No students are enrolled → system shows an empty roster, not an error.
- **3b.** More students are enrolled than fit on one roster page → Actor may request the next page of the roster.

**Extension Points**
- **Select a student:** a Course Administrator or Registrar selects one name on the roster → continues at **UC-17: View Student Detail**. For the Course Administrator this is the *only* path into a student record.

**Postconditions:** No data is changed; the Actor sees the selected course's record, and — for the two staff roles — a page of its enrolled-student roster.

**Note on the roster.** Step 3 is conditional rather than universal because a Student browsing a course they are taking has no business receiving the names and contact details of everyone else taking it. The catalogue itself is open to them; the roster is not.

**Related User Stories:** US-5.3, US-5.4.

---

## UC-20: Look Up Enrollments

- **Actors:** Registrar, Course Administrator
- **Extends:** UC-17 (View Student Detail), UC-19 (View Course Detail)
- **Preconditions:** The actor knows a student code or a course code, or has one of those records on screen.
- **Trigger:** Actor asks either "what is this student taking?" or "who is taking this course?"

**Main Flow**
1. Actor supplies **exactly one** of a student code or a course code.
   - A **Registrar** typically types a student code directly — this is how the Registrar works, student first.
   - A **Course Administrator** typically arrives from a course, having picked it from the current course list.
2. System returns one page of the matching enrollments, each showing both sides: the student's summary, the course's summary, and when the enrollment began.
3. Actor may select one entry to see that single enrollment on its own.

**Alternate / Exception Flows**
- **1a.** Neither code is supplied, or both are → system rejects the request. Neither would be meaningful: with no code this would list every enrollment in the system, which no actor has asked for; with both, the answer is the single entry step 3 already reaches.
- **1b.** The supplied code matches no student / no course → system reports the code as invalid, rather than showing an empty list that would look like "enrolled in nothing."
- **2a.** The student is enrolled in nothing / the course has nobody enrolled → system returns an empty page.
- **2b.** More enrollments exist than fit on one page → Actor may request the next page.
- **3a.** The selected enrollment no longer exists (e.g., it was ended after the list was shown) → system returns a not-found result; Actor returns to the list. *(req.md §4 Enrollment.4)*

**Extension Points**
- **Select a student:** the Actor selects the student side of an entry → continues at **UC-17: View Student Detail**.
- **Select a course:** the Actor selects the course side of an entry → continues at **UC-19: View Course Detail**.

**Postconditions:** No data is changed; the Actor sees the enrollments matching the code they supplied.

**Note on the Student actor.** A Student is deliberately *not* an actor here, though an earlier version of this use case listed one. A Student reads their enrolled courses through **UC-16**, where the system identifies them from their session rather than from a code they type. The two use cases answer the same question; only UC-16 does it without a caller-supplied identifier that could name somebody else.

**Note on identifiers.** Every code above is a business code — a student code, a course code. No actor ever handles or types a database identifier, in this or any other use case.

**Related User Story:** US-5.5.

---

## UC-21: Login

- **Actor:** Registrar, Librarian, Course Administrator, or Student
- **Preconditions:** The actor holds an existing user account.
- **Trigger:** Actor submits a username and password.

**Main Flow**
1. Actor enters username and password.
2. System validates the username exists and the password matches the account's current password.
3. System starts an authenticated session for the actor, carrying their role.
4. System indicates to the actor whether their account requires a password change before further use. *(Identity.3)*

**Alternate / Exception Flows**
- **2a.** Username does not exist, or password does not match → system rejects with an authentication error; return to step 1.

**Postconditions:** The actor has an authenticated session scoped to their role. If the account is still using its initial password, the only action available in that session is UC-22 (Change Password).

**Related Rules:** Identity.2, Identity.3.

---

## UC-22: Change Password

- **Actor:** Registrar, Librarian, Course Administrator, or Student
- **Preconditions:** The actor has an authenticated session (UC-21). This includes an account still using its system-issued initial password — in fact, such an account cannot do anything else until it completes this use case.
- **Trigger:** Actor submits their current password, a new password, and the new password retyped.

**Main Flow**
1. Actor enters current password, new password, and retyped new password.
2. System validates the retyped new password matches the new password.
3. System validates the current password matches the account's current password.
4. System validates the new password meets the minimum password policy and differs from the current password.
5. System replaces the account's password with the new one and clears the "must change password" state, if set. *(Identity.3–5)*
6. System confirms the password has been changed.

**Alternate / Exception Flows**
- **2a.** Retyped new password does not match the new password → system rejects with a validation error; return to step 1.
- **3a.** Current password does not match → system rejects with an authentication error; return to step 1.
- **4a.** New password fails the minimum policy, or is identical to the current password → system rejects with a validation error; return to step 1.

**Postconditions:** The account's password is changed; if it was previously recoverable by a Registrar as an initial password (UC-23), it is no longer recoverable by anyone. There is no recovery path in this design for an actor who cannot authenticate at all (i.e., has forgotten their current password with no active session) — that scenario is out of scope and must be handled operationally.

**Related Rules:** Identity.3–5.

---

## UC-23: View Student's Initial Password

- **Actor:** Registrar
- **Preconditions:** The target student's user account exists.
- **Trigger:** Registrar requests to view a student's initial (system-issued) password, e.g. from that student's detail view.

**Main Flow**
1. Registrar selects a student.
2. System checks whether the student's account is still using its initial password. *(Identity.5)*
3. System returns the initial password.

**Alternate / Exception Flows**
- **2a.** The student has already changed their password → system indicates the initial password is no longer available to anyone, including the Registrar; no password is returned. *(Identity.4, Identity.5)*

**Postconditions:** No data is changed.

**Related Rules:** Identity.4, Identity.5.

---

## UC-24: Create Staff Account

- **Actor:** System Administrator
- **Preconditions:** None — the System Administrator holds an authenticated session.
- **Trigger:** System Administrator submits a username, a role (Registrar, Librarian, or Course Administrator), and requests the account be created.

**Main Flow**
1. System Administrator selects a role and enters a username for the new staff account.
2. System validates the username is not already in use. *(Identity.2)*
3. System validates the requested role is one of Registrar, Librarian, or Course Administrator. *(Identity.6)*
4. System generates an initial password, creates the account in a "must change password" state, and returns the initial password once. *(Identity.3, Identity.6)*

**Alternate / Exception Flows**
- **2a.** Username already in use → system rejects with a validation error; return to step 1.
- **3a.** Requested role is System Administrator, or any other value outside the 3 staff roles → system rejects with a validation error; return to step 1.

**Postconditions:** A new staff account exists, disabled-for-normal-use until its first password change, exactly like an auto-provisioned Student account (Identity.3). The System Administrator sees the initial password exactly once, in this response.

**Related Rules:** Identity.2, Identity.3, Identity.6.

---

## UC-25: Deactivate/Reactivate Staff Account

- **Actor:** System Administrator
- **Preconditions:** The target staff account exists.
- **Trigger:** System Administrator selects a staff account and toggles it disabled or enabled.

**Main Flow**
1. System Administrator selects an existing staff account.
2. System Administrator sets the account's enabled state to disabled (or, for an already-disabled account, back to enabled).
3. System updates the account accordingly. *(Identity.7)*

**Alternate / Exception Flows**
- None — disabling and re-enabling are symmetric and idempotent from the actor's point of view.

**Postconditions:** A disabled account cannot log in (UC-21) until a System Administrator re-enables it. No data the account previously created is affected.

**Related Rules:** Identity.7.

---

## Use Case Summary Table

| Use Case | Primary Actor | Business Rules |
| -------- | -------------- | --------------- |
| UC-1 Register Student | Registrar | Student.1–4; Identity.1–3 |
| UC-2 Update Student Details | Registrar | Student.2–4 |
| UC-3 Remove Student | Registrar | §5 student removal |
| UC-4 Add Book | Librarian | Book.1, Book.3, Book.4 |
| UC-5 Assign Book to Student | Librarian | Book.2, Book.4 |
| UC-6 Unassign Book | Librarian | Book.3, Book.5 |
| UC-7 Remove Book | Librarian | §5 book removal |
| UC-8 Create Course | Course Administrator | Course.1–3 |
| UC-9 Update Course | Course Administrator | Course.2–3 |
| UC-10 Remove Course | Course Administrator | §5 course removal |
| UC-11 Enroll Student in Course | Registrar | Enrollment.1–3 |
| UC-12 End Enrollment | Registrar | Enrollment.4 |
| UC-13 View/Search Students | Registrar | — (read-only) |
| UC-14 View/Search Books | Librarian | — (read-only) |
| UC-15 View/Search Courses | Course Administrator | — (read-only) |
| UC-16 View Own Record, Books & Courses | Student | — (read-only) |
| UC-17 View Student Detail | Registrar/Librarian/Course Administrator | — (read-only) |
| UC-18 View Book Detail | Librarian/Student | — (read-only) |
| UC-19 View Course Detail | Course Administrator/Registrar/Student | — (read-only) |
| UC-20 Look Up Enrollments | Registrar/Course Administrator | Enrollment.4 (read-only) |
| UC-21 Login | Registrar/Librarian/Course Administrator/Student | Identity.2–3 |
| UC-22 Change Password | Registrar/Librarian/Course Administrator/Student | Identity.3–5 |
| UC-23 View Student's Initial Password | Registrar | Identity.4–5 |
| UC-24 Create Staff Account | System Administrator | Identity.2–3, Identity.6 |
| UC-25 Deactivate/Reactivate Staff Account | System Administrator | Identity.7 |
