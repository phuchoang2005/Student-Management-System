package org.phuchoang.management.course.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

interface SpringDataCourseRepository extends CrudRepository<CourseRow, Long> {

  Optional<CourseRow> findByCourseCode(String courseCode);

  boolean existsByCourseCode(String courseCode);

  // Spring Data JDBC's string-based @Query methods (a) can't return Page (no auto count-query
  // derivation for string queries, unlike derived queries) and (b) don't auto-apply LIMIT/OFFSET
  // from a Pageable parameter the way derived queries do -- both are done explicitly instead:
  // JdbcCourseRepository pairs this with countBySearch and binds limit/offset itself, mirroring
  // SpringDataStudentRepository.
  @Query("""
      SELECT * FROM courses
      WHERE :query IS NULL OR :query = ''
         OR course_code LIKE CONCAT('%', :query, '%')
         OR name LIKE CONCAT('%', :query, '%')
      ORDER BY course_code
      LIMIT :limit OFFSET :offset
      """)
  List<CourseRow> search(String query, int limit, long offset);

  @Query("""
      SELECT COUNT(*) FROM courses
      WHERE :query IS NULL OR :query = ''
         OR course_code LIKE CONCAT('%', :query, '%')
         OR name LIKE CONCAT('%', :query, '%')
      """)
  long countBySearch(String query);

  // The two queries below read `enrollments`, which belongs to the enrollment module, joined in
  // plain SQL rather than reached through a Java call. That direction is forced: `enrollment`
  // already depends on `course` (CourseLookup, for Enrollment.2), so a `course -> enrollment` Java
  // edge would close a cycle `ApplicationModules.verify()` rejects. A SQL join is not a Java
  // dependency, which is the same escape SpringDataEnrollmentRepository documents in the other
  // direction (06-low-level-design.md §9.1).

  // LEFT JOIN, not JOIN: a course nobody has enrolled in must come back as 0 rather than drop out
  // of the result, or the caller cannot tell "no enrollments" from "no such course".
  @Query("""
      SELECT c.course_code AS course_code, COUNT(e.id) AS enrolled_count
      FROM courses c
      LEFT JOIN enrollments e ON e.course_id = c.id
      WHERE c.course_code IN (:courseCodes)
      GROUP BY c.course_code
      """)
  List<CourseEnrollmentCountRow> enrollmentCountsFor(Collection<String> courseCodes);

  // Same shape as SpringDataEnrollmentRepository.countByCourseCode, for the single-course read.
  @Query("""
      SELECT COUNT(*) FROM enrollments e
      JOIN courses c ON c.id = e.course_id
      WHERE c.course_code = :courseCode
      """)
  long enrollmentCountOf(String courseCode);

  void deleteByCourseCode(String courseCode);
}
