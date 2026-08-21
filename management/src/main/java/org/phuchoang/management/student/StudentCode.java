package org.phuchoang.management.student;

import org.phuchoang.management.shared.exception.DomainValidationException;

/**
 * Student.1 (format; uniqueness is checked via {@code StudentRepository.existsByCode}).
 *
 * <p>Placed at the module root, not {@code domain/}, for the same "published language" reason as
 * {@link StudentId} and {@code course.CourseCode}: it is the Student's business key, and it is the
 * <em>only</em> student identifier that crosses the HTTP boundary — {@code enrollment} and {@code
 * book} both key their APIs on it, and {@code web} DTOs carry it. {@code LayeringRulesTest} forbids
 * the Web layer from touching a Domain-layer type, so a {@code domain/} placement would have made
 * that impossible.
 */
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
