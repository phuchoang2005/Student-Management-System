package org.phuchoang.management.course.web.dto;

import java.time.Instant;

public record CourseResponse(
    Long id,
    String courseCode,
    String name,
    String description,
    int credits,
    Instant createdAt,
    Instant updatedAt) {}
