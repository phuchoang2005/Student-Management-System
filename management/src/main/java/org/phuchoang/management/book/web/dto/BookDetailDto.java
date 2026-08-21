package org.phuchoang.management.book.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/** {@code owner}/{@code ownerStudentCode} are {@code null} when the book is unowned. */
public record BookDetailDto(
    String isbn,
    String title,
    String author,
    LocalDate publishedDate,
    String ownerStudentCode,
    Instant createdAt,
    Instant updatedAt,
    BookOwnerDto owner) {}
