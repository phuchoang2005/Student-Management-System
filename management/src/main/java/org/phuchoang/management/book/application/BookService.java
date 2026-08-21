package org.phuchoang.management.book.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
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
import org.phuchoang.management.student.StudentCode;
import org.phuchoang.management.student.StudentDeleted;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;
import org.phuchoang.management.student.StudentSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A book is named by its {@link Isbn} and its owner by a {@link StudentCode} — {@code books.id} and
 * {@code books.owner_id} are database concerns and never appear in a command, a query parameter, or
 * a response (api-specification.md §5 decision #9). {@code StudentLookup.idOf} is the one place a
 * code becomes the FK value.
 */
@Service
public class BookService implements BookLookup {

  private final BookRepository repository;
  private final StudentLookup studentLookup;

  public BookService(BookRepository repository, StudentLookup studentLookup) {
    this.repository = repository;
    this.studentLookup = studentLookup;
  }

  /**
   * existsByIsbn → (if an owner was given) {@code StudentLookup.idOf} (Book.4) → {@code Book.create}
   * → save, mirroring the uniqueness-then-validate-then-persist order of {@code
   * CourseService.create}/{@code StudentService.register}. {@code idOf} is both the Book.4
   * existence check and the code→id resolution, so the ordering is unchanged from when this step
   * was a bare {@code existsById}.
   */
  @Transactional
  public AddedBook addBook(AddBookCommand command) {
    Isbn isbn = new Isbn(command.isbn());
    if (repository.existsByIsbn(isbn)) {
      throw new DuplicateIsbnException("ISBN '" + isbn.value() + "' is already in use.");
    }

    StudentCode ownerCode = optionalCode(command.ownerStudentCode());
    StudentId ownerId = ownerCode == null ? null : resolve(ownerCode);

    Book book = Book.create(isbn, command.title(), command.author(), command.publishedDate(), ownerId);
    book = repository.save(book);

    return new AddedBook(
        book.isbn().value(),
        book.title(),
        book.author(),
        book.publishedDate(),
        ownerCode == null ? null : ownerCode.value(),
        book.createdAt(),
        book.updatedAt());
  }

  /**
   * findByIsbn (404 if absent) → {@code StudentLookup.idOf} (Book.4) → {@code Book.assignOwner}
   * (Book.2 — replaces any prior owner) → save, mirroring {@code CourseService.update}'s
   * findByCode-then-mutate-then-save shape.
   */
  @Transactional
  public AssignedBook assignOwner(String isbn, AssignBookOwnerCommand command) {
    Isbn bookIsbn = new Isbn(isbn);
    Book book = repository
        .findByIsbn(bookIsbn)
        .orElseThrow(() -> new NotFoundException("Book '" + isbn + "' does not exist."));

    StudentCode ownerCode = new StudentCode(command.studentCode());
    book.assignOwner(resolve(ownerCode));
    book = repository.save(book);

    return new AssignedBook(
        book.isbn().value(),
        book.title(),
        book.author(),
        book.publishedDate(),
        ownerCode.value(),
        book.createdAt(),
        book.updatedAt());
  }

  /**
   * findByIsbn (404 if absent) → {@code Book.clearOwner} (Book.5) → save. Deliberately idempotent
   * on an already-unowned book — clearing a null owner is still a {@code 200} with {@code
   * ownerStudentCode: null}, not an error (api-specification.md §5.7), mirroring {@link
   * #assignOwner}'s findByIsbn-then-mutate-then-save shape.
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
        book.isbn().value(),
        book.title(),
        book.author(),
        book.publishedDate(),
        book.createdAt(),
        book.updatedAt());
  }

  /**
   * existsByIsbn (404 if absent) → {@code repository.deleteByIsbn}. No event is published — unlike
   * {@code CourseService.remove}, book removal never cascades to anything (req.md §5 "When a book
   * is removed": the owning student, if any, is left untouched), so there's nothing for a listener
   * to react to.
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
   * Closes the {@code StudentService.remove} stub (06-low-level-design.md §13, US-1.3/PM-018) —
   * clears ownership on every book the deleted student owned, mirroring {@code
   * EnrollmentService.onStudentDeleted}'s shape.
   */
  @ApplicationModuleListener
  void onStudentDeleted(StudentDeleted event) {
    repository.clearOwnerByStudentId(event.studentId());
  }

  /**
   * UC-14 — matches isbn/title/author, optionally filtered by owner, paged. {@code query} may be
   * blank/{@code null}. {@code ownerStudentCode} is how the Librarian pulls up one student's
   * borrowed books from that student's detail page; an unknown code is a {@code 400}, consistent
   * with every other unresolvable reference (api-specification.md §5 decision #2).
   *
   * <p>{@code callerStudentId} is non-null only for a STUDENT caller (02-component-diagram.md §4);
   * when present it silently overrides {@code ownerStudentCode} rather than rejecting a mismatched
   * client-supplied value — consistent with the "transparently scoped, never blocked" search
   * philosophy (api-specification.md §5 decision #4), and it short-circuits the code resolution
   * entirely since the principal already carries the id.
   */
  public Page<BookSummaryView> search(
      String query, String ownerStudentCode, Pageable pageable, Long callerStudentId) {
    StudentId ownerId;
    if (callerStudentId != null) {
      ownerId = new StudentId(callerStudentId);
    } else {
      StudentCode filter = optionalCode(ownerStudentCode);
      ownerId = filter == null ? null : resolve(filter);
    }

    // One lookup per distinct owner rather than one per row: a Librarian's catalogue page is mostly
    // the same handful of borrowers, and an owner-filtered page is a single owner by construction.
    Map<Long, String> ownerCodes = new HashMap<>();
    return repository.search(query, ownerId, pageable).map(book -> toSummaryView(book, ownerCodes));
  }

  /**
   * findByIsbn (404 if absent), then (if owned) {@code StudentLookup.summaryOf} to embed the current
   * owner's summary, mirroring {@code CourseService.getDetail}'s findByCode-then-compose shape.
   *
   * <p>{@code callerStudentId} is non-null only for a STUDENT caller; a book not owned by that
   * student — including an unowned book — is a 403, not a 404: the resource exists, only
   * authorization fails (api-specification.md §5 decision #3, same "own records only" reading as
   * {@code StudentService.getDetail}).
   */
  public BookDetailView getDetail(String isbn, Long callerStudentId) {
    Isbn bookIsbn = new Isbn(isbn);
    Book book = repository
        .findByIsbn(bookIsbn)
        .orElseThrow(() -> new NotFoundException("Book '" + isbn + "' does not exist."));

    if (callerStudentId != null
        && (book.ownerId() == null || !book.ownerId().value().equals(callerStudentId))) {
      throw new AccessDeniedException("Book '" + isbn + "' is not owned by the requesting student.");
    }

    StudentSummary owner = book.ownerId() == null ? null : studentLookup.summaryOf(book.ownerId());

    return new BookDetailView(
        book.isbn().value(),
        book.title(),
        book.author(),
        book.publishedDate(),
        owner == null ? null : owner.studentCode(),
        book.createdAt(),
        book.updatedAt(),
        owner);
  }

  /** Backs {@code BookLookup.findByOwner} (US-5.4, {@code GET /api/v1/me/books}). */
  @Override
  @Transactional(readOnly = true)
  public Page<BookSummary> findByOwner(StudentId ownerId, Pageable pageable) {
    return repository.findByOwnerId(ownerId, pageable).map(this::toSummary);
  }

  /** Book.4 — an owner reference that resolves to no student is malformed input, not a 404. */
  private StudentId resolve(StudentCode code) {
    return studentLookup
        .idOf(code)
        .orElseThrow(() -> new UnknownStudentException("Student '" + code.value() + "' does not exist."));
  }

  private StudentCode optionalCode(String value) {
    return value == null || value.isBlank() ? null : new StudentCode(value);
  }

  private BookSummaryView toSummaryView(Book book, Map<Long, String> ownerCodes) {
    String ownerStudentCode = book.ownerId() == null
        ? null
        : ownerCodes.computeIfAbsent(
            book.ownerId().value(), id -> studentLookup.summaryOf(new StudentId(id)).studentCode());
    return new BookSummaryView(book.isbn().value(), book.title(), book.author(), ownerStudentCode);
  }

  private BookSummary toSummary(Book book) {
    return new BookSummary(book.isbn().value(), book.title(), book.author());
  }

  /**
   * Unwraps {@code Book}'s Value Objects here rather than in {@code BookMapper} — the web layer may
   * never call a method on a Domain-layer object directly (LayeringRulesTest).
   */
  public record AddedBook(
      String isbn,
      String title,
      String author,
      LocalDate publishedDate,
      String ownerStudentCode,
      Instant createdAt,
      Instant updatedAt) {}

  /** Same VO-unwrapping rationale as {@link AddedBook}, for {@link #assignOwner}'s result. */
  public record AssignedBook(
      String isbn,
      String title,
      String author,
      LocalDate publishedDate,
      String ownerStudentCode,
      Instant createdAt,
      Instant updatedAt) {}

  /**
   * Same VO-unwrapping rationale as {@link AddedBook}, for {@link #unassignOwner}'s result. No
   * {@code ownerStudentCode} field — it's always {@code null} after unassignment.
   */
  public record UnassignedBook(
      String isbn,
      String title,
      String author,
      LocalDate publishedDate,
      Instant createdAt,
      Instant updatedAt) {}

  /** Same VO-unwrapping rationale as {@link AddedBook}, for one {@link #search} result. */
  public record BookSummaryView(String isbn, String title, String author, String ownerStudentCode) {}

  /**
   * Same VO-unwrapping rationale as {@link AddedBook}, for {@link #getDetail}'s result. {@code
   * owner}/{@code ownerStudentCode} are {@code null} when the book is unowned.
   */
  public record BookDetailView(
      String isbn,
      String title,
      String author,
      LocalDate publishedDate,
      String ownerStudentCode,
      Instant createdAt,
      Instant updatedAt,
      StudentSummary owner) {}
}
