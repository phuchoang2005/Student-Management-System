package org.phuchoang.management.shared.exception;

/** 404 — {@code IdentityService.viewInitialPassword} when {@code mustChangePassword = false}. */
public class PasswordNoLongerAvailableException extends NotFoundException {

  public PasswordNoLongerAvailableException(String message) {
    super(message);
  }
}
