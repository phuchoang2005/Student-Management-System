package org.phuchoang.management.shared.exception;

/** 400 — {@code EnrollmentService} course reference, via {@code CourseLookup.existsById}. */
public class UnknownCourseException extends UnknownReferenceException {

  public UnknownCourseException(String message) {
    super(message);
  }
}
