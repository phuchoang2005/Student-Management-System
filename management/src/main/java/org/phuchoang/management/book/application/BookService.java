package org.phuchoang.management.book.application;

import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

  private final BookRepository repository;
  private final StudentLookup studentLookup;

  public BookService(BookRepository repository, StudentLookup studentLookup) {
    this.repository = repository;
    this.studentLookup = studentLookup;
  }

  /**
   * existsByIsbn → (if an owner was given) {@code StudentLookup.existsById} (Book.4) → {@code
   * Book.create} → save, mirroring the uniqueness-then-validate-then-persist order of {@code
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
   * findByIsbn (404 if absent) → {@code StudentLookup.existsById} (Book.4) → {@code
   * Book.assignOwner} (Book.2 — replaces any prior owner) → save, mirroring {@code
   * CourseService.update}'s findByCode-then-mutate-then-save shape.
   */
  @Transactional
  public AssignedBook assignOwner(String isbn, AssignBookOwnerCommand command) {
    Isbn bookIsbn = new Isbn(isbn);
    Book book =
        repository
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
   * Unwraps {@code Book}'s Value Objects here rather than in {@code BookMapper} — the web layer
   * may never call a method on a Domain-layer object directly (LayeringRulesTest).
   */
  public record AddedBook(
      Long id,
      String isbn,
      String title,
      String author,
      LocalDate publishedDate,
      Long ownerId,
      Instant createdAt,
      Instant updatedAt) {}

  /** Same VO-unwrapping rationale as {@link AddedBook}, for {@link #assignOwner}'s result. */
  public record AssignedBook(
      Long id,
      String isbn,
      String title,
      String author,
      LocalDate publishedDate,
      Long ownerId,
      Instant createdAt,
      Instant updatedAt) {}
}
