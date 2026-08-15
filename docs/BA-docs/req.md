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
- The student record is removed.

---

## 6. Data Integrity Rules

- Uniqueness must hold for: student code, student email, book ISBN, course code.
- A given (student, course) pairing may exist as an active enrollment at most once.
- Every book's owner, if set, must reference a student that exists.
- Every enrollment must reference a student and a course that both exist.

---

## 7. Out of Scope

This document defines business rules and domain invariants only. It intentionally excludes system architecture, technology choices, storage design, interface design, and any other implementation concerns — those are the subject of a separate technical design document.
