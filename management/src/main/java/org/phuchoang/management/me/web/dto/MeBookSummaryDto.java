package org.phuchoang.management.me.web.dto;

/** Matches the OpenAPI {@code BookSummary} schema. No owner field — every row is the caller's own. */
public record MeBookSummaryDto(String isbn, String title, String author) {}
