package org.phuchoang.management.book.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

interface SpringDataBookRepository extends CrudRepository<BookRow, Long> {

  boolean existsByIsbn(String isbn);

  Optional<BookRow> findByIsbn(String isbn);

  @Query("""
      SELECT * FROM books
      WHERE owner_id = :ownerId
        AND (:afterKey IS NULL OR isbn > :afterKey)
      ORDER BY isbn
      LIMIT :limit
      """)
  List<BookRow> findByOwnerId(Long ownerId, String afterKey, int limit);

  // Keyset pagination (PM-045): the caller always asks for limit+1 rows so JdbcBookRepository can
  // tell "exactly filled the page" apart from "one more page exists" without a separate COUNT(*)
  // query. FULLTEXT (PM-044/V5 migration) replaces the old leading-wildcard LIKE scan across
  // isbn/title/author; a blank/null query falls through to the unfiltered branch, same as before.
  @Query("""
      SELECT * FROM books
      WHERE (:query IS NULL OR :query = ''
             OR MATCH(isbn, title, author) AGAINST (:query IN BOOLEAN MODE))
        AND (:ownerId IS NULL OR owner_id = :ownerId)
        AND (:afterKey IS NULL OR isbn > :afterKey)
      ORDER BY isbn
      LIMIT :limit
      """)
  List<BookRow> search(String query, Long ownerId, String afterKey, int limit);

  void deleteByIsbn(String isbn);

  @Modifying
  @Query("UPDATE books SET owner_id = NULL WHERE owner_id = :studentId")
  void clearOwnerByStudentId(Long studentId);
}
