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
import org.phuchoang.management.shared.paging.CursorCodec;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.student.StudentCode;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;
import org.phuchoang.management.student.StudentSummary;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

  @Mock private BookRepository repository;
  @Mock private StudentLookup studentLookup;

  private BookService service;

  private static final StudentCode OWNER_CODE = new StudentCode("S00101");
  private static final StudentId OWNER_ID = new StudentId(1L);

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
    when(studentLookup.idOf(new StudentCode("S00999"))).thenReturn(Optional.empty());
    AddBookCommand command =
        new AddBookCommand("978-0-13-468599-1", "Clean Architecture", "Robert C. Martin",
            LocalDate.of(2017, 9, 20), "S00999");

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

    assertThat(result.isbn()).isEqualTo("978-0-13-468599-1");
    assertThat(result.title()).isEqualTo("Clean Architecture");
    assertThat(result.author()).isEqualTo("Robert C. Martin");
    assertThat(result.ownerStudentCode()).isNull();
  }

  @Test
  void addBookSavesBookWithValidOwner() {
    service = new BookService(repository, studentLookup);
    when(repository.existsByIsbn(any())).thenReturn(false);
    when(studentLookup.idOf(OWNER_CODE)).thenReturn(Optional.of(OWNER_ID));
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
            LocalDate.of(2017, 9, 20), "S00101");

    BookService.AddedBook result = service.addBook(command);

    assertThat(result.ownerStudentCode()).isEqualTo("S00101");
    verify(repository)
        .save(org.mockito.ArgumentMatchers.argThat(book -> OWNER_ID.equals(book.ownerId())));
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
        OWNER_ID,
        Instant.now(),
        Instant.now(),
        0L);
  }

  @Test
  void assignOwnerRejectsUnknownBook() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.assignOwner("978-0-13-468599-1", new AssignBookOwnerCommand("S00101")))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void assignOwnerRejectsUnknownStudent() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anUnownedBook()));
    when(studentLookup.idOf(new StudentCode("S00999"))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.assignOwner("978-0-13-468599-1", new AssignBookOwnerCommand("S00999")))
        .isInstanceOf(UnknownStudentException.class);
  }

  @Test
  void assignOwnerSetsOwnerAndReplacesAnyPriorOwner() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anUnownedBook()));
    when(studentLookup.idOf(OWNER_CODE)).thenReturn(Optional.of(OWNER_ID));
    when(repository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

    BookService.AssignedBook result =
        service.assignOwner("978-0-13-468599-1", new AssignBookOwnerCommand("S00101"));

    assertThat(result.ownerStudentCode()).isEqualTo("S00101");
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
    CursorPage<Book> repoPage = new CursorPage<>(List.of(anOwnedBook(), anOwnedBook()), null);
    when(repository.search("clean", null, null, 20)).thenReturn(repoPage);
    when(studentLookup.summaryOf(OWNER_ID))
        .thenReturn(new StudentSummary("S00101", "Amy", "Lee", "amy.lee@example.edu"));

    CursorPage<BookService.BookSummaryView> result = service.search("clean", null, null, 20, null);

    assertThat(result.content()).hasSize(2);
    BookService.BookSummaryView summary = result.content().get(0);
    assertThat(summary.isbn()).isEqualTo("978-0-13-468599-1");
    assertThat(summary.title()).isEqualTo("Clean Architecture");
    assertThat(summary.ownerStudentCode()).isEqualTo("S00101");
    assertThat(result.nextCursor()).isNull();
    // Two rows sharing an owner cost one lookup, not two.
    verify(studentLookup).summaryOf(OWNER_ID);
  }

  @Test
  void searchResolvesTheOwnerStudentCodeToTheIdItFiltersOn() {
    service = new BookService(repository, studentLookup);
    when(studentLookup.idOf(OWNER_CODE)).thenReturn(Optional.of(OWNER_ID));
    when(repository.search(null, OWNER_ID, null, 20)).thenReturn(new CursorPage<>(List.of(), null));

    service.search(null, "S00101", null, 20, null);

    verify(repository).search(null, OWNER_ID, null, 20);
  }

  @Test
  void searchRejectsAnUnknownOwnerStudentCode() {
    service = new BookService(repository, studentLookup);
    when(studentLookup.idOf(new StudentCode("S00999"))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.search(null, "S00999", null, 20, null))
        .isInstanceOf(UnknownStudentException.class);
  }

  @Test
  void searchReturnsEmptyPageWhenNothingMatches() {
    service = new BookService(repository, studentLookup);
    when(repository.search("nobody", null, null, 20)).thenReturn(new CursorPage<>(List.of(), null));

    CursorPage<BookService.BookSummaryView> result = service.search("nobody", null, null, 20, null);

    assertThat(result.content()).isEmpty();
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void searchDecodesTheCursorBeforePassingItToTheRepositoryAsTheAfterKey() {
    service = new BookService(repository, studentLookup);
    String cursor = CursorCodec.encode("978-0-13-468599-1");
    when(repository.search(null, null, "978-0-13-468599-1", 20))
        .thenReturn(new CursorPage<>(List.of(), null));

    service.search(null, null, cursor, 20, null);

    verify(repository).search(null, null, "978-0-13-468599-1", 20);
  }

  @Test
  void searchOverridesTheOwnerFilterWithTheCallerStudentIdWhenGiven() {
    service = new BookService(repository, studentLookup);
    when(repository.search(null, new StudentId(9L), null, 20)).thenReturn(new CursorPage<>(List.of(), null));

    // Client asked for S00101's books, but the caller is Student 9 -- the caller wins, silently,
    // and the supplied code is never even resolved.
    service.search(null, "S00101", null, 20, 9L);

    verify(repository).search(null, new StudentId(9L), null, 20);
    verify(studentLookup, org.mockito.Mockito.never()).idOf(any());
  }

  @Test
  void getDetailThrowsNotFoundWhenBookDoesNotExist() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail("978-0-13-468599-1", null))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void getDetailReturnsBookFieldsWithNullOwnerWhenUnowned() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anUnownedBook()));

    BookService.BookDetailView detail = service.getDetail("978-0-13-468599-1", null);

    assertThat(detail.isbn()).isEqualTo("978-0-13-468599-1");
    assertThat(detail.ownerStudentCode()).isNull();
    assertThat(detail.owner()).isNull();
  }

  @Test
  void getDetailEmbedsOwnerSummaryWhenOwned() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anOwnedBook()));
    StudentSummary summary = new StudentSummary("S00101", "Amy", "Lee", "amy.lee@example.edu");
    when(studentLookup.summaryOf(OWNER_ID)).thenReturn(summary);

    BookService.BookDetailView detail = service.getDetail("978-0-13-468599-1", null);

    assertThat(detail.ownerStudentCode()).isEqualTo("S00101");
    assertThat(detail.owner()).isEqualTo(summary);
  }

  @Test
  void getDetailAllowsTheOwningStudentToReadTheirOwnBook() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anOwnedBook()));
    StudentSummary summary = new StudentSummary("S00101", "Amy", "Lee", "amy.lee@example.edu");
    when(studentLookup.summaryOf(OWNER_ID)).thenReturn(summary);

    BookService.BookDetailView detail = service.getDetail("978-0-13-468599-1", 1L);

    assertThat(detail.ownerStudentCode()).isEqualTo("S00101");
  }

  @Test
  void getDetailForbidsAStudentFromReadingABookOwnedBySomeoneElse() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anOwnedBook()));

    assertThatThrownBy(() -> service.getDetail("978-0-13-468599-1", 2L))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void getDetailForbidsAStudentFromReadingAnUnownedBook() {
    service = new BookService(repository, studentLookup);
    when(repository.findByIsbn(new Isbn("978-0-13-468599-1"))).thenReturn(Optional.of(anUnownedBook()));

    assertThatThrownBy(() -> service.getDetail("978-0-13-468599-1", 1L))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void findByOwnerPagesTheOwnersBooksForMe() {
    service = new BookService(repository, studentLookup);
    when(repository.findByOwnerId(OWNER_ID, null, 20))
        .thenReturn(new CursorPage<>(List.of(anOwnedBook()), null));

    var result = service.findByOwner(OWNER_ID, null, 20);

    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).isbn()).isEqualTo("978-0-13-468599-1");
  }
}
