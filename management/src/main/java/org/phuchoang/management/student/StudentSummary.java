package org.phuchoang.management.student;

/**
 * Read-model returned by {@link StudentLookup#summaryOf(StudentId)} so consuming modules (e.g.
 * {@code book}) can embed a student's summary fields without depending on {@code student}'s
 * internal layers (06-low-level-design.md §4.8). Placed at the module root, not {@code domain/},
 * for the same "published language" reason as {@link StudentId}/{@link StudentDeleted}.
 */
public record StudentSummary(Long id, String studentCode, String firstName, String lastName, String email) {}
