package org.phuchoang.management.me.web.dto;

import java.time.LocalDate;

/**
 * The caller's own student record. Matches the {@code StudentDetail} OpenAPI schema minus the audit
 * timestamps — a student looking at their own profile has no use for {@code createdAt}/{@code
 * updatedAt}, which exist for the Registrar's record-keeping.
 */
public record MeProfileDto(
    String studentCode, String firstName, String lastName, String email, LocalDate dateOfBirth) {}
