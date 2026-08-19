package org.phuchoang.management.book.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

interface SpringDataBookRepository extends CrudRepository<BookRow, Long> {

  boolean existsByIsbn(String isbn);

  Optional<BookRow> findByIsbn(String isbn);

  List<BookRow> findByOwnerId(Long ownerId);

  @Query("""
      SELECT * FROM books
      WHERE owner_id = :ownerId
      ORDER BY isbn
      LIMIT :limit OFFSET :offset
      """)
  List<BookRow> findByOwnerId(Long ownerId, int limit, long offset);

  @Query("SELECT COUNT(*) FROM books WHERE owner_id = :ownerId")
  long countByOwnerId(Long ownerId);

  // Same LIMIT/OFFSET + separate count-query idiom as SpringDataCourseRepository.search /
  // SpringDataStudentRepository.search -- Spring Data JDBC's string-based @Query methods can't
  // derive a Page-returning method or auto-apply Pageable's LIMIT/OFFSET.
  @Query("""
      SELECT * FROM books
      WHERE (:query IS NULL OR :query = ''
         OR isbn LIKE CONCAT('%', :query, '%')
         OR title LIKE CONCAT('%', :query, '%')
         OR author LIKE CONCAT('%', :query, '%'))
        AND (:ownerId IS NULL OR owner_id = :ownerId)
      ORDER BY isbn
      LIMIT :limit OFFSET :offset
      """)
  List<BookRow> search(String query, Long ownerId, int limit, long offset);

  @Query("""
      SELECT COUNT(*) FROM books
      WHERE (:query IS NULL OR :query = ''
         OR isbn LIKE CONCAT('%', :query, '%')
         OR title LIKE CONCAT('%', :query, '%')
         OR author LIKE CONCAT('%', :query, '%'))
        AND (:ownerId IS NULL OR owner_id = :ownerId)
      """)
  long countBySearch(String query, Long ownerId);

  void deleteByIsbn(String isbn);
}
