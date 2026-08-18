package org.phuchoang.management.course.web.dto;

import java.time.Instant;
import java.util.List;

/** {@code roster} is always {@code []} until US-5.5 wires the real composition in (CourseService.getDetail's Javadoc). */
public record CourseDetailDto(
    Long id,
    String courseCode,
    String name,
    String description,
    int credits,
    Instant createdAt,
    Instant updatedAt,
    List<Object> roster) {}
