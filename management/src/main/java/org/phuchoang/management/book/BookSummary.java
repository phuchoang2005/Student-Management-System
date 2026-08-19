package org.phuchoang.management.book;

/**
 * Read-model returned by {@link BookLookup#findByOwner(org.phuchoang.management.student.StudentId,
 * org.springframework.data.domain.Pageable)} so consuming modules (e.g. {@code me}) can embed a
 * book's summary fields without depending on {@code book}'s internal layers, mirroring {@code
 * course.CourseSummary}/{@code student.StudentSummary} (06-low-level-design.md §4.8). Placed at
 * the module root, not {@code domain/}, for the same "published language" reason as {@link
 * BookId}. {@code ownerId} is never {@code null} here — {@link BookLookup#findByOwner} is only
 * ever queried with a known owner.
 */
public record BookSummary(Long id, String isbn, String title, String author, Long ownerId) {}
