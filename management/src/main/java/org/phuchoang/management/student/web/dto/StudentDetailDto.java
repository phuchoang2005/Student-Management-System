package org.phuchoang.management.student.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The student record alone. Owned books and active enrollments are deliberately <em>not</em>
 * embedded: each is a separately paged, separately authorized read ({@code GET
 * /api/v1/books?ownerStudentCode=} for the Librarian, {@code GET /api/v1/enrollments?studentCode=}
 * for the Registrar and Course Administrator), and a role that may read a student record is not
 * automatically allowed to read both sides of it. This replaces the {@code books}/{@code courses}
 * fields that were always {@code []}.
 */
public record StudentDetailDto(
    String studentCode,
    String firstName,
    String lastName,
    String email,
    LocalDate dateOfBirth,
    Instant createdAt,
    Instant updatedAt) {}
