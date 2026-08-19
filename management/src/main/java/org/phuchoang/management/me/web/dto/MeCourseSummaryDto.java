package org.phuchoang.management.me.web.dto;

/** Matches the OpenAPI {@code CourseSummary} schema. */
public record MeCourseSummaryDto(Long id, String courseCode, String name, int credits) {}
