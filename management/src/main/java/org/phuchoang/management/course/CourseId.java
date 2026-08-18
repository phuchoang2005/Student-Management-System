package org.phuchoang.management.course;

/**
 * Canonical cross-module reference type (06-low-level-design.md §6), mirroring {@code
 * StudentId}: lives at {@code course}'s module root, not {@code domain/}, so other modules can
 * depend on the type itself without an illegal domain-to-domain dependency across modules. {@code
 * null} before the owning {@link org.phuchoang.management.course.domain.Course} is first saved;
 * assigned by the database on insert.
 */
public record CourseId(Long value) {}
