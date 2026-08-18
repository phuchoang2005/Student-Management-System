-- V1__init_schema.sql
-- Charset utf8mb4, engine InnoDB throughout (05-database-schema.md §6).

CREATE TABLE students (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_code   VARCHAR(20)  NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    date_of_birth  DATE         NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_students_student_code UNIQUE (student_code),
    CONSTRAINT uq_students_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE courses (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_code   VARCHAR(20)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   TEXT         NULL,
    credits       SMALLINT     NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_courses_course_code UNIQUE (course_code),
    CONSTRAINT chk_courses_credits CHECK (credits > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE books (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    isbn            VARCHAR(20)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    author          VARCHAR(255) NOT NULL,
    published_date  DATE         NULL,
    owner_id        BIGINT       NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_books_isbn UNIQUE (isbn),
    CONSTRAINT fk_books_owner FOREIGN KEY (owner_id) REFERENCES students (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE enrollments (
    id           BIGINT   AUTO_INCREMENT PRIMARY KEY,
    student_id   BIGINT   NOT NULL,
    course_id    BIGINT   NOT NULL,
    enrolled_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_enrollments_student_course UNIQUE (student_id, course_id),
    CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username                    VARCHAR(255) NOT NULL,
    password_hash               CHAR(60)     NOT NULL,
    initial_password_encrypted  VARCHAR(255) NULL,
    role                        ENUM('REGISTRAR','LIBRARIAN','COURSE_ADMINISTRATOR','STUDENT') NOT NULL,
    student_id                  BIGINT       NULL,
    must_change_password        BOOLEAN      NOT NULL DEFAULT FALSE,
    version                     BIGINT       NOT NULL DEFAULT 0,
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_student_id UNIQUE (student_id),
    CONSTRAINT fk_users_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT chk_users_student_role CHECK (
        (role = 'STUDENT' AND student_id IS NOT NULL) OR (role <> 'STUDENT' AND student_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
