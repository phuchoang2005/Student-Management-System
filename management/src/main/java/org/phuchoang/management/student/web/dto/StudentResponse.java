package org.phuchoang.management.student.web.dto;

import java.time.Instant;
import java.time.LocalDate;

public record StudentResponse(
    Long id,
    String studentCode,
    String firstName,
    String lastName,
    String email,
    LocalDate dateOfBirth,
    Instant createdAt,
    Instant updatedAt) {}
