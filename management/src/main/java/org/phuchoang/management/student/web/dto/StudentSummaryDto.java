package org.phuchoang.management.student.web.dto;

/** No surrogate {@code id} — a student is named by {@code studentCode} everywhere the API is
 * reachable from (api-specification.md §5 decision #9). */
public record StudentSummaryDto(String studentCode, String firstName, String lastName, String email) {}
