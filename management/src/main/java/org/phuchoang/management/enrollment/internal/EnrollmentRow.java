package org.phuchoang.management.enrollment.internal;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Stores {@code courseId}, not {@code courseCode} — {@code enrollments.course_id} is the DB's
 * surrogate FK, while {@code Enrollment.courseCode} is what the aggregate and repository port are
 * typed in. {@code JdbcEnrollmentRepository} resolves the difference with SQL joins against
 * {@code courses} (06-low-level-design.md §9.1). No {@code @Version} — Enrollment carries no
 * update use case (§7), so optimistic locking doesn't apply.
 */
@Table("enrollments")
record EnrollmentRow(@Id Long id, Long studentId, Long courseId, Instant enrolledAt) {}
