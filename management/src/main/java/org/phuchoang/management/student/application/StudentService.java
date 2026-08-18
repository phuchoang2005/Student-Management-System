package org.phuchoang.management.student.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.phuchoang.management.identity.AccountProvisioning;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.DuplicateEmailException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.student.StudentDeleted;
import org.phuchoang.management.student.application.command.RegisterStudentCommand;
import org.phuchoang.management.student.application.command.UpdateStudentCommand;
import org.phuchoang.management.student.domain.DateOfBirth;
import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.domain.StudentCode;
import org.phuchoang.management.student.port.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

  private final StudentRepository repository;
  private final AccountProvisioning accountProvisioning;
  private final ApplicationEventPublisher events;

  public StudentService(
      StudentRepository repository,
      AccountProvisioning accountProvisioning,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.accountProvisioning = accountProvisioning;
    this.events = events;
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
   * findByCode (404 if absent) → (if email changed) existsByEmailExcludingCode (409) →
   * {@code Student.applyChanges} → save → (if email changed) {@code
   * AccountProvisioning.renameUsernameForStudent}, all in one transaction — mirrors {@link
   * #register}'s "never leave a student and its account out of sync" guarantee, this time for
   * req.md §3's "account username tracks the student's email" invariant (TC-STU-018).
   *
   * <p>Takes the raw {@code String code} the controller receives from the path, not {@code
   * StudentCode}: like {@link #register}, {@code StudentCode} is constructed here rather than
   * accepted as a parameter so the Web layer never depends on a Domain-layer type
   * (LayeringRulesTest).
   */
  @Transactional
  public UpdatedStudent update(String code, UpdateStudentCommand command) {
    StudentCode studentCode = new StudentCode(code);
    Student student =
        repository
            .findByCode(studentCode)
            .orElseThrow(() -> new NotFoundException("Student '" + code + "' does not exist."));

    Email email = new Email(command.email());
    boolean emailChanged = !email.equals(student.email());
    if (emailChanged && repository.existsByEmailExcludingCode(email, studentCode)) {
      throw new DuplicateEmailException("Email '" + email.value() + "' is already used by another student.");
    }

    DateOfBirth dateOfBirth = new DateOfBirth(command.dateOfBirth());
    student.applyChanges(command.firstName(), command.lastName(), email, dateOfBirth);
    student = repository.save(student);

    if (emailChanged) {
      accountProvisioning.renameUsernameForStudent(student.id().value(), student.email().value());
    }

    return new UpdatedStudent(
        student.id().value(),
        student.code().value(),
        student.firstName(),
        student.lastName(),
        student.email().value(),
        student.dateOfBirth().value(),
        student.createdAt(),
        student.updatedAt());
  }

  /**
   * findByCode (404 if absent) → {@code repository.deleteByCode} → publish {@code StudentDeleted}
   * (06-low-level-design.md §2.3, §13). The `book`/`enrollment`/`identity` cascade listeners that
   * consume this event don't exist until those modules ship in later sprints (04-sprint-backlog.md
   * §3) — for now, the DB-level {@code ON DELETE CASCADE}/{@code SET NULL} constraints
   * (05-database-schema.md §5) are the only cascade actually in effect; publishing here just makes
   * sure the event is on the classpath and fires so those listeners can be wired in without
   * touching this method again.
   */
  @Transactional
  public void remove(String code) {
    StudentCode studentCode = new StudentCode(code);
    Student student =
        repository
            .findByCode(studentCode)
            .orElseThrow(() -> new NotFoundException("Student '" + code + "' does not exist."));

    repository.deleteByCode(studentCode);
    events.publishEvent(new StudentDeleted(student.id()));
  }

  /** UC-13 — matches code/name/email, paged. {@code query} may be blank/{@code null}. */
  public Page<StudentSummaryView> search(String query, Pageable pageable) {
    return repository.search(query, pageable).map(this::toSummaryView);
  }

  /**
   * findByCode (404 if absent), then composes the student's owned books and active enrollments.
   * {@code ownedBooks}/{@code activeCourses} are stubbed empty here — {@code BookService.findByOwner}/
   * {@code EnrollmentService.findByStudent} don't exist until `book`/`enrollment` ship in Sprint
   * 2/3, and US-5.5 wires the real calls in (04-sprint-backlog.md §1, §3).
   */
  public StudentDetailView getDetail(String code) {
    StudentCode studentCode = new StudentCode(code);
    Student student =
        repository
            .findByCode(studentCode)
            .orElseThrow(() -> new NotFoundException("Student '" + code + "' does not exist."));

    return new StudentDetailView(
        student.id().value(),
        student.code().value(),
        student.firstName(),
        student.lastName(),
        student.email().value(),
        student.dateOfBirth().value(),
        student.createdAt(),
        student.updatedAt(),
        List.of(),
        List.of());
  }

  private StudentSummaryView toSummaryView(Student student) {
    return new StudentSummaryView(
        student.id().value(),
        student.code().value(),
        student.firstName(),
        student.lastName(),
        student.email().value());
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

  /** Same VO-unwrapping rationale as {@link ProvisionedStudent}, for {@link #update}'s result. */
  public record UpdatedStudent(
      Long id,
      String studentCode,
      String firstName,
      String lastName,
      String email,
      LocalDate dateOfBirth,
      Instant createdAt,
      Instant updatedAt) {}

  /** Same VO-unwrapping rationale as {@link ProvisionedStudent}, for one {@link #search} result. */
  public record StudentSummaryView(
      Long id, String studentCode, String firstName, String lastName, String email) {}

  /** Same VO-unwrapping rationale as {@link ProvisionedStudent}, for {@link #getDetail}'s result. */
  public record StudentDetailView(
      Long id,
      String studentCode,
      String firstName,
      String lastName,
      String email,
      LocalDate dateOfBirth,
      Instant createdAt,
      Instant updatedAt,
      List<Object> ownedBooks,
      List<Object> activeCourses) {}
}
