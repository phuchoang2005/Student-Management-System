package org.phuchoang.management.enrollment.port;

import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.enrollment.domain.Enrollment;
import org.phuchoang.management.student.StudentId;

/** Scoped to what US-4.1 (enroll) and US-4.2 (end, incl. the cascade listeners) need. */
public interface EnrollmentRepository {

  boolean existsByStudentAndCourse(StudentId studentId, CourseCode courseCode);

  Enrollment save(Enrollment enrollment);

  void deleteByStudentAndCourse(StudentId studentId, CourseCode courseCode);

  /** Backs {@code EnrollmentService.onStudentDeleted} (06-low-level-design.md §13). */
  void deleteByStudentId(StudentId studentId);

  /** Backs {@code EnrollmentService.onCourseDeleted} (06-low-level-design.md §13). */
  void deleteByCourseCode(CourseCode courseCode);
}
