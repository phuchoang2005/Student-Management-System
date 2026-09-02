package org.phuchoang.management.book.port;

import java.util.Optional;
import org.phuchoang.management.book.domain.Book;
import org.phuchoang.management.book.domain.Isbn;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.student.StudentId;

/**
 * Scoped to what US-2.1 (add), US-2.2 (assign owner), US-2.3 (unassign), US-2.4 (remove), and
 * US-5.2 (search/lookup) need.
 */
public interface BookRepository {

  boolean existsByIsbn(Isbn isbn);

  Optional<Book> findByIsbn(Isbn isbn);

  /**
   * UC-14 — matches isbn/title/author, optionally filtered by owner, keyset-paged (PM-045).
   * {@code query} may be blank/{@code null}. Typed in {@link StudentId}, not {@code StudentCode}:
   * {@code BookService} resolves the caller's code to the {@code books.owner_id} value this
   * matches on. {@code afterKey} is the decoded cursor (the last-seen {@code isbn}, or {@code
   * null} for the first page); {@code limit} is the page size.
   */
  CursorPage<Book> search(String query, StudentId ownerFilter, String afterKey, int limit);

  /** Backs {@code BookLookup.findByOwner} (US-5.4, {@code GET /api/v1/me/books}). */
  CursorPage<Book> findByOwnerId(StudentId ownerId, String afterKey, int limit);

  Book save(Book book);

  void deleteByIsbn(Isbn isbn);

  /** {@code BookService.onStudentDeleted} (06-low-level-design.md §13) — clears ownership on every book the deleted student owned. */
  void clearOwnerByStudentId(StudentId studentId);
}
