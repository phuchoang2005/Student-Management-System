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
  //
  // search/browse are deliberately separate statements, not one query with a
  // "(:query IS NULL OR :query = '' OR MATCH(...))" branch: a single combined statement stopped
  // the planner from specializing per call and regressed even the no-filter case (+191% p95,
  // docs-v01/Benchmark/09-v01-vs-v00-conclusions.md §3) -- reopens IP-02/IP-03.
  @Query("""
      SELECT * FROM students
      WHERE (:scopeId IS NULL OR id = :scopeId)
        AND (:afterKey IS NULL OR student_code > :afterKey)
      ORDER BY student_code
      LIMIT :limit
      """)
  List<StudentRow> browse(Long scopeId, String afterKey, int limit);

  @Query("""
      SELECT * FROM students
      WHERE MATCH(student_code, first_name, last_name, email) AGAINST (:query IN BOOLEAN MODE)
        AND (:scopeId IS NULL OR id = :scopeId)
        AND (:afterKey IS NULL OR student_code > :afterKey)
      ORDER BY student_code
      LIMIT :limit
      """)
  List<StudentRow> search(String query, Long scopeId, String afterKey, int limit);

  void deleteByStudentCode(String studentCode);
}
