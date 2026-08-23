# Student Management System — Business Requirements

## 1. Purpose

Define the business rules governing a Student Management domain: students, the books they own, and the courses they take. This document describes **what must always be true about the data and the relationships between entities** — it does not prescribe how the system is built.

---

## 2. Business Entities

### Student

A person registered in the system.

| Attribute       | Business Meaning                                  |
| --------------- | --------------------------------------------------- |
| Student Code    | Unique identifier assigned to the student            |
| First Name      | Given name                                           |
| Last Name       | Family name                                          |
| Email           | Contact email address                                |
| Date of Birth   | The student's birth date                             |

### Book

An item that may be owned by a student.

| Attribute      | Business Meaning                          |
| -------------- | -------------------------------------------- |
| ISBN           | Unique identifier for the book                |
| Title          | Title of the book                             |
| Author         | Author of the book                            |
| Published Date | Date the book was published                   |
| Owner          | The student who currently owns the book (may be none) |

### Course

A course that students may take.

| Attribute    | Business Meaning                       |
| ------------ | ----------------------------------------- |
| Course Code  | Unique identifier for the course           |
| Name         | Title of the course                        |
| Description  | Description of the course content          |
| Credits      | Number of credits the course is worth      |

### User Account

A login identity that lets an actor authenticate into the system. Every actor (System Administrator, Registrar, Librarian, Course Administrator, Student) acts through one of these; a Student's account is created automatically when they are registered (see Identity.1), and a staff account (Registrar, Librarian, Course Administrator) is created by a System Administrator (see Identity.6).

| Attribute             | Business Meaning                                                                 |
| ---------------------- | --------------------------------------------------------------------------------- |
| Username               | Unique login identifier; for a Student, this is always their email address        |
| Password               | The account's current credential; never stored or displayed in recoverable form once chosen by the account holder |
| Role                   | One of System Administrator, Registrar, Librarian, Course Administrator, Student — determines access |
| Must Change Password   | Whether the account is still using its system-issued initial password and must replace it before normal use |
| Enabled                | Whether the account may currently log in; a System Administrator may disable a staff account without deleting it |

---

## 3. Relationships

### Student ↔ Book — Ownership

- One student may own many books.
- Each book is owned by **at most one** student at a time.
- A book may exist without an owner.

### Student ↔ Course — Enrollment

- One student may enroll in many courses.
- One course may have many students enrolled.
- A student may not hold more than one enrollment in the same course.

### Student ↔ User Account — Login Identity

- Every student has **exactly one** associated user account, created automatically at registration.
- The account's username is always the student's email; if the student's email changes, the account's username changes with it.

### System Administrator ↔ Staff User Account — Provisioning

- A staff user account (Registrar, Librarian, or Course Administrator) is created only by a System Administrator.
- A System Administrator account is never created through the application — it exists only as a pre-seeded, out-of-band identity, to prevent an account from ever granting itself System Administrator privileges.

---

## 4. Business Rules (Invariants)

### Student

1. A student's **student code** must be unique across all students.
2. A student's **email** must be unique across all students and must be a valid email address.
3. First name and last name are mandatory and cannot be blank.
4. Date of birth must be a real, valid date.
5. A student must exist before any book or course can be associated with them.

### Book

1. A book's **ISBN** must be unique across all books.
2. A book can be owned by at most one student at any point in time.
3. A book is allowed to have no owner (an unassigned book is a valid state).
4. A book cannot be assigned to a student who does not exist.
5. Ending a book's ownership link does not delete the book — the book continues to exist as an unassigned item.

### Course

1. A course's **course code** must be unique across all courses.
2. A course's name is mandatory and cannot be blank.
3. A course's credit value must be a positive number.

### Enrollment

1. A student cannot enroll in a course they are already enrolled in — attempting to do so is a duplicate and must be rejected.
2. A student cannot be enrolled in a course that does not exist.
3. A course cannot have an enrollment for a student who does not exist.
4. Ending an enrollment removes only the link between the student and the course — neither the student nor the course is deleted as a result.

### User Account (Identity)

1. A user account is created automatically for a student the moment they are registered — never as a separate manual step.
2. A user account's **username** must be unique across all accounts; for a Student account it equals the student's email.
3. A newly created student account starts in a "must change password" state and cannot be used for anything beyond changing its own password until that password is replaced.
4. Once an account holder replaces their initial password, the new password is never stored or displayed in a recoverable form again — not even to the Registrar.
5. Until an account holder replaces their initial password, the Registrar may look up that initial password on demand. This access ends permanently the moment the password is replaced.
6. A staff account (Registrar, Librarian, Course Administrator) is created only by a System Administrator, never automatically and never by self-registration. A System Administrator account itself is never created through the application.
7. A System Administrator may disable a staff account at any time; a disabled account cannot log in until a System Administrator re-enables it. Disabling an account does not delete it or affect any records the account holder previously created.
8. A System Administrator may see which accounts are signed in at any moment, and may end any signed-in session other than their own. Ending a session revokes only that session: the account itself is untouched and its holder may sign in again immediately. Ending a session is not the same as disabling an account (Identity.7) — disabling prevents the *next* sign-in and leaves the current session running, while ending a session stops the current one and permits the next.

---

## 5. Lifecycle Rules

### When a book is removed
- The book and its ownership link are removed.
- The student who owned it is otherwise unaffected and continues to exist.

### When a course is removed
- The course and every enrollment tied to it are removed.
- All students who were enrolled remain in the system, unaffected.

### When a student is removed
- Every book the student owned becomes unassigned (ownership cleared); the books themselves are **not** deleted.
- Every enrollment the student held is removed; the courses themselves are **not** affected.
- The student's associated user account is also removed. *(Identity.1)*
- The student record is removed.

---

## 6. Data Integrity Rules

- Uniqueness must hold for: student code, student email, book ISBN, course code, user account username.
- A given (student, course) pairing may exist as an active enrollment at most once.
- Every book's owner, if set, must reference a student that exists.
- Every enrollment must reference a student and a course that both exist.
- Every student has exactly one user account, created automatically and removed automatically with the student.

---

## 7. Out of Scope

This document defines business rules and domain invariants only. It intentionally excludes system architecture, technology choices, storage design, interface design, and any other implementation concerns — those are the subject of a separate technical design document.
