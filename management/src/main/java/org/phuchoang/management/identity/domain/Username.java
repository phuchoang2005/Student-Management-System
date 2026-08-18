package org.phuchoang.management.identity.domain;

import org.phuchoang.management.shared.exception.DomainValidationException;

/** Identity.2 — unique across all accounts; for a Student account, always their email. */
public record Username(String value) {

  public Username {
    if (value == null || value.isBlank()) {
      throw new DomainValidationException("Username must not be blank");
    }
  }
}
