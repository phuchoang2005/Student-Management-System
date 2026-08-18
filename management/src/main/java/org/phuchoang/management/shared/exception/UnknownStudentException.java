package org.phuchoang.management.shared.exception;

/** 400 — {@code BookService} owner reference, {@code EnrollmentService} student reference, via {@code StudentLookup.existsById}. */
public class UnknownStudentException extends UnknownReferenceException {

  public UnknownStudentException(String message) {
    super(message);
  }
}
