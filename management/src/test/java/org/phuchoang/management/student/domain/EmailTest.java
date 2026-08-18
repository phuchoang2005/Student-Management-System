package org.phuchoang.management.student.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.InvalidEmailException;

class EmailTest {

  @Test
  void acceptsWellFormedEmail() {
    assertThat(new Email("jane.doe@example.edu").value()).isEqualTo("jane.doe@example.edu");
  }

  @Test
  void rejectsMalformedEmail() {
    assertThatThrownBy(() -> new Email("not-an-email")).isInstanceOf(InvalidEmailException.class);
  }

  @Test
  void rejectsBlankEmail() {
    assertThatThrownBy(() -> new Email("")).isInstanceOf(InvalidEmailException.class);
  }

  @Test
  void rejectsNullEmail() {
    assertThatThrownBy(() -> new Email(null)).isInstanceOf(InvalidEmailException.class);
  }
}
