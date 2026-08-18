package org.phuchoang.management.shared.exception;

/** 409 — {@code BookService.addBook}. */
public class DuplicateIsbnException extends ConflictException {

  public DuplicateIsbnException(String message) {
    super(message);
  }
}
