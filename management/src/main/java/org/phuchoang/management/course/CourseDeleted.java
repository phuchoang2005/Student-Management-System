package org.phuchoang.management.course;

/**
 * Published by {@code CourseService.remove(...)} after the course row is deleted, so {@code
 * enrollment} can react without a direct dependency on {@code course}'s internals
 * (06-low-level-design.md §2.2, §13). Carries {@code CourseCode}, not {@code CourseId}, per
 * §13's listener contract: {@code EnrollmentService.onCourseDeleted} deletes by course code, not
 * by the surrogate id.
 */
public record CourseDeleted(CourseCode courseCode) {}
