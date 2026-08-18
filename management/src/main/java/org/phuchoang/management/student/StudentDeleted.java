package org.phuchoang.management.student;

/**
 * Published by {@code StudentService.remove(...)} after the student row is deleted, so {@code
 * book}/{@code enrollment}/{@code identity} can react without a direct dependency on {@code
 * student}'s internals (06-low-level-design.md §2.2, §13). Placed at the module root, not {@code
 * domain/}, so consuming modules can have this type on their classpath without reaching into
 * {@code internal/} — the same "published language" exemption {@link StudentId} gets.
 */
public record StudentDeleted(StudentId studentId) {}
