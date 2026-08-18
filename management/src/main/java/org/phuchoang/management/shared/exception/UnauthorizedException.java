package org.phuchoang.management.shared.exception;

import org.springframework.http.HttpStatus;

/** 401 — the caller's credentials are missing or wrong. */
public abstract class UnauthorizedException extends ApiException {

  protected UnauthorizedException(String message) {
    super(message);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.UNAUTHORIZED;
  }
}
