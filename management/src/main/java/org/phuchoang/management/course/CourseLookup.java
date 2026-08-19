package org.phuchoang.management.course;

/**
 * Public read-only API other modules use to reference a {@code Course} without depending on
 * {@code course}'s internal layers (06-low-level-design.md §5), mirroring {@code
 * student.StudentLookup}. Keyed by {@link CourseCode}, not a surrogate id: {@code enrollment} —
 * this interface's only consumer so far — only ever holds a course's business-key code (the
 * {@code courseCode} field on {@code EnrollmentCreateRequest}), never its numeric id, and {@code
 * EnrollmentRepository}'s own port methods are likewise typed in {@code CourseCode}
 * (06-low-level-design.md §7, §9.1).
 */
public interface CourseLookup {

  boolean existsByCode(CourseCode code);
}
