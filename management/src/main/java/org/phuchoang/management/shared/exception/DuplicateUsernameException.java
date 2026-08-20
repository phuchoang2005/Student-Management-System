package org.phuchoang.management.shared.exception;

/** 409 — {@code IdentityService.provisionStaff} (Identity.2, Identity.6, UC-24). */
public class DuplicateUsernameException extends ConflictException {

  public DuplicateUsernameException(String message) {
    super(message);
  }
}
