package org.phuchoang.management.book.domain;

import java.time.Instant;
import java.time.LocalDate;
import org.phuchoang.management.book.BookId;
import org.phuchoang.management.student.StudentId;

/**
 * {@code createdAt}/{@code updatedAt}/{@code version} are set by the application, not read back
 * from MySQL's column defaults, mirroring {@code Course}/{@code Student}
 * (06-low-level-design.md §4.4, §6).
 */
public class Book {

  private BookId id;
  private final Isbn isbn;
  private final String title;
  private final String author;
  private final LocalDate publishedDate;
  private final StudentId ownerId;
  private final Instant createdAt;
  private Instant updatedAt;
  private final long version;

  private Book(
      BookId id,
      Isbn isbn,
      String title,
      String author,
      LocalDate publishedDate,
      StudentId ownerId,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = id;
    this.isbn = isbn;
    this.title = title;
    this.author = author;
    this.publishedDate = publishedDate;
    this.ownerId = ownerId;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  /** Book.3 — {@code ownerId} may be {@code null}: a newly added book may have no owner. */
  public static Book create(
      Isbn isbn, String title, String author, LocalDate publishedDate, StudentId ownerId) {
    Instant now = Instant.now();
    return new Book(null, isbn, title, author, publishedDate, ownerId, now, now, 0L);
  }

  /**
   * Rehydrates a {@code Book} from data already validated at write time (a DB row) — bypasses
   * {@link #create}'s invariant checks, mirroring {@code Course.reconstitute}.
   */
  public static Book reconstitute(
      BookId id,
      Isbn isbn,
      String title,
      String author,
      LocalDate publishedDate,
      StudentId ownerId,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new Book(id, isbn, title, author, publishedDate, ownerId, createdAt, updatedAt, version);
  }

  public BookId id() {
    return id;
  }

  public Isbn isbn() {
    return isbn;
  }

  public String title() {
    return title;
  }

  public String author() {
    return author;
  }

  public LocalDate publishedDate() {
    return publishedDate;
  }

  public StudentId ownerId() {
    return ownerId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public long version() {
    return version;
  }
}
