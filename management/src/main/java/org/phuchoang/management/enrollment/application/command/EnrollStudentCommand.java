package org.phuchoang.management.enrollment.application.command;

/**
 * Both sides are business keys, never surrogate ids — {@code EnrollmentService} resolves {@code
 * studentCode} to a {@code StudentId} through {@code StudentLookup.idOf} before it touches the
 * {@code enrollments.student_id} FK (api-specification.md §5 decision #9).
 */
public record EnrollStudentCommand(String studentCode, String courseCode) {}
