package org.phuchoang.management.shared.web;

import java.time.Instant;

/** {@code Error} envelope — api-specification.md §3 ({@code timestamp, status, error, message, path}). */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {}
