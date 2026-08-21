package org.phuchoang.management.book.application.command;

import java.time.LocalDate;

/**
 * {@code ownerStudentCode} is the Student's business key, not the {@code books.owner_id} FK —
 * {@code BookService} resolves one to the other through {@code StudentLookup.idOf}
 * (api-specification.md §5 decision #9). {@code null} means "add the book unowned".
 */
public record AddBookCommand(
    String isbn, String title, String author, LocalDate publishedDate, String ownerStudentCode) {}
