package org.phuchoang.management.shared.exception;

/**
 * 409 — a {@code *Service} write method saved an aggregate loaded with a {@code version} that no
 * longer matches the row (06-low-level-design.md §10, optimistic locking).
 */
public class StaleWriteException extends ConflictException {

  public StaleWriteException(String message) {
    super(message);
  }
}
