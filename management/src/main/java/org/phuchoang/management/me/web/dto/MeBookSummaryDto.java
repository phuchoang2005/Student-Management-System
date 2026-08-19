package org.phuchoang.management.me.web.dto;

/** Matches the OpenAPI {@code BookSummary} schema — {@code ownerId} is always the caller's own id here. */
public record MeBookSummaryDto(Long id, String isbn, String title, String author, Long ownerId) {}
