package org.phuchoang.management.book.internal;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface SpringDataBookRepository extends CrudRepository<BookRow, Long> {

  boolean existsByIsbn(String isbn);

  Optional<BookRow> findByIsbn(String isbn);

  void deleteByIsbn(String isbn);
}
