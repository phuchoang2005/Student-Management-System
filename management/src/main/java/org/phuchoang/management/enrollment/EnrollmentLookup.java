package org.phuchoang.management.enrollment;

import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.student.StudentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public read-only API other modules use to reference a Student's active enrollments without
 * depending on {@code enrollment}'s internal layers, mirroring {@code course.CourseLookup}/{@code
 * student.StudentLookup}/{@code book.BookLookup}. {@code me} — this interface's only consumer so
 * far — needs a Student-scoped, paginated view for {@code GET /api/v1/me/courses} (US-5.4). Returns {@link CourseSummary} directly, not a bespoke
 * "enrollment summary" type — an enrollment only ever matters to the caller as "which course",
 * same read-model {@code EnrollmentService.getDetail} already composes (US-5.5).
 */
public interface EnrollmentLookup {

  /** Consumed by {@code MeController} (US-5.4) to serve the caller's own enrolled courses, one page at a time. */
  Page<CourseSummary> findByStudent(StudentId studentId, Pageable pageable);
}
