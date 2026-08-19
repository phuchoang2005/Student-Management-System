package org.phuchoang.management.course.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CourseCreateRequest(
    @NotBlank @Size(max = 20) String courseCode,
    @NotBlank @Size(max = 150) String name,
    String description,
    @NotNull @Min(1) Integer credits) {}
