package org.phuchoang.management.book.internal;

import java.util.List;
import java.util.Optional;
import org.phuchoang.management.book.BookId;
import org.phuchoang.management.book.domain.Book;
import org.phuchoang.management.book.domain.Isbn;
import org.phuchoang.management.book.port.BookRepository;
import org.phuchoang.management.shared.exception.StaleWriteException;
import org.phuchoang.management.shared.paging.CursorCodec;
import org.phuchoang.management.shared.paging.CursorPage;
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
  public CursorPage<Book> search(String query, StudentId ownerFilter, String afterKey, int limit) {
    Long ownerId = ownerFilter == null ? null : ownerFilter.value();
    String booleanQuery = toBooleanModeQuery(query);
    List<BookRow> rows = springRepo.search(booleanQuery, ownerId, afterKey, limit + 1);
    return toCursorPage(rows, limit);
  }

  @Override
  public CursorPage<Book> findByOwnerId(StudentId ownerId, String afterKey, int limit) {
    List<BookRow> rows = springRepo.findByOwnerId(ownerId.value(), afterKey, limit + 1);
    return toCursorPage(rows, limit);
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

  /**
   * Keyset pagination (PM-045): the Spring Data method is always called with {@code limit + 1}, so
   * a result of {@code limit + 1} rows means another page exists — the extra row is dropped and
   * {@code nextCursor} is derived from the last row actually kept; anything else (including empty)
   * means this is the last page.
   */
  private CursorPage<Book> toCursorPage(List<BookRow> rows, int limit) {
    List<Book> books = rows.stream().map(this::toDomain).toList();
    if (books.size() > limit) {
      List<Book> content = books.subList(0, limit);
      String nextCursor = CursorCodec.encode(content.get(content.size() - 1).isbn().value());
      return new CursorPage<>(content, nextCursor);
    }
    return new CursorPage<>(books, null);
  }

  /**
   * Builds a MySQL boolean-mode FULLTEXT expression requiring every token in the raw query as a
   * prefix match (PM-044), mirroring how the built-in FULLTEXT parser tokenizes indexed text on
   * non-alphanumeric boundaries -- see JdbcStudentRepository.toBooleanModeQuery for the full
   * rationale (a single glued trailing '*' breaks on any query with its own word separators, e.g.
   * an ISBN like "978-0-13-235088-4" indexes as five separate numeric tokens; and a plain
   * multi-word AGAINST with no '+' defaults to OR). {@code findByOwnerId}'s exact-match filter
   * never goes through this. Tokens under {@code innodb_ft_min_token_size}'s default of 3
   * characters are dropped rather than required -- MySQL never indexes them, so requiring one as a
   * mandatory term would make the whole query unsatisfiable (e.g. an ISBN's single-digit segments).
   * A {@code null}/blank query passes through unchanged; the query itself already treats blank as
   * "no filter".
   */
  private static String toBooleanModeQuery(String query) {
    if (query == null || query.isBlank()) {
      return query;
    }
    StringBuilder booleanQuery = new StringBuilder();
    for (String token : query.split("[^\\p{Alnum}]+")) {
      if (token.length() < 3) {
        continue;
      }
      if (booleanQuery.length() > 0) {
        booleanQuery.append(' ');
      }
      booleanQuery.append('+').append(token).append('*');
    }
    return booleanQuery.isEmpty() ? null : booleanQuery.toString();
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
