package org.phuchoang.management.shared.exception;

/** 409 — {@code StudentService.register}/{@code update}. */
public class DuplicateEmailException extends ConflictException {

  public DuplicateEmailException(String message) {
    super(message);
  }
}
