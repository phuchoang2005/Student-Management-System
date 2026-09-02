package org.phuchoang.management.course;

import java.util.Collection;
import java.util.Map;

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

  /** Consumed by {@code EnrollmentService.getDetail} (06-low-level-design.md §7) to embed the linked course's summary. */
  CourseSummary summaryOf(CourseCode code);

  /** One bulk lookup for every course named in {@code codes}. A code naming no course is simply
   * absent from the result — callers decide how to treat a code they expected to resolve. Backs
   * EnrollmentService's batch course resolution (PM-046) in place of one summaryOf call per row. */
  Map<CourseCode, CourseSummary> summariesOf(Collection<CourseCode> codes);
}
