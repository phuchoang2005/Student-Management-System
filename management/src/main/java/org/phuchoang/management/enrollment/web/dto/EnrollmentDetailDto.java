package org.phuchoang.management.enrollment.web.dto;

import java.time.Instant;

/**
 * One enrollment, both sides resolved. Keyed by the student/course pair rather than a surrogate id,
 * per the {@code EnrollmentDetail} OpenAPI schema.
 *
 * <p>Doubles as the row type of {@code GET /api/v1/enrollments?studentCode=|courseCode=}: filtering
 * by either side yields the same rows viewed from a different end, so one schema serves both rather
 * than two endpoints with divergent shapes. The redundant side is constant across a page — every
 * row shares a {@code student} when filtering by {@code studentCode}, a {@code course} when
 * filtering by {@code courseCode} — which is what lets one client render "this student's courses"
 * and another "this course's roster" off the same response.
 */
public record EnrollmentDetailDto(EnrollmentStudentDto student, EnrollmentCourseDto course, Instant enrolledAt) {}
