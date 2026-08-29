-- V5__add_fulltext_search_indexes.sql
-- PM-044: replaces leading-wildcard LIKE scans (hazard H1) with FULLTEXT lookups on the three
-- free-text-searched tables. books.owner_id and enrollments.course_id are FK columns already
-- indexed by side effect (01-benchmark-strategy.md §3.1) and are deliberately not touched here.

ALTER TABLE students
    ADD FULLTEXT INDEX ft_students_search (student_code, first_name, last_name, email);

ALTER TABLE courses
    ADD FULLTEXT INDEX ft_courses_search (course_code, name);

ALTER TABLE books
    ADD FULLTEXT INDEX ft_books_search (isbn, title, author);
