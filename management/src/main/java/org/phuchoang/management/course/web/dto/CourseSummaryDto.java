package org.phuchoang.management.course.web.dto;

/** No surrogate {@code id} — a course is named by {@code courseCode} everywhere the API is
 * reachable from (api-specification.md §5 decision #9). */
public record CourseSummaryDto(String courseCode, String name, int credits) {}
