package org.phuchoang.management.enrollment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnrollmentCreateRequest(
    @NotBlank @Size(max = 20) String studentCode, @NotBlank @Size(max = 20) String courseCode) {}
