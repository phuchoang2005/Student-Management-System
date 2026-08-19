package org.phuchoang.management.enrollment.web.dto;

import java.time.Instant;

public record EnrollmentResponse(Long id, Long studentId, String courseCode, Instant enrolledAt) {}
