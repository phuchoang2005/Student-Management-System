package org.phuchoang.management.shared.exception;

import org.springframework.http.HttpStatus;

/** 409 — uniqueness violations and lost updates. */
public abstract class ConflictException extends ApiException {

  protected ConflictException(String message) {
    super(message);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.CONFLICT;
  }
}
