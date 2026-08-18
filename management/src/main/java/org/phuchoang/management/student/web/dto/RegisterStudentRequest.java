package org.phuchoang.management.student.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RegisterStudentRequest(
    @NotBlank @Size(max = 20) String studentCode,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank @Size(max = 255) String email,
    @NotNull LocalDate dateOfBirth) {}
