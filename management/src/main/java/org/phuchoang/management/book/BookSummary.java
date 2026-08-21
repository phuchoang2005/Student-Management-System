package org.phuchoang.management.book;

/**
 * Read-model returned by {@link BookLookup#findByOwner(org.phuchoang.management.student.StudentId,
 * org.springframework.data.domain.Pageable)} so consuming modules (e.g. {@code me}) can embed a
 * book's summary fields without depending on {@code book}'s internal layers, mirroring {@code
 * course.CourseSummary}/{@code student.StudentSummary} (06-low-level-design.md §4.8). Placed at the
 * module root, not {@code domain/}, for the same "published language" reason as {@link BookId}.
 *
 * <p>Carries neither a surrogate id nor an owner: ids never cross the HTTP boundary
 * (api-specification.md §5 decision #9), and {@link BookLookup#findByOwner} is only ever queried
 * with one known owner, so naming that owner on every row would repeat what the caller already
 * supplied.
 */
public record BookSummary(String isbn, String title, String author) {}
