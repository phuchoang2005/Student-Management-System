package org.phuchoang.management.shared.exception;

/** 400 — a caller-supplied foreign-key reference doesn't exist; malformed input, not a state conflict. */
public abstract class UnknownReferenceException extends DomainValidationException {

  protected UnknownReferenceException(String message) {
    super(message);
  }
}
