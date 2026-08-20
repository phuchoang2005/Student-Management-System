package org.phuchoang.management.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.student.StudentId;

class EnrollmentTest {

  private final StudentId studentId = new StudentId(1L);
  private final CourseCode courseCode = new CourseCode("CS101");

  @Test
  void createsEnrollmentWithGeneratedEnrolledAt() {
    Enrollment enrollment = Enrollment.create(studentId, courseCode);

    assertThat(enrollment.id()).isNull();
    assertThat(enrollment.studentId()).isEqualTo(studentId);
    assertThat(enrollment.courseCode()).isEqualTo(courseCode);
    assertThat(enrollment.enrolledAt()).isNotNull();
  }

  @Test
  void reconstituteRehydratesAllFields() {
    EnrollmentId id = new EnrollmentId(5L);
    Instant enrolledAt = Instant.parse("2026-01-15T09:24:00Z");

    Enrollment enrollment = Enrollment.reconstitute(id, studentId, courseCode, enrolledAt);

    assertThat(enrollment.id()).isEqualTo(id);
    assertThat(enrollment.studentId()).isEqualTo(studentId);
    assertThat(enrollment.courseCode()).isEqualTo(courseCode);
    assertThat(enrollment.enrolledAt()).isEqualTo(enrolledAt);
  }
}
