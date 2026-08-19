package org.phuchoang.management.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.DomainValidationException;

class CourseCodeTest {

  @Test
  void acceptsValidCode() {
    assertThat(new CourseCode("CS101").value()).isEqualTo("CS101");
  }

  @Test
  void rejectsBlankCode() {
    assertThatThrownBy(() -> new CourseCode(" ")).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void rejectsNullCode() {
    assertThatThrownBy(() -> new CourseCode(null)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void acceptsCodeAtTwentyCharBoundary() {
    String code = "a".repeat(20);
    assertThat(new CourseCode(code).value()).hasSize(20);
  }

  @Test
  void rejectsCodeExceedingTwentyChars() {
    String code = "a".repeat(21);
    assertThatThrownBy(() -> new CourseCode(code)).isInstanceOf(DomainValidationException.class);
  }
}
