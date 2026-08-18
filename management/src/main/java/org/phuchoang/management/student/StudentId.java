package org.phuchoang.management.student;

/**
 * Canonical cross-module reference type (06-low-level-design.md §6): lives at {@code student}'s
 * module root, not {@code domain/}, so {@code book}/{@code enrollment}/{@code identity} can
 * depend on the type itself without an illegal domain-to-domain dependency across modules.
 * {@code null} before the owning {@link org.phuchoang.management.student.domain.Student} is first
 * saved; assigned by the database on insert.
 */
public record StudentId(Long value) {}
