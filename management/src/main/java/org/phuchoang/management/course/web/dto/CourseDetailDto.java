package org.phuchoang.management.course.web.dto;

import java.time.Instant;

/**
 * The course record plus how many students are enrolled. The roster itself is deliberately
 * <em>not</em> embedded: it is a separately paged, separately authorized read ({@code GET
 * /api/v1/enrollments?courseCode=}, open to the Registrar and Course Administrator but not to a
 * Student browsing the catalogue), and folding it in here would hand every reader of a course record
 * the names of everyone taking it.
 *
 * <p>{@code enrolledCount} is not in tension with that: "how many" is not "who". The count is what
 * the Registrar's catalogue view needs and it identifies nobody.
 */
public record CourseDetailDto(
    String courseCode,
    String name,
    String description,
    int credits,
    long enrolledCount,
    Instant createdAt,
    Instant updatedAt) {}
