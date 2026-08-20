package org.phuchoang.management.shared.exception;

/** 404 — {@code IdentityService.setAccountEnabled} (UC-25). */
public class UserNotFoundException extends NotFoundException {

  public UserNotFoundException(String message) {
    super(message);
  }
}
