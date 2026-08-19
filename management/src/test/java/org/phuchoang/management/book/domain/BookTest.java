package org.phuchoang.management.book.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.student.StudentId;

class BookTest {

  private final Isbn isbn = new Isbn("978-0-13-468599-1");
  private final LocalDate publishedDate = LocalDate.of(2017, 9, 20);

  @Test
  void createsBookWithNoOwnerAndGeneratedTimestampsAndZeroVersion() {
    Book book = Book.create(isbn, "Clean Architecture", "Robert C. Martin", publishedDate, null);

    assertThat(book.id()).isNull();
    assertThat(book.isbn()).isEqualTo(isbn);
    assertThat(book.title()).isEqualTo("Clean Architecture");
    assertThat(book.author()).isEqualTo("Robert C. Martin");
    assertThat(book.publishedDate()).isEqualTo(publishedDate);
    assertThat(book.ownerId()).isNull();
    assertThat(book.createdAt()).isNotNull().isEqualTo(book.updatedAt());
    assertThat(book.version()).isZero();
  }

  @Test
  void createAcceptsAnOwner() {
    StudentId ownerId = new StudentId(1L);

    Book book = Book.create(isbn, "Clean Architecture", "Robert C. Martin", publishedDate, ownerId);

    assertThat(book.ownerId()).isEqualTo(ownerId);
  }

  @Test
  void createAcceptsNullPublishedDate() {
    Book book = Book.create(isbn, "Clean Architecture", "Robert C. Martin", null, null);

    assertThat(book.publishedDate()).isNull();
  }

  @Test
  void assignOwnerSetsOwnerAndBumpsUpdatedAt() {
    Book book = Book.create(isbn, "Clean Architecture", "Robert C. Martin", publishedDate, null);
    Instant createdAt = book.updatedAt();
    StudentId ownerId = new StudentId(1L);

    book.assignOwner(ownerId);

    assertThat(book.ownerId()).isEqualTo(ownerId);
    assertThat(book.updatedAt()).isAfterOrEqualTo(createdAt);
  }

  @Test
  void assignOwnerReplacesPriorOwner() {
    Book book = Book.create(isbn, "Clean Architecture", "Robert C. Martin", publishedDate, new StudentId(1L));

    book.assignOwner(new StudentId(2L));

    assertThat(book.ownerId()).isEqualTo(new StudentId(2L));
  }
}
