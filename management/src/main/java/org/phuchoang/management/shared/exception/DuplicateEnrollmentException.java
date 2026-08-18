package org.phuchoang.management.shared.exception;

/** 409 — {@code EnrollmentService.enroll}. */
public class DuplicateEnrollmentException extends ConflictException {

  public DuplicateEnrollmentException(String message) {
    super(message);
  }
}
