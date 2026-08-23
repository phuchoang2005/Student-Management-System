package org.phuchoang.management.course.web.dto;

/**
 * No surrogate {@code id} — a course is named by {@code courseCode} everywhere the API is reachable
 * from (api-specification.md §5 decision #9).
 *
 * <p>{@code enrolledCount} is the one aggregate embedded in a summary rather than left to its own
 * endpoint (api-specification.md §5 decision #11). It is a count, not the roster: the roster stays a
 * separately authorized read, and a number reveals no student's identity to a Student browsing the
 * catalogue. Read outside any enrolling transaction, so it is a snapshot, not a capacity guarantee.
 */
public record CourseSummaryDto(String courseCode, String name, int credits, long enrolledCount) {}
