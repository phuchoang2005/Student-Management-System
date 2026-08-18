package org.phuchoang.management.course.domain;

import org.phuchoang.management.shared.exception.DomainValidationException;

/** Course.1 (format; uniqueness is checked via {@code CourseRepository.existsByCode}). */
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
