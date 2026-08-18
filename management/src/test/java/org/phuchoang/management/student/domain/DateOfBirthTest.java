package org.phuchoang.management.student.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.DomainValidationException;

class DateOfBirthTest {

  @Test
  void acceptsPlausibleDate() {
    LocalDate dob = LocalDate.of(2000, 1, 1);
    assertThat(new DateOfBirth(dob).value()).isEqualTo(dob);
  }

  @Test
  void rejectsNullDate() {
    assertThatThrownBy(() -> new DateOfBirth(null)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void rejectsFutureDate() {
    assertThatThrownBy(() -> new DateOfBirth(LocalDate.now().plusDays(1)))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void rejectsImplausiblyOldDate() {
    assertThatThrownBy(() -> new DateOfBirth(LocalDate.now().minusYears(151)))
        .isInstanceOf(DomainValidationException.class);
  }
}
