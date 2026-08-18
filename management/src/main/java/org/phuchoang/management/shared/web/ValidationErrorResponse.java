package org.phuchoang.management.shared.web;

import java.time.Instant;
import java.util.List;
import org.phuchoang.management.shared.exception.FieldError;

/** {@code ValidationError} envelope — an {@link ErrorResponse} plus per-field {@code errors} (api-specification.md §3). */
public record ValidationErrorResponse(
    Instant timestamp, int status, String error, String message, String path, List<FieldError> errors) {}
