package org.phuchoang.management.enrollment.internal;

import java.time.Instant;

/**
 * Projection for {@code SpringDataEnrollmentRepository.findByStudentId}'s join query — carries
 * {@code courseCode} alongside the plain {@link EnrollmentRow} columns since {@code
 * JdbcEnrollmentRepository} needs it to reconstitute a {@code CourseCode}-typed {@code Enrollment}
 * without a second round trip per row.
 */
record EnrollmentCourseRow(Long id, Long studentId, Long courseId, Instant enrolledAt, String courseCode) {}
