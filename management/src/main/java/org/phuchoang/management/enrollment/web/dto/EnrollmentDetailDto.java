package org.phuchoang.management.enrollment.web.dto;

import java.time.Instant;

/** No {@code id} field, per the {@code EnrollmentDetail} OpenAPI schema — keyed by the student/course pair instead. */
public record EnrollmentDetailDto(EnrollmentStudentDto student, EnrollmentCourseDto course, Instant enrolledAt) {}
