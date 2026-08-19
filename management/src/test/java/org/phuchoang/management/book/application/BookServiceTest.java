package org.phuchoang.management.book.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.book.BookId;
import org.phuchoang.management.book.application.command.AddBookCommand;
import org.phuchoang.management.book.domain.Book;
import org.phuchoang.management.book.domain.Isbn;
import org.phuchoang.management.book.port.BookRepository;
import org.phuchoang.management.shared.exception.DuplicateIsbnException;
import org.phuchoang.management.shared.exception.UnknownStudentException;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

  @Mock private BookRepository repository;
  @Mock private StudentLookup studentLookup;

  private BookService service;

  private final AddBookCommand commandWithoutOwner =
      new AddBookCommand("978-0-13-468599-1", "Clean Architecture", "Robert C. Martin",
          LocalDate.of(2017, 9, 20), null);

  @Test
  void addBookRejectsDuplicateIsbn() {
    service = new BookService(repository, studentLookup);
    when(repository.existsByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(true);

    assertThatThrownBy(() -> service.addBook(commandWithoutOwner))
        .isInstanceOf(DuplicateIsbnException.class);
  }

  @Test
  void addBookRejectsUnknownOwner() {
    service = new BookService(repository, studentLookup);
    when(repository.existsByIsbn(any())).thenReturn(false);
    when(studentLookup.existsById(new StudentId(99L))).thenReturn(false);
    AddBookCommand command =
        new AddBookCommand("978-0-13-468599-1", "Clean Architecture", "Robert C. Martin",
            LocalDate.of(2017, 9, 20), 99L);

    assertThatThrownBy(() -> service.addBook(command)).isInstanceOf(UnknownStudentException.class);
  }

  @Test
  void addBookSavesBookWithoutOwnerAndReturnsView() {
    service = new BookService(repository, studentLookup);
    when(repository.existsByIsbn(any())).thenReturn(false);
    when(repository.save(any(Book.class)))
        .thenAnswer(
            invocation -> {
              Book toSave = invocation.getArgument(0);
              return Book.reconstitute(
                  new BookId(1L),
                  toSave.isbn(),
                  toSave.title(),
                  toSave.author(),
                  toSave.publishedDate(),
                  toSave.ownerId(),
                  toSave.createdAt(),
                  toSave.updatedAt(),
                  toSave.version());
            });

    BookService.AddedBook result = service.addBook(commandWithoutOwner);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.isbn()).isEqualTo("978-0-13-468599-1");
    assertThat(result.title()).isEqualTo("Clean Architecture");
    assertThat(result.author()).isEqualTo("Robert C. Martin");
    assertThat(result.ownerId()).isNull();
  }

  @Test
  void addBookSavesBookWithValidOwner() {
    service = new BookService(repository, studentLookup);
    when(repository.existsByIsbn(any())).thenReturn(false);
    when(studentLookup.existsById(new StudentId(1L))).thenReturn(true);
    when(repository.save(any(Book.class)))
        .thenAnswer(
            invocation -> {
              Book toSave = invocation.getArgument(0);
              return Book.reconstitute(
                  new BookId(1L),
                  toSave.isbn(),
                  toSave.title(),
                  toSave.author(),
                  toSave.publishedDate(),
                  toSave.ownerId(),
                  toSave.createdAt(),
                  toSave.updatedAt(),
                  toSave.version());
            });
    AddBookCommand command =
        new AddBookCommand("978-0-13-468599-1", "Clean Architecture", "Robert C. Martin",
            LocalDate.of(2017, 9, 20), 1L);

    BookService.AddedBook result = service.addBook(command);

    assertThat(result.ownerId()).isEqualTo(1L);
  }
}
