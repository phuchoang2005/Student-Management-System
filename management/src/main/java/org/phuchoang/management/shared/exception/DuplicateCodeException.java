package org.phuchoang.management.shared.exception;

/** 409 — {@code StudentService.register}, {@code CourseService.create}. */
public class DuplicateCodeException extends ConflictException {

  public DuplicateCodeException(String message) {
    super(message);
  }
}
