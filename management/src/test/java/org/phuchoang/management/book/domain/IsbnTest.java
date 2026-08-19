package org.phuchoang.management.book.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.DomainValidationException;

class IsbnTest {

  @Test
  void acceptsValidIsbn() {
    assertThat(new Isbn("978-0-13-468599-1").value()).isEqualTo("978-0-13-468599-1");
  }

  @Test
  void rejectsBlankIsbn() {
    assertThatThrownBy(() -> new Isbn(" ")).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void rejectsNullIsbn() {
    assertThatThrownBy(() -> new Isbn(null)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void acceptsIsbnAtTwentyCharBoundary() {
    String isbn = "1".repeat(20);
    assertThat(new Isbn(isbn).value()).hasSize(20);
  }

  @Test
  void rejectsIsbnExceedingTwentyChars() {
    String isbn = "1".repeat(21);
    assertThatThrownBy(() -> new Isbn(isbn)).isInstanceOf(DomainValidationException.class);
  }
}
