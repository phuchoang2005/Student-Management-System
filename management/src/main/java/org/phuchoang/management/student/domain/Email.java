package org.phuchoang.management.student.domain;

import java.util.regex.Pattern;
import org.phuchoang.management.shared.exception.InvalidEmailException;

/**
 * Student.2 — format only; duplicate-email uniqueness is checked via {@code
 * StudentRepository.existsByEmail}, which raises {@code DuplicateEmailException} instead.
 */
public record Email(String value) {

  private static final int MAX_LENGTH = 255;
  private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  public Email {
    if (value == null || value.isBlank() || value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches()) {
      throw new InvalidEmailException("Email '" + value + "' is not a valid email address");
    }
  }
}
