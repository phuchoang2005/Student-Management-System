package org.phuchoang.management.book;

/**
 * Canonical cross-module reference type (06-low-level-design.md §6), mirroring {@code
 * CourseId}/{@code StudentId}: lives at {@code book}'s module root, not {@code domain/}, so other
 * modules can depend on the type itself without an illegal domain-to-domain dependency across
 * modules. {@code null} before the owning {@link org.phuchoang.management.book.domain.Book} is
 * first saved; assigned by the database on insert.
 */
public record BookId(Long value) {}
