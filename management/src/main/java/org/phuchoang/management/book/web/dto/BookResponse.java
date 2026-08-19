package org.phuchoang.management.book.web.dto;

import java.time.Instant;
import java.time.LocalDate;

public record BookResponse(
    Long id,
    String isbn,
    String title,
    String author,
    LocalDate publishedDate,
    Long ownerId,
    Instant createdAt,
    Instant updatedAt) {}
