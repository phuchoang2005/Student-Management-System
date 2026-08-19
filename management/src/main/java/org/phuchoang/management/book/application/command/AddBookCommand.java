package org.phuchoang.management.book.application.command;

import java.time.LocalDate;

public record AddBookCommand(
    String isbn, String title, String author, LocalDate publishedDate, Long ownerId) {}
