package org.phuchoang.management.book.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.book.BookId;
import org.phuchoang.management.book.application.command.AddBookCommand;
import org.phuchoang.management.book.application.command.AssignBookOwnerCommand;
import org.phuchoang.management.book.domain.Book;
import org.phuchoang.management.book.domain.Isbn;
import org.phuchoang.management.book.port.BookRepository;
import org.phuchoang.management.shared.exception.DuplicateIsbnException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.exception.UnknownStudentException;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;
import org.phuchoang.management.student.StudentSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

  private static Book anUnownedBook() {
    return Book.reconstitute(
        new BookId(1L),
        new Isbn("978-0-13-468599-1"),
        "Clean Architecture",
        "Robert C. Martin",
        LocalDate.of(2017, 9, 20),
        null,
        Instant.now(),
        Instant.now(),
        0L);
  }

  private static Book anOwnedBook() {
    return Book.reconstitute(
        new BookId(1L),
        new Isbn("978-0-13-468599-1"),
        "Clean Architecture",
        "Robert C. Martin",
        LocalDate.of(2017, 9, 20),
        new StudentId(1L),
        Instant.now(),
        Instant.now(),
        0L);
  }

  @Test
  void assignOwnerRejectsUnknownBook() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.assignOwner("978-0-13-468599-1", new AssignBookOwnerCommand(1L)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void assignOwnerRejectsUnknownStudent() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anUnownedBook()));
    when(studentLookup.existsById(new StudentId(99L))).thenReturn(false);

    assertThatThrownBy(
            () -> service.assignOwner("978-0-13-468599-1", new AssignBookOwnerCommand(99L)))
        .isInstanceOf(UnknownStudentException.class);
  }

  @Test
  void assignOwnerSetsOwnerAndReplacesAnyPriorOwner() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anUnownedBook()));
    when(studentLookup.existsById(new StudentId(1L))).thenReturn(true);
    when(repository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

    BookService.AssignedBook result =
        service.assignOwner("978-0-13-468599-1", new AssignBookOwnerCommand(1L));

    assertThat(result.ownerId()).isEqualTo(1L);
  }

  @Test
  void unassignOwnerRejectsUnknownBook() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.unassignOwner("978-0-13-468599-1"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void unassignOwnerClearsOwner() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anOwnedBook()));
    when(repository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

    BookService.UnassignedBook result = service.unassignOwner("978-0-13-468599-1");

    assertThat(result.isbn()).isEqualTo("978-0-13-468599-1");
  }

  @Test
  void unassignOwnerOnAnAlreadyUnownedBookIsIdempotent() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anUnownedBook()));
    when(repository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

    BookService.UnassignedBook result = service.unassignOwner("978-0-13-468599-1");

    assertThat(result.isbn()).isEqualTo("978-0-13-468599-1");
  }

  @Test
  void removeRejectsUnknownBook() {
    service = new BookService(repository, studentLookup);
    when(repository.existsByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(false);

    assertThatThrownBy(() -> service.remove("978-0-13-468599-1"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void removeDeletesAnExistingBook() {
    service = new BookService(repository, studentLookup);
    when(repository.existsByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(true);

    service.remove("978-0-13-468599-1");

    verify(repository).deleteByIsbn(new Isbn("978-0-13-468599-1"));
  }

  @Test
  void searchReturnsMappedSummariesFromRepositoryPage() {
    service = new BookService(repository, studentLookup);
    Pageable pageable = PageRequest.of(0, 20);
    Page<Book> repoPage = new PageImpl<>(List.of(anOwnedBook()), pageable, 1);
    when(repository.search("clean", null, pageable)).thenReturn(repoPage);

    Page<BookService.BookSummaryView> result = service.search("clean", null, pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    BookService.BookSummaryView summary = result.getContent().get(0);
    assertThat(summary.id()).isEqualTo(1L);
    assertThat(summary.isbn()).isEqualTo("978-0-13-468599-1");
    assertThat(summary.title()).isEqualTo("Clean Architecture");
    assertThat(summary.ownerId()).isEqualTo(1L);
  }

  @Test
  void searchAppliesOwnerFilter() {
    service = new BookService(repository, studentLookup);
    Pageable pageable = PageRequest.of(0, 20);
    when(repository.search(null, new StudentId(1L), pageable)).thenReturn(Page.empty(pageable));

    service.search(null, 1L, pageable);

    verify(repository).search(null, new StudentId(1L), pageable);
  }

  @Test
  void searchReturnsEmptyPageWhenNothingMatches() {
    service = new BookService(repository, studentLookup);
    Pageable pageable = PageRequest.of(0, 20);
    when(repository.search("nobody", null, pageable)).thenReturn(Page.empty(pageable));

    Page<BookService.BookSummaryView> result = service.search("nobody", null, pageable);

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  void getDetailThrowsNotFoundWhenBookDoesNotExist() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail("978-0-13-468599-1"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void getDetailReturnsBookFieldsWithNullOwnerWhenUnowned() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anUnownedBook()));

    BookService.BookDetailView detail = service.getDetail("978-0-13-468599-1");

    assertThat(detail.isbn()).isEqualTo("978-0-13-468599-1");
    assertThat(detail.ownerId()).isNull();
    assertThat(detail.owner()).isNull();
  }

  @Test
  void getDetailEmbedsOwnerSummaryWhenOwned() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anOwnedBook()));
    StudentSummary summary = new StudentSummary(1L, "S00101", "Amy", "Lee", "amy.lee@example.edu");
    when(studentLookup.summaryOf(new StudentId(1L))).thenReturn(summary);

    BookService.BookDetailView detail = service.getDetail("978-0-13-468599-1");

    assertThat(detail.ownerId()).isEqualTo(1L);
    assertThat(detail.owner()).isEqualTo(summary);
  }

  @Test
  void findByOwnerDelegatesToRepository() {
    service = new BookService(repository, studentLookup);
    when(repository.findByOwnerId(new StudentId(1L))).thenReturn(List.of(anOwnedBook()));

    List<Book> result = service.findByOwner(new StudentId(1L));

    assertThat(result).hasSize(1);
    verify(repository).findByOwnerId(new StudentId(1L));
  }
}
