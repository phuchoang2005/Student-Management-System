package org.phuchoang.management.student.domain;

import java.time.LocalDate;
import org.phuchoang.management.shared.exception.DomainValidationException;

/** Student.4 — must be a real, non-future date within a plausible human age range. */
public record DateOfBirth(LocalDate value) {

  // No exact bound is fixed by req.md/06-low-level-design.md ("plausible human age range");
  // 150 years is a generous ceiling that only rejects clearly-bogus input.
  private static final int MAX_AGE_YEARS = 150;

  public DateOfBirth {
    if (value == null) {
      throw new DomainValidationException("Date of birth must not be null");
    }
    if (value.isAfter(LocalDate.now())) {
      throw new DomainValidationException("Date of birth must not be in the future");
    }
    if (value.isBefore(LocalDate.now().minusYears(MAX_AGE_YEARS))) {
      throw new DomainValidationException("Date of birth is not a plausible human age");
    }
  }
}
