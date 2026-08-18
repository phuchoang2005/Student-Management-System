package org.phuchoang.management.student.application.command;

import java.time.LocalDate;

public record RegisterStudentCommand(
    String studentCode, String firstName, String lastName, String email, LocalDate dateOfBirth) {}
