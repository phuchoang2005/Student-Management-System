package org.phuchoang.management.course.domain;

import org.phuchoang.management.shared.exception.DomainValidationException;

/** Course.3 — must be a positive number. */
public record Credits(int value) {

  public Credits {
    if (value <= 0) {
      throw new DomainValidationException("Credits must be a positive number");
    }
  }
}
