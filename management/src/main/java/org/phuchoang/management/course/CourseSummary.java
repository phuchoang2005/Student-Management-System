package org.phuchoang.management.course;

/**
 * Read-model returned by {@link CourseLookup#summaryOf(CourseCode)} so consuming modules (e.g.
 * {@code enrollment}) can embed a course's summary fields without depending on {@code course}'s
 * internal layers, mirroring {@code student.StudentSummary} (06-low-level-design.md §4.8). Placed
 * at the module root, not {@code domain/}, for the same "published language" reason as {@link
 * CourseCode}. Carries no surrogate id: the id is a database concern and never crosses the HTTP
 * boundary (api-specification.md §5 decision #9).
 */
public record CourseSummary(String courseCode, String name, int credits) {}
