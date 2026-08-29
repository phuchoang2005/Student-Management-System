package org.phuchoang.management.student.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.phuchoang.management.identity.AccountProvisioning;
import org.phuchoang.management.identity.InitialPasswordLookup;
import org.phuchoang.management.identity.InitialPasswordView;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.DuplicateEmailException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.exception.PasswordNoLongerAvailableException;
import org.phuchoang.management.shared.paging.CursorCodec;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.student.StudentCode;
import org.phuchoang.management.student.StudentDeleted;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;
import org.phuchoang.management.student.StudentProfile;
import org.phuchoang.management.student.StudentSummary;
import org.phuchoang.management.student.application.command.RegisterStudentCommand;
import org.phuchoang.management.student.application.command.UpdateStudentCommand;
import org.phuchoang.management.student.domain.DateOfBirth;
import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.port.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService implements StudentLookup {

  private final StudentRepository repository;
  private final AccountProvisioning accountProvisioning;
  private final InitialPasswordLookup initialPasswordLookup;
  private final ApplicationEventPublisher events;

  public StudentService(
      StudentRepository repository,
      AccountProvisioning accountProvisioning,
      InitialPasswordLookup initialPasswordLookup,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.accountProvisioning = accountProvisioning;
    this.initialPasswordLookup = initialPasswordLookup;
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
        student.code().value(),
        student.firstName(),
        student.lastName(),
        student.email().value(),
        student.dateOfBirth().value(),
        student.createdAt(),
        student.updatedAt());
  }

  /**
   * findByCode (404 if absent) → {@code repository.deleteByCode} → {@code
   * AccountProvisioning.deprovisionForStudent} (synchronous — see its Javadoc for why this one
   * cascade can't be an event listener like `book`/`enrollment`'s) → publish {@code StudentDeleted}
   * for the `book`/`enrollment` listeners (06-low-level-design.md §2.3, §13; PM-018).
   */
  @Transactional
  public void remove(String code) {
    StudentCode studentCode = new StudentCode(code);
    Student student =
        repository
            .findByCode(studentCode)
            .orElseThrow(() -> new NotFoundException("Student '" + code + "' does not exist."));

    repository.deleteByCode(studentCode);
    accountProvisioning.deprovisionForStudent(student.id().value());
    events.publishEvent(new StudentDeleted(student.id()));
  }

  /**
   * UC-13 — matches code/name/email via FULLTEXT search, keyset-paginated (PM-044/PM-045).
   * {@code query} may be blank/{@code null}. {@code cursor} is the opaque cursor from the previous
   * page's {@code nextCursor}, {@code null} for the first page. {@code callerStudentId} is
   * non-null only for a STUDENT caller (02-component-diagram.md §4) and narrows the result to that
   * student's own record — transparently (0/1 results, never a 403), per api-specification.md §5
   * decision #4.
   */
  public CursorPage<StudentSummaryView> search(String query, String cursor, int size, Long callerStudentId) {
    StudentId scopeToId = callerStudentId == null ? null : new StudentId(callerStudentId);
    String afterKey = CursorCodec.decode(cursor);
    return repository.search(query, scopeToId, afterKey, size).map(this::toSummaryView);
  }

  /**
   * findByCode (404 if absent) — the record itself, nothing composed. A student's owned books and
   * active enrollments are <em>not</em> embedded here: they are separately paged, separately
   * authorized reads ({@code GET /api/v1/books?ownerStudentCode=} for the Librarian, {@code GET
   * /api/v1/enrollments?studentCode=} for the Registrar and Course Administrator), and folding
   * either into this response would hand every reader of a student record data their role may not
   * see. This replaces the two hardcoded empty lists that stood in for the composition US-5.5
   * originally scoped here.
   *
   * <p>{@code callerStudentId} is non-null only for a STUDENT caller; a mismatch against the
   * resolved student's id is a 403, not a 404 — the resource exists and the request is
   * well-formed, only authorization fails (api-specification.md §5 decision #3). Existence is
   * checked first so the 403 vs. 404 distinction stays meaningful.
   */
  public StudentDetailView getDetail(String code, Long callerStudentId) {
    StudentCode studentCode = new StudentCode(code);
    Student student =
        repository
            .findByCode(studentCode)
            .orElseThrow(() -> new NotFoundException("Student '" + code + "' does not exist."));

    if (callerStudentId != null && !callerStudentId.equals(student.id().value())) {
      throw new AccessDeniedException("Student '" + code + "' is not visible to the requesting student.");
    }

    return new StudentDetailView(
        student.code().value(),
        student.firstName(),
        student.lastName(),
        student.email().value(),
        student.dateOfBirth().value(),
        student.createdAt(),
        student.updatedAt());
  }

  /**
   * US-6.3 / UC-23 — findByCode → {@code InitialPasswordLookup.viewInitialPassword}, read-only.
   * "No such student", "no account", and "password already changed" all raise the same {@link
   * PasswordNoLongerAvailableException} with the same message: api-specification.md §5.5 requires
   * the two 404s to be indistinguishable, which is deliberate information-hiding, not an
   * oversight (TC-IDN-018).
   */
  public InitialPassword viewInitialPassword(String code) {
    String message = "No unchanged initial password found for student '" + code + "'.";
    Long studentId =
        repository
            .findByCode(new StudentCode(code))
            .map(student -> student.id().value())
            .orElseThrow(() -> new PasswordNoLongerAvailableException(message));

    InitialPasswordView view =
        initialPasswordLookup
            .viewInitialPassword(studentId)
            .orElseThrow(() -> new PasswordNoLongerAvailableException(message));

    return new InitialPassword(view.username(), view.initialPassword());
  }

  /**
   * findByCode → the student's surrogate id, or empty when no such student exists. Reuses {@code
   * findByCode} rather than adding a projection query: {@link #viewInitialPassword} already reads a
   * whole row for one field the same way, and the callers ({@code BookService}, {@code
   * EnrollmentService}) run this once per write, not per row.
   */
  @Override
  public Optional<StudentId> idOf(StudentCode code) {
    return repository.findByCode(code).map(Student::id);
  }

  /**
   * findById (404 if absent — callers only pass ids already known to reference an existing
   * student, per Book.4/Enrollment.3, so this only fires on a genuine data race).
   */
  @Override
  public StudentSummary summaryOf(StudentId id) {
    Student student =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Student '" + id.value() + "' does not exist."));

    return toSummary(student);
  }

  /**
   * One bulk lookup for every id named in {@code ids} (PM-046) — backs {@code
   * EnrollmentService}'s batch course/student resolution in place of one {@link #summaryOf} call
   * per row. An id naming no student is simply absent from the result, unlike {@link #summaryOf}'s
   * 404: the caller decides how to treat an id it expected to resolve.
   */
  @Override
  public Map<StudentId, StudentSummary> summariesOf(Collection<StudentId> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Set<StudentId> distinctIds = new LinkedHashSet<>(ids);
    return repository.findByIds(distinctIds).stream()
        .collect(Collectors.toMap(Student::id, this::toSummary, (first, second) -> first));
  }

  private StudentSummary toSummary(Student student) {
    return new StudentSummary(
        student.code().value(),
        student.firstName(),
        student.lastName(),
        student.email().value());
  }

  /**
   * findById → the caller's own full record. Optional rather than a 404 throw, unlike {@link
   * #summaryOf}: {@code me}'s id comes from the session principal, which outlives a student row a
   * Registrar deleted mid-session, and that case is the caller's to render.
   */
  @Override
  public Optional<StudentProfile> profileOf(StudentId id) {
    return repository
        .findById(id)
        .map(student -> new StudentProfile(
            student.code().value(),
            student.firstName(),
            student.lastName(),
            student.email().value(),
            student.dateOfBirth().value()));
  }

  private StudentSummaryView toSummaryView(Student student) {
    return new StudentSummaryView(
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
      String studentCode,
      String firstName,
      String lastName,
      String email,
      LocalDate dateOfBirth,
      Instant createdAt,
      Instant updatedAt) {}

  /** Same VO-unwrapping rationale as {@link ProvisionedStudent}, for {@link #viewInitialPassword}. */
  public record InitialPassword(String username, String initialPassword) {}

  /** Same VO-unwrapping rationale as {@link ProvisionedStudent}, for one {@link #search} result. */
  public record StudentSummaryView(
      String studentCode, String firstName, String lastName, String email) {}

  /** Same VO-unwrapping rationale as {@link ProvisionedStudent}, for {@link #getDetail}'s result. */
  public record StudentDetailView(
      String studentCode,
      String firstName,
      String lastName,
      String email,
      LocalDate dateOfBirth,
      Instant createdAt,
      Instant updatedAt) {}
}
