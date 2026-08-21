# In Student Page

- At the Students tabs, It should display directly the information's student, not a list.
- In the Courses tabs, It will show all the course which student have enrolled. The Students can click to the course to check more detail.
- In the Enrollments tabs, the Stduents shouldn't know logic about Enrollments, so remove it from the Students logic.
- In the My Boosk & Courses, Students doesn't need this logic, remove it because we can check books in Books tab, and Courses in Couuses tab above, so remove this tab.

# In Registra Page

- In Students tab, when the registra click to the student, it should show all the courses which the student have enrolled.
- In the Books, The Registra doesn't care about whether Student borrows any books or not, so remove Books search (and any Books logic involving) in Registra role.
- In the Courses tab, When the Registra click to the Course's information, the Registra should know all the students who enrolled the course.
- In the Enrollments tab, The Registra should interact with the student information through StudentCode, not StudentId. When the Registra click to look up an enrollments, the Registra should only type the StudentCode and the system will show all the courses that the student has enrolled.

# In Librarian Page

- In Students tab, this tab should show all the students with the search bar, and pagination. When the Librarian click to a student, Student's information and borrowed books of that student will be shown.
- In Courses tab, the Librarian shouldn't care about this information, so remove it from The Librarian.
- In Enrollments tab, the Librarian shouldn't care about the Enrollments, so remove it from The Librarian.

# In Course Administrator

- In Students tab, this role shouldn't care about Students, so remove it from the Course Administrator.
- In Books, this role shouldn't care about Books, so remove it from the Course Administrator.
- In Enrollments tab, this tab will show all the current courses. When the Administrator click to the course, it will show all students who have enrolled. the Administrator can also click to the student to check the student's profile.

> [!IMPORTANT]
> StudentId, or anything relevant like BookId, CourseId, are the only attribute that be interacted with database.
> User should only interact with StudentCode, ISBN,etc
