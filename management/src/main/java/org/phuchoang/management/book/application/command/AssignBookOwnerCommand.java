package org.phuchoang.management.book.application.command;

/** Same business-key rationale as {@link AddBookCommand#ownerStudentCode()}, for Book.2's reassignment. */
public record AssignBookOwnerCommand(String studentCode) {}
