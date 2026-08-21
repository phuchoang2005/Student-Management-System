package org.phuchoang.management.course.web.dto;

import java.time.Instant;

/**
 * The course record alone. The enrolled-student roster is deliberately <em>not</em> embedded: it is
 * a separately paged, separately authorized read ({@code GET /api/v1/enrollments?courseCode=},
 * open to the Registrar and Course Administrator but not to a Student browsing the catalogue), and
 * folding it in here would hand every reader of a course record the names of everyone taking it.
 * This replaces the {@code roster} field that was always {@code []}.
 */
public record CourseDetailDto(
    String courseCode,
    String name,
    String description,
    int credits,
    Instant createdAt,
    Instant updatedAt) {}
