package org.phuchoang.management.book.internal;

import org.springframework.data.repository.CrudRepository;

interface SpringDataBookRepository extends CrudRepository<BookRow, Long> {

  boolean existsByIsbn(String isbn);
}
