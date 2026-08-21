package org.phuchoang.management.enrollment.port;

import java.util.Optional;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.enrollment.domain.Enrollment;
import org.phuchoang.management.student.StudentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Scoped to what US-4.1 (enroll), US-4.2 (end, incl. the cascade listeners), and US-5.5 (detail and
 * list views) need. Typed in {@link StudentId} rather than {@code StudentCode}: the code→id
 * translation happens once in {@code EnrollmentService}, above this port, because {@code
 * enrollments.student_id} is the column these methods actually match on.
 */
public interface EnrollmentRepository {

  boolean existsByStudentAndCourse(StudentId studentId, CourseCode courseCode);

  /** Backs {@code EnrollmentService.getDetail} (06-low-level-design.md §7, UC-20). */
  Optional<Enrollment> findByStudentAndCourse(StudentId studentId, CourseCode courseCode);

  /**
   * Backs {@code EnrollmentLookup.findByStudent} ({@code GET /api/v1/me/courses}) and the {@code
   * studentCode} half of {@code EnrollmentService.search}.
   */
  Page<Enrollment> findByStudentId(StudentId studentId, Pageable pageable);

  /** Backs the {@code courseCode} half of {@code EnrollmentService.search} — a course's roster. */
  Page<Enrollment> findByCourseCode(CourseCode courseCode, Pageable pageable);

  Enrollment save(Enrollment enrollment);

  void deleteByStudentAndCourse(StudentId studentId, CourseCode courseCode);

  /** Backs {@code EnrollmentService.onStudentDeleted} (06-low-level-design.md §13). */
  void deleteByStudentId(StudentId studentId);

  /** Backs {@code EnrollmentService.onCourseDeleted} (06-low-level-design.md §13). */
  void deleteByCourseCode(CourseCode courseCode);
}
