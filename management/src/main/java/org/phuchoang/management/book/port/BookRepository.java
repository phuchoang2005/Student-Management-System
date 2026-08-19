package org.phuchoang.management.book.port;

import java.util.Optional;
import org.phuchoang.management.book.domain.Book;
import org.phuchoang.management.book.domain.Isbn;

/** Scoped to what US-2.1 (add) and US-2.2 (assign owner) need. */
public interface BookRepository {

  boolean existsByIsbn(Isbn isbn);

  Optional<Book> findByIsbn(Isbn isbn);

  Book save(Book book);
}
