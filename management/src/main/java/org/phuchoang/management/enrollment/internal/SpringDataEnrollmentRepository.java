package org.phuchoang.management.enrollment.internal;

import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * {@code enrollments.course_id} is a surrogate FK, but {@code EnrollmentRepository}'s port is
 * typed in {@code CourseCode} — every method here that needs to match/resolve a course joins
 * against {@code courses} in plain SQL rather than importing across the module boundary
 * (06-low-level-design.md §9.1; a SQL join isn't a Java dependency, so {@code
 * ApplicationModules.verify()} doesn't flag it).
 */
interface SpringDataEnrollmentRepository extends CrudRepository<EnrollmentRow, Long> {

  @Query("""
      SELECT COUNT(*) FROM enrollments e
      JOIN courses c ON c.id = e.course_id
      WHERE e.student_id = :studentId AND c.course_code = :courseCode
      """)
  long countByStudentIdAndCourseCode(Long studentId, String courseCode);

  // Backs EnrollmentRepository.findByStudentAndCourse (US-5.5) -- same join as
  // countByStudentIdAndCourseCode, but returning the row itself rather than just its existence.
  @Query("""
      SELECT e.* FROM enrollments e
      JOIN courses c ON c.id = e.course_id
      WHERE e.student_id = :studentId AND c.course_code = :courseCode
      """)
  Optional<EnrollmentRow> findByStudentIdAndCourseCode(Long studentId, String courseCode);

  // Resolves courseCode -> courseId before an insert -- the caller (JdbcEnrollmentRepository)
  // only calls this once CourseLookup.existsByCode already guaranteed the row exists, so a null
  // result here would only mean a genuine data race, same as StudentLookup.summaryOf's findById.
  @Query("SELECT id FROM courses WHERE course_code = :courseCode")
  Long findCourseIdByCode(String courseCode);

  @Modifying
  @Query("""
      DELETE e FROM enrollments e
      JOIN courses c ON c.id = e.course_id
      WHERE e.student_id = :studentId AND c.course_code = :courseCode
      """)
  void deleteByStudentIdAndCourseCode(Long studentId, String courseCode);

  void deleteByStudentId(Long studentId);

  @Modifying
  @Query("""
      DELETE e FROM enrollments e
      JOIN courses c ON c.id = e.course_id
      WHERE c.course_code = :courseCode
      """)
  void deleteByCourseCode(String courseCode);
}
