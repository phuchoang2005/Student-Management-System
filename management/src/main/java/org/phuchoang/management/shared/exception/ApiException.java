package org.phuchoang.management.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base of the shared exception hierarchy (06-low-level-design.md §3). {@code
 * GlobalExceptionHandler} reads {@link #getStatus()} off any caught instance, so every
 * concrete/abstract subclass fixes its own HTTP status rather than the handler switching on type.
 */
public abstract class ApiException extends RuntimeException {

  protected ApiException(String message) {
    super(message);
  }

  public abstract HttpStatus getStatus();
}
