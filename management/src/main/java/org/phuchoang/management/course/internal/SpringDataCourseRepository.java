package org.phuchoang.management.course.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

interface SpringDataCourseRepository extends CrudRepository<CourseRow, Long> {

  Optional<CourseRow> findByCourseCode(String courseCode);

  boolean existsByCourseCode(String courseCode);

  // Keyset (cursor) pagination, not OFFSET/COUNT: PM-044/PM-045 replaced the old LIKE + COUNT(*)
  // pair with one FULLTEXT-backed query, ordered and filtered on course_code so JdbcCourseRepository
  // can ask for one extra row to detect whether a next page exists rather than pre-counting. `query`
  // has already been sanitized of boolean-mode operator characters by JdbcCourseRepository before it
  // reaches here.
  @Query("""
      SELECT * FROM courses
      WHERE (:query IS NULL OR :query = ''
             OR MATCH(course_code, name) AGAINST (:query IN BOOLEAN MODE))
        AND (:afterKey IS NULL OR course_code > :afterKey)
      ORDER BY course_code
      LIMIT :limit
      """)
  List<CourseRow> search(String query, String afterKey, int limit);

  @Query("SELECT * FROM courses WHERE course_code IN (:codes)")
  List<CourseRow> findByCourseCodeIn(Collection<String> codes);

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
