package org.phuchoang.management.book.web.dto;

public record BookSummaryDto(Long id, String isbn, String title, String author, Long ownerId) {}
