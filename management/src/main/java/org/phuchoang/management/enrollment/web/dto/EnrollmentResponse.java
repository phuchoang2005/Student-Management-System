package org.phuchoang.management.enrollment.web.dto;

import java.time.Instant;

/**
 * Keyed by the {@code studentCode}/{@code courseCode} pair the caller supplied, with no surrogate
 * {@code id}: the pair is what {@code GET}/{@code DELETE /api/v1/enrollments/{studentCode}/{courseCode}}
 * address, so an enrollment id would be a value no endpoint accepts.
 */
public record EnrollmentResponse(String studentCode, String courseCode, Instant enrolledAt) {}
