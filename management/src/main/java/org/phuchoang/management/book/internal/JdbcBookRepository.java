package org.phuchoang.management.book.internal;

import java.util.Optional;
import org.phuchoang.management.book.BookId;
import org.phuchoang.management.book.domain.Book;
import org.phuchoang.management.book.domain.Isbn;
import org.phuchoang.management.book.port.BookRepository;
import org.phuchoang.management.shared.exception.StaleWriteException;
import org.phuchoang.management.student.StudentId;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JdbcBookRepository implements BookRepository {

  private final SpringDataBookRepository springRepo;

  JdbcBookRepository(SpringDataBookRepository springRepo) {
    this.springRepo = springRepo;
  }

  @Override
  public boolean existsByIsbn(Isbn isbn) {
    return springRepo.existsByIsbn(isbn.value());
  }

  @Override
  public Optional<Book> findByIsbn(Isbn isbn) {
    return springRepo.findByIsbn(isbn.value()).map(this::toDomain);
  }

  @Override
  public Book save(Book book) {
    try {
      return toDomain(springRepo.save(toRow(book)));
    } catch (OptimisticLockingFailureException e) {
      throw new StaleWriteException("Book " + book.isbn().value() + " was modified concurrently");
    }
  }

  private BookRow toRow(Book book) {
    BookId id = book.id();
    StudentId ownerId = book.ownerId();
    return new BookRow(
        id == null ? null : id.value(),
        book.isbn().value(),
        book.title(),
        book.author(),
        book.publishedDate(),
        ownerId == null ? null : ownerId.value(),
        book.version(),
        book.createdAt(),
        book.updatedAt());
  }

  private Book toDomain(BookRow row) {
    return Book.reconstitute(
        new BookId(row.id()),
        new Isbn(row.isbn()),
        row.title(),
        row.author(),
        row.publishedDate(),
        row.ownerId() == null ? null : new StudentId(row.ownerId()),
        row.createdAt(),
        row.updatedAt(),
        row.version());
  }
}
