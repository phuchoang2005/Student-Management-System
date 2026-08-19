package org.phuchoang.management.book.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/** {@code owner} is {@code null} when the book is unowned. */
public record BookDetailDto(
    Long id,
    String isbn,
    String title,
    String author,
    LocalDate publishedDate,
    Long ownerId,
    Instant createdAt,
    Instant updatedAt,
    BookOwnerDto owner) {}
