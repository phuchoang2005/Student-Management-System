package org.phuchoang.management.course;

import org.phuchoang.management.shared.exception.DomainValidationException;

/**
 * Course.1 (format; uniqueness is checked via {@code CourseRepository.existsByCode}). Lives at
 * {@code course}'s module root, not {@code domain/}, mirroring {@code CourseId}/{@code
 * StudentId}: {@code enrollment} reuses this type directly to reference a course
 * (06-low-level-design.md §7 — "studentId/courseCode reuse student.StudentId/course.CourseCode
 * directly"), which requires it to sit in the module's published-API package.
 */
public record CourseCode(String value) {

  private static final int MAX_LENGTH = 20;

  public CourseCode {
    if (value == null || value.isBlank()) {
      throw new DomainValidationException("Course code must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new DomainValidationException("Course code must not exceed " + MAX_LENGTH + " characters");
    }
  }
}
