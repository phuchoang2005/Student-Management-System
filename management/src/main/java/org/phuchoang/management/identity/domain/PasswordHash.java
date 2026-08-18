package org.phuchoang.management.identity.domain;

import org.phuchoang.management.shared.exception.DomainValidationException;

/** Wraps a BCrypt digest; the plaintext policy itself is checked before hashing. */
public record PasswordHash(String value) {

  public PasswordHash {
    if (value == null || value.isBlank()) {
      throw new DomainValidationException("Password hash must not be blank");
    }
  }
}
