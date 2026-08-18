package org.phuchoang.management.student.domain;

import org.phuchoang.management.shared.exception.DomainValidationException;

/** Student.1 (format; uniqueness is checked via {@code StudentRepository.existsByCode}). */
public record StudentCode(String value) {

  private static final int MAX_LENGTH = 20;

  public StudentCode {
    if (value == null || value.isBlank()) {
      throw new DomainValidationException("Student code must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new DomainValidationException("Student code must not exceed " + MAX_LENGTH + " characters");
    }
  }
}
