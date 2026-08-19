package org.phuchoang.management.book.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.phuchoang.management.book.BookLookup;
import org.phuchoang.management.book.BookSummary;
import org.phuchoang.management.book.application.command.AddBookCommand;
import org.phuchoang.management.book.application.command.AssignBookOwnerCommand;
import org.phuchoang.management.book.domain.Book;
import org.phuchoang.management.book.domain.Isbn;
import org.phuchoang.management.book.port.BookRepository;
import org.phuchoang.management.shared.exception.DuplicateIsbnException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.exception.UnknownStudentException;
import org.phuchoang.management.student.StudentDeleted;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;
import org.phuchoang.management.student.StudentSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService implements BookLookup {

  private final BookRepository repository;
  private final StudentLookup studentLookup;

  public BookService(BookRepository repository, StudentLookup studentLookup) {
    this.repository = repository;
    this.studentLookup = studentLookup;
  }

  /**
   * existsByIsbn → (if an owner was given) {@code StudentLookup.existsById}
   * (Book.4) → {@code
   * Book.create} → save, mirroring the uniqueness-then-validate-then-persist
   * order of {@code
   * CourseService.create}/{@code StudentService.register}.
   */
  @Transactional
  public AddedBook addBook(AddBookCommand command) {
    Isbn isbn = new Isbn(command.isbn());
    if (repository.existsByIsbn(isbn)) {
      throw new DuplicateIsbnException("ISBN '" + isbn.value() + "' is already in use.");
    }

    StudentId ownerId = command.ownerId() == null ? null : new StudentId(command.ownerId());
    if (ownerId != null && !studentLookup.existsById(ownerId)) {
      throw new UnknownStudentException("Student '" + ownerId.value() + "' does not exist.");
    }

    Book book = Book.create(isbn, command.title(), command.author(), command.publishedDate(), ownerId);
    book = repository.save(book);

    return new AddedBook(
        book.id().value(),
        book.isbn().value(),
        book.title(),
        book.author(),
        book.publishedDate(),
        book.ownerId() == null ? null : book.ownerId().value(),
        book.createdAt(),
        book.updatedAt());
  }

  /**
   * findByIsbn (404 if absent) → {@code StudentLookup.existsById} (Book.4) →
   * {@code
   * Book.assignOwner} (Book.2 — replaces any prior owner) → save, mirroring
   * {@code
   * CourseService.update}'s findByCode-then-mutate-then-save shape.
   */
  @Transactional
  public AssignedBook assignOwner(String isbn, AssignBookOwnerCommand command) {
    Isbn bookIsbn = new Isbn(isbn);
    Book book = repository
        .findByIsbn(bookIsbn)
        .orElseThrow(() -> new NotFoundException("Book '" + isbn + "' does not exist."));

    StudentId ownerId = new StudentId(command.studentId());
    if (!studentLookup.existsById(ownerId)) {
      throw new UnknownStudentException("Student '" + ownerId.value() + "' does not exist.");
    }

    book.assignOwner(ownerId);
    book = repository.save(book);

    return new AssignedBook(
        book.id().value(),
        book.isbn().value(),
        book.title(),
        book.author(),
        book.publishedDate(),
        book.ownerId().value(),
        book.createdAt(),
        book.updatedAt());
  }

  /**
   * findByIsbn (404 if absent) → {@code Book.clearOwner} (Book.5) → save.
   * Deliberately idempotent
   * on an already-unowned book — clearing a null owner is still a {@code 200}
   * with {@code owner:
   * null}, not an error (api-specification.md §5.7), mirroring
   * {@link #assignOwner}'s
   * findByIsbn-then-mutate-then-save shape.
   */
  @Transactional
  public UnassignedBook unassignOwner(String isbn) {
    Isbn bookIsbn = new Isbn(isbn);
    Book book = repository
        .findByIsbn(bookIsbn)
        .orElseThrow(() -> new NotFoundException("Book '" + isbn + "' does not exist."));

    book.clearOwner();
    book = repository.save(book);

    return new UnassignedBook(
        book.id().value(),
        book.isbn().value(),
        book.title(),
        book.author(),
        book.publishedDate(),
        book.createdAt(),
        book.updatedAt());
  }

  /**
   * existsByIsbn (404 if absent) → {@code repository.deleteByIsbn}. No event is
   * published —
   * unlike {@code CourseService.remove}, book removal never cascades to anything
   * (req.md §5 "When
   * a book is removed": the owning student, if any, is left untouched), so
   * there's nothing for a
   * listener to react to.
   */
  @Transactional
  public void remove(String isbn) {
    Isbn bookIsbn = new Isbn(isbn);
    if (!repository.existsByIsbn(bookIsbn)) {
      throw new NotFoundException("Book '" + isbn + "' does not exist.");
    }

    repository.deleteByIsbn(bookIsbn);
  }

  /**
   * Closes the {@code StudentService.remove} stub (06-low-level-design.md §13,
   * US-1.3/PM-018) —
   * clears ownership on every book the deleted student owned, mirroring {@code
   * EnrollmentService.onStudentDeleted}'s shape.
   */
  @ApplicationModuleListener
  @Async
  void onStudentDeleted(StudentDeleted event) {
    repository.clearOwnerByStudentId(event.studentId());
  }

  /**
   * UC-14 — matches isbn/title/author, optionally filtered by owner, paged.
   * {@code query} may be blank/{@code null}.
   */
  public Page<BookSummaryView> search(String query, Long ownerFilter, Pageable pageable) {
    StudentId ownerId = ownerFilter == null ? null : new StudentId(ownerFilter);
    return repository.search(query, ownerId, pageable).map(this::toSummaryView);
  }

  /**
   * findByIsbn (404 if absent), then (if owned) {@code StudentLookup.summaryOf}
   * to embed the
   * current owner's summary, mirroring {@code CourseService.getDetail}'s
   * findByCode-then-compose
   * shape.
   */
  public BookDetailView getDetail(String isbn) {
    Isbn bookIsbn = new Isbn(isbn);
    Book book = repository
        .findByIsbn(bookIsbn)
        .orElseThrow(() -> new NotFoundException("Book '" + isbn + "' does not exist."));

    StudentSummary owner = book.ownerId() == null ? null : studentLookup.summaryOf(book.ownerId());

    return new BookDetailView(
        book.id().value(),
        book.isbn().value(),
        book.title(),
        book.author(),
        book.publishedDate(),
        book.ownerId() == null ? null : book.ownerId().value(),
        book.createdAt(),
        book.updatedAt(),
        owner);
  }

  /**
   * Unpaginated — backs {@code StudentService.getDetail}'s embedded "owned books"
   * list (US-5.1, US-5.5).
   */
  public List<Book> findByOwner(StudentId ownerId) {
    return repository.findByOwnerId(ownerId);
  }

  /**
   * Backs {@code BookLookup.findByOwner} (US-5.4,
   * {@code GET /api/v1/me/books-and-courses}).
   */
  @Override
  @Transactional(readOnly = true)
  public Page<BookSummary> findByOwner(StudentId ownerId, Pageable pageable) {
    return repository.findByOwnerId(ownerId, pageable).map(this::toSummary);
  }

  private BookSummaryView toSummaryView(Book book) {
    return new BookSummaryView(
        book.id().value(),
        book.isbn().value(),
        book.title(),
        book.author(),
        book.ownerId() == null ? null : book.ownerId().value());
  }

  private BookSummary toSummary(Book book) {
    return new BookSummary(
        book.id().value(), book.isbn().value(), book.title(), book.author(), book.ownerId().value());
  }

  /**
   * Unwraps {@code Book}'s Value Objects here rather than in {@code BookMapper} —
   * the web layer
   * may never call a method on a Domain-layer object directly
   * (LayeringRulesTest).
   */
  public record AddedBook(
      Long id,
      String isbn,
      String title,
      String author,
      LocalDate publishedDate,
      Long ownerId,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Same VO-unwrapping rationale as {@link AddedBook}, for {@link #assignOwner}'s
   * result.
   */
  public record AssignedBook(
      Long id,
      String isbn,
      String title,
      String author,
      LocalDate publishedDate,
      Long ownerId,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Same VO-unwrapping rationale as {@link AddedBook}, for
   * {@link #unassignOwner}'s result. No
   * {@code ownerId} field — it's always {@code null} after unassignment.
   */
  public record UnassignedBook(
      Long id,
      String isbn,
      String title,
      String author,
      LocalDate publishedDate,
      Instant createdAt,
      Instant updatedAt) {
  }

  /**
   * Same VO-unwrapping rationale as {@link AddedBook}, for one {@link #search}
   * result.
   */
  public record BookSummaryView(Long id, String isbn, String title, String author, Long ownerId) {
  }

  /**
   * Same VO-unwrapping rationale as {@link AddedBook}, for {@link #getDetail}'s
   * result. {@code owner} is {@code null} when the book is unowned.
   */
  public record BookDetailView(
      Long id,
      String isbn,
      String title,
      String author,
      LocalDate publishedDate,
      Long ownerId,
      Instant createdAt,
      Instant updatedAt,
      StudentSummary owner) {
  }
}
