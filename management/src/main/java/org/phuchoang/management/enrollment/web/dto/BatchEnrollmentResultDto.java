package org.phuchoang.management.enrollment.web.dto;

import java.time.Instant;

/**
 * What happened to one requested course. {@code status} is {@code ENROLLED}, {@code UNKNOWN_COURSE},
 * {@code ALREADY_ENROLLED} or {@code INVALID_COURSE_CODE}; {@code enrolledAt} is set only on the
 * first, {@code message} only on the others.
 */
public record BatchEnrollmentResultDto(
    String courseCode, String status, Instant enrolledAt, String message) {}
