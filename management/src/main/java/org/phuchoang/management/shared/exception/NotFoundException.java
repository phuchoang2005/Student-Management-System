package org.phuchoang.management.shared.exception;

import org.springframework.http.HttpStatus;

/** 404 — {@code *Service.getDetail(...)} when the requested aggregate no longer exists. */
public class NotFoundException extends ApiException {

  public NotFoundException(String message) {
    super(message);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.NOT_FOUND;
  }
}
