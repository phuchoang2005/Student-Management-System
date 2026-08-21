package org.phuchoang.management.book.web.dto;

/** {@code ownerStudentCode} is {@code null} when the book is unowned. */
public record BookSummaryDto(String isbn, String title, String author, String ownerStudentCode) {}
