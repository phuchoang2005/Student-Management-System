package org.phuchoang.management.book.port;

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

  /**
   * UC-14 — matches isbn/title/author, optionally filtered by owner, paged. {@code query} may be
   * blank/{@code null}. Typed in {@link StudentId}, not {@code StudentCode}: {@code BookService}
   * resolves the caller's code to the {@code books.owner_id} value this matches on.
   */
  Page<Book> search(String query, StudentId ownerFilter, Pageable pageable);

  /** Backs {@code BookLookup.findByOwner} (US-5.4, {@code GET /api/v1/me/books}). */
  Page<Book> findByOwnerId(StudentId ownerId, Pageable pageable);

  Book save(Book book);

  void deleteByIsbn(Isbn isbn);

  /** {@code BookService.onStudentDeleted} (06-low-level-design.md §13) — clears ownership on every book the deleted student owned. */
  void clearOwnerByStudentId(StudentId studentId);
}
