package org.phuchoang.management.enrollment.internal;

import java.time.Instant;
import java.util.List;
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

  // Selects c.course_code alongside the enrollment's own columns -- EnrollmentRow only carries
  // the surrogate course_id, but EnrollmentLookup.findByStudent's callers (EnrollmentService)
  // need CourseCode to resolve each row's CourseSummary via CourseLookup.summariesOf, so the join
  // result needs its own Row type (EnrollmentCourseRow) rather than EnrollmentRow itself.
  //
  // Keyset pagination on the compound (enrolled_at, id) key (PM-045): enrolled_at alone isn't
  // unique, so two enrollments created in the same millisecond would otherwise collide or get
  // silently skipped -- the surrogate id breaks the tie. No separate COUNT(*) (PM-043): the caller
  // fetches limit+1 and trims, the same convention as student/course/book.
  @Query("""
      SELECT e.id AS id, e.student_id AS student_id, e.course_id AS course_id,
             e.enrolled_at AS enrolled_at, c.course_code AS course_code
      FROM enrollments e
      JOIN courses c ON c.id = e.course_id
      WHERE e.student_id = :studentId
        AND (:afterEnrolledAt IS NULL
             OR e.enrolled_at > :afterEnrolledAt
             OR (e.enrolled_at = :afterEnrolledAt AND e.id > :afterId))
      ORDER BY e.enrolled_at, e.id
      LIMIT :limit
      """)
  List<EnrollmentCourseRow> findByStudentId(
      Long studentId, Instant afterEnrolledAt, Long afterId, int limit);

  // The roster half of EnrollmentRepository.search. Returns EnrollmentRow, not EnrollmentCourseRow:
  // every row in this page shares the one course the caller filtered on, so there is nothing per-row
  // for a joined course_code column to carry -- JdbcEnrollmentRepository threads the caller's
  // CourseCode through instead, the same way findByStudentAndCourse already does.
  @Query("""
      SELECT e.* FROM enrollments e
      JOIN courses c ON c.id = e.course_id
      WHERE c.course_code = :courseCode
        AND (:afterEnrolledAt IS NULL
             OR e.enrolled_at > :afterEnrolledAt
             OR (e.enrolled_at = :afterEnrolledAt AND e.id > :afterId))
      ORDER BY e.enrolled_at, e.id
      LIMIT :limit
      """)
  List<EnrollmentRow> findByCourseCode(
      String courseCode, Instant afterEnrolledAt, Long afterId, int limit);

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
