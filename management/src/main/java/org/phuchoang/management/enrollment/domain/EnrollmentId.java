package org.phuchoang.management.enrollment.domain;

/**
 * Mirrors {@code CourseId}/{@code StudentId}'s nullable-before-save pattern, but stays in {@code
 * domain/} rather than the module root: unlike those, no other module ever needs to reference an
 * {@code Enrollment} by its surrogate id (06-low-level-design.md §7 — {@code enrollment} is a
 * terminal module nothing else depends on for Sprint 3).
 */
public record EnrollmentId(Long value) {}
