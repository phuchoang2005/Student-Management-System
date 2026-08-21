package org.phuchoang.management.book;

import org.phuchoang.management.student.StudentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public read-only API other modules use to reference a Student's owned books without depending
 * on {@code book}'s internal layers, mirroring {@code course.CourseLookup}/{@code
 * student.StudentLookup}. {@code me} — this interface's only consumer so far — needs a
 * Student-scoped, paginated view of ownership for {@code GET /api/v1/me/books} (US-5.4).
 */
public interface BookLookup {

  /** Consumed by {@code MeController} (US-5.4) to serve the caller's own owned books, one page at a time. */
  Page<BookSummary> findByOwner(StudentId ownerId, Pageable pageable);
}
