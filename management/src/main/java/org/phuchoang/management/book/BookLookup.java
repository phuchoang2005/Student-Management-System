package org.phuchoang.management.book;

import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.student.StudentId;

/**
 * Public read-only API other modules use to reference a Student's owned books without depending
 * on {@code book}'s internal layers, mirroring {@code course.CourseLookup}/{@code
 * student.StudentLookup}. {@code me} — this interface's only consumer so far — needs a
 * Student-scoped, keyset-paginated view of ownership for {@code GET /api/v1/me/books} (US-5.4).
 */
public interface BookLookup {

  /**
   * Consumed by {@code MeController} (US-5.4) to serve the caller's own owned books, one
   * keyset-paginated page at a time (PM-045). {@code cursor} is the opaque value from a prior
   * page's {@code nextCursor}, or {@code null} for the first page; {@code size} is the page size.
   */
  CursorPage<BookSummary> findByOwner(StudentId ownerId, String cursor, int size);
}
