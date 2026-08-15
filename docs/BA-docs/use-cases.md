# Student Management System — Use Cases

Derived from [req.md](./req.md) and [user-stories.md](./user-stories.md). Actors and flows describe interaction between a user and the system; business rule references trace back to the source invariants.

---

## Actors

| Actor | Description |
| ----- | ----------- |
| **Registrar** | Manages student records and enrollments. |
| **Librarian** | Manages the book catalog and book ownership assignments. |
| **Course Administrator** | Manages course offerings. |
| **Student** | May look up their own books/courses and self-enroll, depending on system policy. |

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
7. System confirms creation.

**Alternate / Exception Flows**
- **3a.** Student code already exists → system rejects with a duplicate-code error; return to step 1.
- **3b.** Email already exists or is malformed → system rejects with a validation error; return to step 1.
- **4a.** First or last name is blank → system rejects with a validation error; return to step 1.
- **5a.** Date of birth is invalid → system rejects with a validation error; return to step 1.

**Postconditions:** A new, uniquely identified student exists in the system.

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

- **Actor:** Registrar (or Student, if self-service is enabled)
- **Preconditions:** The student and course both exist.
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

- **Actor:** Registrar (or Student, if self-service is enabled)
- **Preconditions:** An active enrollment exists for the (student, course) pair.
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
3. System returns the matching record(s).

**Alternate / Exception Flows**
- **2a.** No student matches the given code or search term → system returns an empty result.

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
3. System returns the matching record(s).

**Alternate / Exception Flows**
- **2a.** No book matches the given ISBN or search term → system returns an empty result.

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
3. System returns the matching record(s).

**Alternate / Exception Flows**
- **2a.** No course matches the given code or search term → system returns an empty result.

**Extension Points**
- **Select a result:** Administrator selects one course from the results → continues at **UC-19: View Course Detail** (which includes the full enrolled-student roster).

**Postconditions:** No data is changed; matching course summaries are displayed to the Administrator.

**Related User Story:** US-5.3.

---

## UC-16: View Own Books, Courses & Enrollments

- **Actor:** Student
- **Preconditions:** The student record exists and the Student actor is looking up their own data.
- **Trigger:** Student requests to view the books they own and/or the courses they are enrolled in.

**Main Flow**
1. Student selects "my books" and/or "my courses."
2. System looks up books owned by the student and returns a summary for each.
3. System looks up active enrollments for the student and returns a course summary for each.
4. System returns the requested information.

**Alternate / Exception Flows**
- **2a.** Student owns no books → system returns an empty list for books.
- **3a.** Student has no active enrollments → system returns an empty list for courses.

**Extension Points**
- **Select a book:** Student selects one owned book → continues at **UC-18: View Book Detail**.
- **Select a course:** Student selects one enrolled course → continues at **UC-19: View Course Detail**.

**Postconditions:** No data is changed; the Student sees summaries of their own current books and course enrollments.

**Related User Story:** US-5.4.

---

## UC-17: View Student Detail

- **Actor:** Registrar
- **Extends:** UC-13 (View/Search Students)
- **Preconditions:** A student search has returned at least one result.
- **Trigger:** Registrar selects a student from the search results.

**Main Flow**
1. Registrar selects one student from the list of search results.
2. System retrieves the full record for the selected student: all student fields, the list of books they currently own, and the list of courses they are currently enrolled in.
3. System displays the full student detail.

**Alternate / Exception Flows**
- **2a.** The selected student no longer exists (e.g., removed after the search ran) → system returns a not-found result; Registrar returns to the search results.

**Postconditions:** No data is changed; the Registrar sees the selected student's complete detail, including owned books and enrollments.

**Related User Story:** US-5.1.

---

## UC-18: View Book Detail

- **Actor:** Librarian (also reachable by Student via UC-16)
- **Extends:** UC-14 (View/Search Books), UC-16 (View Own Books, Courses & Enrollments)
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

- **Actor:** Course Administrator (also reachable by Student via UC-16)
- **Extends:** UC-15 (View/Search Courses), UC-16 (View Own Books, Courses & Enrollments)
- **Preconditions:** A course search or "my courses" list has returned at least one result.
- **Trigger:** Actor selects a course from the results.

**Main Flow**
1. Actor selects one course from the list of results.
2. System retrieves the full record for the selected course: course code, name, description, credits, and the full roster of currently enrolled students.
3. System displays the full course detail.

**Alternate / Exception Flows**
- **2a.** The selected course no longer exists (e.g., removed after the results were shown) → system returns a not-found result; Actor returns to the results list.

**Postconditions:** No data is changed; the Actor sees the selected course's complete detail, including its enrolled-student roster.

**Related User Story:** US-5.3, US-5.4.

---

## UC-20: View Enrollment Detail

- **Actor:** Registrar, Course Administrator, or Student
- **Extends:** UC-17 (View Student Detail), UC-19 (View Course Detail), UC-16 (View Own Books, Courses & Enrollments)
- **Preconditions:** A student's enrollment list or a course's roster is being displayed, listing at least one enrollment.
- **Trigger:** Actor selects a specific enrollment (a student-course pairing) from the list.

**Main Flow**
1. Actor selects one enrollment entry from a student's enrollment list or a course's roster.
2. System retrieves the enrollment for the selected (student, course) pair.
3. System retrieves the linked student's summary information and the linked course's summary information.
4. System displays the full enrollment detail (student summary + course summary).

**Alternate / Exception Flows**
- **2a.** The selected enrollment no longer exists (e.g., it was ended after the list was shown) → system returns a not-found result; Actor returns to the list. *(req.md §4 Enrollment.4)*

**Postconditions:** No data is changed; the Actor sees the full detail of the selected enrollment.

**Related User Story:** US-5.5.

---

## Use Case Summary Table

| Use Case | Primary Actor | Business Rules |
| -------- | -------------- | --------------- |
| UC-1 Register Student | Registrar | Student.1–4 |
| UC-2 Update Student Details | Registrar | Student.2–4 |
| UC-3 Remove Student | Registrar | §5 student removal |
| UC-4 Add Book | Librarian | Book.1, Book.3, Book.4 |
| UC-5 Assign Book to Student | Librarian | Book.2, Book.4 |
| UC-6 Unassign Book | Librarian | Book.3, Book.5 |
| UC-7 Remove Book | Librarian | §5 book removal |
| UC-8 Create Course | Course Administrator | Course.1–3 |
| UC-9 Update Course | Course Administrator | Course.2–3 |
| UC-10 Remove Course | Course Administrator | §5 course removal |
| UC-11 Enroll Student in Course | Registrar/Student | Enrollment.1–3 |
| UC-12 End Enrollment | Registrar/Student | Enrollment.4 |
| UC-13 View/Search Students | Registrar | — (read-only) |
| UC-14 View/Search Books | Librarian | — (read-only) |
| UC-15 View/Search Courses | Course Administrator | — (read-only) |
| UC-16 View Own Books, Courses & Enrollments | Student | — (read-only) |
| UC-17 View Student Detail | Registrar | — (read-only) |
| UC-18 View Book Detail | Librarian/Student | — (read-only) |
| UC-19 View Course Detail | Course Administrator/Student | — (read-only) |
| UC-20 View Enrollment Detail | Registrar/Course Administrator/Student | Enrollment.4 (read-only) |
