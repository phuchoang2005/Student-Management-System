package org.phuchoang.management.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.phuchoang.management.shared.exception.ApiException;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.FieldError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the {@link ApiException} hierarchy — plus the two framework exceptions that need the same
 * envelope but aren't part of it (bean-validation and access-denial) — to the {@code Error}/{@code
 * ValidationError} shape fixed by api-specification.md §3 (06-low-level-design.md §3).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
    HttpStatus status = ex.getStatus();
    if (ex instanceof DomainValidationException dve && !dve.getErrors().isEmpty()) {
      return ResponseEntity.status(status)
          .body(validationErrorResponse(status, ex.getMessage(), request, dve.getErrors()));
    }
    return ResponseEntity.status(status).body(errorResponse(status, ex.getMessage(), request));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<FieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(validationErrorResponse(HttpStatus.BAD_REQUEST, "Validation failed", request, errors));
  }

  // A field typed LocalDate/etc. that can't be parsed (e.g. a non-existent calendar date like
  // "2023-02-30") never reaches a VO constructor -- Jackson rejects it during deserialization,
  // before the controller method is even invoked. Normalized to the same envelope so callers see
  // one consistent 400 shape regardless of which layer caught the malformed input.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Object> handleMalformedRequest(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(errorResponse(HttpStatus.BAD_REQUEST, "Malformed request body", request));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(errorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request));
  }

  private ErrorResponse errorResponse(HttpStatus status, String message, HttpServletRequest request) {
    return new ErrorResponse(
        Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }

  private ValidationErrorResponse validationErrorResponse(
      HttpStatus status, String message, HttpServletRequest request, List<FieldError> errors) {
    return new ValidationErrorResponse(
        Instant.now(),
        status.value(),
        status.getReasonPhrase(),
        message,
        request.getRequestURI(),
        errors);
  }
}
