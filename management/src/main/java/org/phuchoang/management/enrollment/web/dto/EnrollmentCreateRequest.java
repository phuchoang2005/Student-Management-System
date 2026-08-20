package org.phuchoang.management.enrollment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnrollmentCreateRequest(
    @NotNull Long studentId, @NotBlank @Size(max = 20) String courseCode) {}
