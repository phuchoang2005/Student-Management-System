package org.phuchoang.management.book.domain;

import org.phuchoang.management.shared.exception.DomainValidationException;

/** Book.1 (format; uniqueness is checked via {@code BookRepository.existsByIsbn}). */
public record Isbn(String value) {

  private static final int MAX_LENGTH = 20;

  public Isbn {
    if (value == null || value.isBlank()) {
      throw new DomainValidationException("ISBN must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new DomainValidationException("ISBN must not exceed " + MAX_LENGTH + " characters");
    }
  }
}
