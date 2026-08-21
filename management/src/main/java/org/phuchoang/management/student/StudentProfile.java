package org.phuchoang.management.student;

import java.time.LocalDate;

/**
 * Read-model returned by {@link StudentLookup#profileOf(StudentId)} so {@code me} can render a
 * Student's own record without depending on {@code student}'s internal layers, mirroring {@link
 * StudentSummary} (06-low-level-design.md §4.8). Distinct from {@code StudentSummary} because
 * {@code GET /api/v1/me/profile} shows the full record — {@code dateOfBirth} included — where a
 * summary is only ever an embedded reference. Carries no surrogate id: no caller may key anything
 * on one (api-specification.md §5 decision #9).
 */
public record StudentProfile(
    String studentCode,
    String firstName,
    String lastName,
    String email,
    LocalDate dateOfBirth) {}
