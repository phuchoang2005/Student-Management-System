package org.phuchoang.management.student;

/**
 * Read-model returned by {@link StudentLookup#summaryOf(StudentId)} so consuming modules (e.g.
 * {@code book}) can embed a student's summary fields without depending on {@code student}'s
 * internal layers (06-low-level-design.md §4.8). Placed at the module root, not {@code domain/},
 * for the same "published language" reason as {@link StudentId}/{@link StudentDeleted}. Carries no
 * surrogate id: the id is a database concern and never crosses the HTTP boundary
 * (api-specification.md §5 decision #9), so every consumer references the student by {@link
 * StudentCode} instead.
 */
public record StudentSummary(String studentCode, String firstName, String lastName, String email) {}
