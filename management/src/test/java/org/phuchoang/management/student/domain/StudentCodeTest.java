package org.phuchoang.management.student.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.DomainValidationException;

class StudentCodeTest {

  @Test
  void acceptsValidCode() {
    assertThat(new StudentCode("S00123").value()).isEqualTo("S00123");
  }

  @Test
  void rejectsBlankCode() {
    assertThatThrownBy(() -> new StudentCode(" ")).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void rejectsNullCode() {
    assertThatThrownBy(() -> new StudentCode(null)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void acceptsCodeAtTwentyCharBoundary() {
    String code = "a".repeat(20);
    assertThat(new StudentCode(code).value()).hasSize(20);
  }

  @Test
  void rejectsCodeExceedingTwentyChars() {
    String code = "a".repeat(21);
    assertThatThrownBy(() -> new StudentCode(code)).isInstanceOf(DomainValidationException.class);
  }
}
