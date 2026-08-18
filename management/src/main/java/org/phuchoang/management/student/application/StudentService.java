package org.phuchoang.management.student.application;

import java.time.Instant;
import java.time.LocalDate;
import org.phuchoang.management.identity.AccountProvisioning;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.DuplicateEmailException;
import org.phuchoang.management.student.application.command.RegisterStudentCommand;
import org.phuchoang.management.student.domain.DateOfBirth;
import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.domain.StudentCode;
import org.phuchoang.management.student.port.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

  private final StudentRepository repository;
  private final AccountProvisioning accountProvisioning;

  public StudentService(StudentRepository repository, AccountProvisioning accountProvisioning) {
    this.repository = repository;
    this.accountProvisioning = accountProvisioning;
  }

  /**
   * existsByCode → existsByEmail → {@code Student.register} → save → {@code
   * AccountProvisioning.provisionForStudent}, all in one transaction (06-low-level-design.md
   * §4.6) — a registered student must never end up without a login account.
   */
  @Transactional
  public ProvisionedStudent register(RegisterStudentCommand command) {
    StudentCode code = new StudentCode(command.studentCode());
    if (repository.existsByCode(code)) {
      throw new DuplicateCodeException("Student code '" + code.value() + "' is already in use.");
    }

    Email email = new Email(command.email());
    if (repository.existsByEmail(email)) {
      throw new DuplicateEmailException("Email '" + email.value() + "' is already used by another student.");
    }

    DateOfBirth dateOfBirth = new DateOfBirth(command.dateOfBirth());
    Student student =
        Student.register(code, command.firstName(), command.lastName(), email, dateOfBirth);
    student = repository.save(student);

    ProvisionedAccount account =
        accountProvisioning.provisionForStudent(student.id().value(), student.email().value());

    return new ProvisionedStudent(
        student.id().value(),
        student.code().value(),
        student.firstName(),
        student.lastName(),
        student.email().value(),
        student.dateOfBirth().value(),
        student.createdAt(),
        student.updatedAt(),
        account.username(),
        account.plaintextPassword());
  }

  /**
   * Unwraps {@code Student}'s Value Objects here rather than in {@code StudentMapper} — the web
   * layer may never call a method on a Domain-layer object directly (LayeringRulesTest), only the
   * Application layer may.
   */
  public record ProvisionedStudent(
      Long id,
      String studentCode,
      String firstName,
      String lastName,
      String email,
      LocalDate dateOfBirth,
      Instant createdAt,
      Instant updatedAt,
      String username,
      String initialPassword) {}
}
