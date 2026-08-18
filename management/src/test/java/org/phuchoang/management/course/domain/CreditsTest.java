package org.phuchoang.management.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.DomainValidationException;

class CreditsTest {

  @Test
  void acceptsPositiveValue() {
    assertThat(new Credits(3).value()).isEqualTo(3);
  }

  @Test
  void acceptsMinimumBoundaryOfOne() {
    assertThat(new Credits(1).value()).isEqualTo(1);
  }

  @Test
  void rejectsZero() {
    assertThatThrownBy(() -> new Credits(0)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void rejectsNegativeValue() {
    assertThatThrownBy(() -> new Credits(-1)).isInstanceOf(DomainValidationException.class);
  }
}
