-- V2__add_system_administrator_role.sql
-- PM-016: widen users.role to admit SYSTEM_ADMINISTRATOR (04-sprint-backlog.md §"PM-016").
-- chk_users_student_role's `role <> 'STUDENT'` branch already covers this new role correctly
-- (no student_id), so it needs no change.

ALTER TABLE users
    MODIFY COLUMN role ENUM('REGISTRAR','LIBRARIAN','COURSE_ADMINISTRATOR','STUDENT','SYSTEM_ADMINISTRATOR') NOT NULL;
