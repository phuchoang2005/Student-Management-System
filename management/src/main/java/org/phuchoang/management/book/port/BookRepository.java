package org.phuchoang.management.book.port;

import org.phuchoang.management.book.domain.Book;
import org.phuchoang.management.book.domain.Isbn;

/** Scoped to what US-2.1 (add) needs. */
public interface BookRepository {

  boolean existsByIsbn(Isbn isbn);

  Book save(Book book);
}
