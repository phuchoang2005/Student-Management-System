package org.phuchoang.management.book.port;

import java.util.List;
import java.util.Optional;
import org.phuchoang.management.book.domain.Book;
import org.phuchoang.management.book.domain.Isbn;
import org.phuchoang.management.student.StudentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Scoped to what US-2.1 (add), US-2.2 (assign owner), US-2.3 (unassign), US-2.4 (remove), and
 * US-5.2 (search/lookup) need.
 */
public interface BookRepository {

  boolean existsByIsbn(Isbn isbn);

  Optional<Book> findByIsbn(Isbn isbn);

  /** UC-14 — matches isbn/title/author, optionally filtered by owner, paged. {@code query} may be blank/{@code null}. */
  Page<Book> search(String query, StudentId ownerFilter, Pageable pageable);

  /** Unpaginated — backs {@code StudentService.getDetail}'s embedded "owned books" list (US-5.1). */
  List<Book> findByOwnerId(StudentId ownerId);

  Book save(Book book);

  void deleteByIsbn(Isbn isbn);
}
