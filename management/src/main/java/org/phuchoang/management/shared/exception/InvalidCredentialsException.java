package org.phuchoang.management.shared.exception;

/** 401 — login authentication failure, {@code IdentityService.changePassword} current-password mismatch. */
public class InvalidCredentialsException extends UnauthorizedException {

  public InvalidCredentialsException(String message) {
    super(message);
  }
}
