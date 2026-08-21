package org.phuchoang.management.student.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Flattens the OpenAPI {@code StudentRegistrationResponse} (=
 * {@code StudentResponse allOf {username, initialPassword}}) into one DTO — the only response in
 * the API that ever shows the plaintext initial password.
 */
public record StudentRegistrationResponse(
    String studentCode,
    String firstName,
    String lastName,
    String email,
    LocalDate dateOfBirth,
    Instant createdAt,
    Instant updatedAt,
    String username,
    String initialPassword) {}
