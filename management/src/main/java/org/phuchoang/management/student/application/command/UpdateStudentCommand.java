package org.phuchoang.management.student.application.command;

import java.time.LocalDate;

public record UpdateStudentCommand(String firstName, String lastName, String email, LocalDate dateOfBirth) {}
