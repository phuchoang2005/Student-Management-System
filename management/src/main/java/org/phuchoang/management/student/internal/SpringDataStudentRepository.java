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

  // Keyset pagination (PM-045) over the ft_students_search FULLTEXT index
  // (V5__add_fulltext_search_indexes.sql): a trailing wildcard gives prefix matching in BOOLEAN
  // MODE, :afterKey is the previous page's last student_code (null for the first page), and there
  // is no separate count query -- JdbcStudentRepository asks for limit + 1 rows and trims the
  // extra one itself to decide whether a nextCursor is warranted.
  @Query("""
      SELECT * FROM students
      WHERE (:query IS NULL OR :query = ''
             OR MATCH(student_code, first_name, last_name, email)
                AGAINST (:query IN BOOLEAN MODE))
        AND (:scopeId IS NULL OR id = :scopeId)
        AND (:afterKey IS NULL OR student_code > :afterKey)
      ORDER BY student_code
      LIMIT :limit
      """)
  List<StudentRow> search(String query, Long scopeId, String afterKey, int limit);

  void deleteByStudentCode(String studentCode);
}
