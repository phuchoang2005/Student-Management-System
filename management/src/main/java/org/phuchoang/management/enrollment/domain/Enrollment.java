package org.phuchoang.management.enrollment.domain;

import java.time.Instant;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.student.StudentId;

/**
 * No {@code Enrollment}-owned Value Object wraps a format invariant of its own — {@code
 * studentId}/{@code courseCode} reuse {@code student.StudentId}/{@code course.CourseCode}
 * directly, since Enrollment.1–4 are all cross-aggregate existence/uniqueness rules, not format
 * rules (06-low-level-design.md §7). There is no {@code update} use case (Enrollment.4 — "end
 * removes only the link"), so no field here is ever mutated after {@link #create}: no {@code
 * applyChanges}, no {@code updatedAt}.
 */
public class Enrollment {

  private EnrollmentId id;
  private final StudentId studentId;
  private final CourseCode courseCode;
  private final Instant enrolledAt;

  private Enrollment(EnrollmentId id, StudentId studentId, CourseCode courseCode, Instant enrolledAt) {
    this.id = id;
    this.studentId = studentId;
    this.courseCode = courseCode;
    this.enrolledAt = enrolledAt;
  }

  public static Enrollment create(StudentId studentId, CourseCode courseCode) {
    return new Enrollment(null, studentId, courseCode, Instant.now());
  }

  /**
   * Rehydrates an {@code Enrollment} from data already validated at write time (a DB row) —
   * bypasses nothing {@link #create} doesn't already bypass, since Enrollment has no invariant
   * checks of its own, but kept for the same reconstitute-vs-create symmetry as {@code
   * Course}/{@code Student}/{@code Book}.
   */
  public static Enrollment reconstitute(
      EnrollmentId id, StudentId studentId, CourseCode courseCode, Instant enrolledAt) {
    return new Enrollment(id, studentId, courseCode, enrolledAt);
  }

  public EnrollmentId id() {
    return id;
  }

  public StudentId studentId() {
    return studentId;
  }

  public CourseCode courseCode() {
    return courseCode;
  }

  public Instant enrolledAt() {
    return enrolledAt;
  }
}
