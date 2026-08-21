package org.phuchoang.management.book.internal;

import java.util.List;
import java.util.Optional;
import org.phuchoang.management.book.BookId;
import org.phuchoang.management.book.domain.Book;
import org.phuchoang.management.book.domain.Isbn;
import org.phuchoang.management.book.port.BookRepository;
import org.phuchoang.management.shared.exception.StaleWriteException;
import org.phuchoang.management.student.StudentId;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
  public Page<Book> search(String query, StudentId ownerFilter, Pageable pageable) {
    Long ownerId = ownerFilter == null ? null : ownerFilter.value();
    List<Book> content =
        springRepo.search(query, ownerId, pageable.getPageSize(), pageable.getOffset()).stream()
            .map(this::toDomain)
            .toList();
    long total = springRepo.countBySearch(query, ownerId);
    return new PageImpl<>(content, pageable, total);
  }

  @Override
  public Page<Book> findByOwnerId(StudentId ownerId, Pageable pageable) {
    List<Book> content =
        springRepo.findByOwnerId(ownerId.value(), pageable.getPageSize(), pageable.getOffset()).stream()
            .map(this::toDomain)
            .toList();
    long total = springRepo.countByOwnerId(ownerId.value());
    return new PageImpl<>(content, pageable, total);
  }

  @Override
  public Book save(Book book) {
    try {
      return toDomain(springRepo.save(toRow(book)));
    } catch (OptimisticLockingFailureException e) {
      throw new StaleWriteException("Book " + book.isbn().value() + " was modified concurrently");
    }
  }

  @Override
  public void deleteByIsbn(Isbn isbn) {
    springRepo.deleteByIsbn(isbn.value());
  }

  @Override
  public void clearOwnerByStudentId(StudentId studentId) {
    springRepo.clearOwnerByStudentId(studentId.value());
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
