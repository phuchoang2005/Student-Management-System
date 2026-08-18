package org.phuchoang.management.shared.exception;

/** 400 — {@code Email} VO constructor, malformed format only (duplicate is {@link DuplicateEmailException}). */
public class InvalidEmailException extends DomainValidationException {

  public InvalidEmailException(String message) {
    super(message);
  }
}
