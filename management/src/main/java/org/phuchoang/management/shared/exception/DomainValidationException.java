package org.phuchoang.management.shared.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 400 — VO constructors and aggregate factory methods (06-low-level-design.md §3). Carries an
 * optional per-field {@link FieldError} list so {@code GlobalExceptionHandler} can render the
 * {@code ValidationError} envelope variant instead of the plain {@code Error} one.
 */
public class DomainValidationException extends ApiException {

  private final List<FieldError> errors;

  public DomainValidationException(String message) {
    this(message, List.of());
  }

  public DomainValidationException(String message, List<FieldError> errors) {
    super(message);
    this.errors = List.copyOf(errors);
  }

  public List<FieldError> getErrors() {
    return errors;
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.BAD_REQUEST;
  }
}
