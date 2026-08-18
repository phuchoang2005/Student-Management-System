package org.phuchoang.management.student.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

interface SpringDataStudentRepository extends CrudRepository<StudentRow, Long> {

  Optional<StudentRow> findByStudentCode(String studentCode);

  boolean existsByStudentCode(String studentCode);

  boolean existsByEmail(String email);

  boolean existsByEmailAndStudentCodeNot(String email, String studentCode);

  // Spring Data JDBC's string-based @Query methods (a) can't return Page (no auto count-query
  // derivation for string queries, unlike derived queries) and (b) don't auto-apply LIMIT/OFFSET
  // from a Pageable parameter the way derived queries do -- both are done explicitly instead:
  // JdbcStudentRepository pairs this with countBySearch and binds limit/offset itself.
  @Query("""
      SELECT * FROM students
      WHERE :query IS NULL OR :query = ''
         OR student_code LIKE CONCAT('%', :query, '%')
         OR first_name LIKE CONCAT('%', :query, '%')
         OR last_name LIKE CONCAT('%', :query, '%')
         OR email LIKE CONCAT('%', :query, '%')
      ORDER BY student_code
      LIMIT :limit OFFSET :offset
      """)
  List<StudentRow> search(String query, int limit, long offset);

  @Query("""
      SELECT COUNT(*) FROM students
      WHERE :query IS NULL OR :query = ''
         OR student_code LIKE CONCAT('%', :query, '%')
         OR first_name LIKE CONCAT('%', :query, '%')
         OR last_name LIKE CONCAT('%', :query, '%')
         OR email LIKE CONCAT('%', :query, '%')
      """)
  long countBySearch(String query);

  void deleteByStudentCode(String studentCode);
}
