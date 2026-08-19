package org.phuchoang.management.me.web.dto;

import org.phuchoang.management.shared.web.PageResponse;

/** Matches the OpenAPI {@code MeBooksAndCoursesResponse} schema — {@code books}/{@code courses} page independently. */
public record MeBooksAndCoursesResponse(
    PageResponse<MeBookSummaryDto> books, PageResponse<MeCourseSummaryDto> courses) {}
