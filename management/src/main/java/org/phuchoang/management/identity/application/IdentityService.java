package org.phuchoang.management.identity.application;

import java.util.List;
import java.util.Optional;
import org.phuchoang.management.identity.AccountProvisioning;
import org.phuchoang.management.identity.InitialPasswordLookup;
import org.phuchoang.management.identity.InitialPasswordView;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.identity.application.command.ChangePasswordCommand;
import org.phuchoang.management.identity.application.command.ProvisionStaffCommand;
import org.phuchoang.management.identity.domain.EncryptedInitialPassword;
import org.phuchoang.management.identity.domain.PasswordHash;
import org.phuchoang.management.identity.domain.Role;
import org.phuchoang.management.identity.domain.User;
import org.phuchoang.management.identity.domain.UserId;
import org.phuchoang.management.identity.domain.Username;
import org.phuchoang.management.identity.port.InitialPasswordGenerator;
import org.phuchoang.management.identity.port.PasswordCipher;
import org.phuchoang.management.identity.port.PasswordHasher;
import org.phuchoang.management.identity.port.UserRepository;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.DuplicateUsernameException;
import org.phuchoang.management.shared.exception.FieldError;
import org.phuchoang.management.shared.exception.InvalidCredentialsException;
import org.phuchoang.management.shared.exception.UserNotFoundException;
import org.phuchoang.management.shared.security.AuthenticatedPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService implements AccountProvisioning, InitialPasswordLookup {

  /** 04-authentication-authorization.md §5.2 rules 1 and 5 (BCrypt truncates past 72 bytes). */
  private static final int MIN_PASSWORD_LENGTH = 8;

  private static final int MAX_PASSWORD_LENGTH = 72;

  /** PM-017, 04-authentication-authorization.md §8 — the one fixed password shared by all 5 demo identities. */
  private static final String DEMO_PASSWORD = "Demo#12345";

  /**
   * The 5 fixed demo identities, one per actor. Hardcoded rather than sourced from {@link
   * #repository}: the plaintext password has to be returned on every call, which a persisted
   * {@link User}/{@link PasswordHash} can never do (06-low-level-design.md §8.4).
   */
  private static final List<DemoAccount> DEMO_ACCOUNTS =
      List.of(
          new DemoAccount(Role.SYSTEM_ADMINISTRATOR.name(), "demo.sysadmin", DEMO_PASSWORD),
          new DemoAccount(Role.REGISTRAR.name(), "demo.registrar", DEMO_PASSWORD),
          new DemoAccount(Role.LIBRARIAN.name(), "demo.librarian", DEMO_PASSWORD),
          new DemoAccount(Role.COURSE_ADMINISTRATOR.name(), "demo.courseadmin", DEMO_PASSWORD),
          new DemoAccount(Role.STUDENT.name(), "demo.student", DEMO_PASSWORD));

  private final UserRepository repository;
  private final PasswordHasher hasher;
  private final PasswordCipher cipher;
  private final InitialPasswordGenerator passwordGenerator;

  public IdentityService(
      UserRepository repository,
      PasswordHasher hasher,
      PasswordCipher cipher,
      InitialPasswordGenerator passwordGenerator) {
    this.repository = repository;
    this.hasher = hasher;
    this.cipher = cipher;
    this.passwordGenerator = passwordGenerator;
  }

  @Override
  public ProvisionedAccount provisionForStudent(Long studentId, String email) {
    String plaintextPassword = passwordGenerator.generate();
    Username username = new Username(email);
    PasswordHash passwordHash = hasher.hash(plaintextPassword);
    EncryptedInitialPassword initialPassword = cipher.encrypt(plaintextPassword);

    User user = User.provisionForStudent(username, studentId, passwordHash, initialPassword);
    repository.save(user);

    return new ProvisionedAccount(username.value(), plaintextPassword);
  }

  @Override
  public void renameUsernameForStudent(Long studentId, String newEmail) {
    User user =
        repository
            .findByStudentId(studentId)
            .orElseThrow(() -> new IllegalStateException("No account found for student " + studentId));
    user.renameUsername(new Username(newEmail));
    repository.save(user);
  }

  /**
   * 04-authentication-authorization.md §3a, UC-24: validate role → {@code existsByUsername} →
   * generate/hash/encrypt exactly as {@link #provisionForStudent} → save. Called only from {@code
   * StaffAccountController} — never from another module, unlike {@link #provisionForStudent}.
   */
  @Transactional
  public ProvisionedStaffAccount provisionStaff(ProvisionStaffCommand command) {
    Role role = validateStaffRole(command.role());

    Username username = new Username(command.username());
    if (repository.existsByUsername(username)) {
      throw new DuplicateUsernameException("Username '" + username.value() + "' is already in use.");
    }

    String plaintextPassword = passwordGenerator.generate();
    PasswordHash passwordHash = hasher.hash(plaintextPassword);
    EncryptedInitialPassword initialPassword = cipher.encrypt(plaintextPassword);

    User user = User.provisionStaff(username, role, passwordHash, initialPassword);
    repository.save(user);

    return new ProvisionedStaffAccount(username.value(), role.name(), plaintextPassword);
  }

  /** 04-authentication-authorization.md §3b, UC-25: {@code findById} → {@code User.setEnabled} → save. */
  @Transactional
  public StaffAccountStatus setAccountEnabled(Long userId, boolean enabled) {
    User user =
        repository
            .findById(new UserId(userId))
            .orElseThrow(() -> new UserNotFoundException("Account '" + userId + "' does not exist."));

    user.setEnabled(enabled);
    repository.save(user);

    return new StaffAccountStatus(user.username().value(), user.enabled());
  }

  /**
   * 04-authentication-authorization.md §5.1, in that exact order: retype match (400) →
   * {@code findByUsername} → current-password match (401) → policy §5.2 (400) → {@code
   * User.changePassword} → save. Getting the order wrong would leak whether a guessed current
   * password was right via a retype-mismatch response.
   *
   * <p>Takes the caller's {@code username} as a parameter rather than reading {@code
   * SecurityContextHolder} itself (06-low-level-design.md §8.4's "principal resolved from
   * SecurityContext"): {@code AuthController} already has the {@code Authentication}, and keeping
   * the static lookup out of the Application layer leaves this method testable without a security
   * context.
   */
  @Transactional
  public void changePassword(String username, ChangePasswordCommand command) {
    if (!command.newPassword().equals(command.retypeNewPassword())) {
      throw new DomainValidationException(
          "Validation failed",
          List.of(new FieldError("retypeNewPassword", "must match newPassword")));
    }

    User user =
        repository
            .findByUsername(new Username(username))
            .orElseThrow(() -> new InvalidCredentialsException("currentPassword did not match."));

    if (!hasher.matches(command.currentPassword(), user.passwordHash())) {
      throw new InvalidCredentialsException("currentPassword did not match.");
    }

    validatePolicy(command.newPassword(), command.currentPassword());

    user.changePassword(hasher.hash(command.newPassword()));
    repository.save(user);
  }

  /**
   * 04-authentication-authorization.md §5.3 — the account must still be on its system-issued
   * password (Identity.5). Empty covers "already changed", "no account", and the defensive
   * "flagged unchanged but nothing stored" case alike; {@code student} renders all of them, plus
   * its own "no such student", as one indistinguishable 404 (api-specification.md §5.5).
   */
  @Override
  public Optional<InitialPasswordView> viewInitialPassword(Long studentId) {
    return repository
        .findByStudentId(studentId)
        .filter(user -> user.mustChangePassword() && user.initialPasswordEncrypted() != null)
        .map(
            user ->
                new InitialPasswordView(
                    user.username().value(), cipher.decrypt(user.initialPasswordEncrypted())));
  }

  /**
   * Backs {@code AppUserDetailsService} (06-low-level-design.md §11.3). The Web layer may not
   * touch {@code UserRepository}/{@code User} directly (LayeringRulesTest), so the aggregate is
   * unwrapped into the framework principal here — the same VO-unwrapping split {@code
   * StudentService} applies to its own view records.
   *
   * <p>{@code enabled} carries through onto the principal itself (Identity.7): {@link
   * AuthenticatedPrincipal#isEnabled()} reports it, so Spring Security's {@code
   * DaoAuthenticationProvider} rejects a disabled account with {@code DisabledException} before
   * password comparison even runs (04-authentication-authorization.md §4.1).
   */
  public Optional<AuthenticatedPrincipal> loadPrincipal(String username) {
    return repository
        .findByUsername(new Username(username))
        .map(
            user ->
                new AuthenticatedPrincipal(
                    user.username().value(),
                    user.passwordHash().value(),
                    user.role().name(),
                    user.studentId(),
                    user.mustChangePassword(),
                    user.enabled()));
  }

  /** PM-017 — the fixed list {@code DemoAccountsController} exposes at {@code GET /api/v1/auth/demo-accounts}. */
  public List<DemoAccount> listDemoAccounts() {
    return DEMO_ACCOUNTS;
  }

  /**
   * PM-017 — called once at startup by {@code DemoAccountsSeeder} (identity.web), gated behind the
   * same {@code app.demo-accounts.enabled} flag as the endpoint and controller bean itself. Only
   * the 4 non-{@code STUDENT} demo identities are seeded here: a {@code STUDENT}-role account needs
   * a real {@code students} row ({@code chk_users_student_role}'s FK co-invariant), which {@code
   * identity} cannot create without depending on the {@code student} module — a cycle, since {@code
   * student} already depends on {@code identity} via {@link org.phuchoang.management.identity.AccountProvisioning}.
   * {@code demo.student} is therefore still listed by {@link #listDemoAccounts()} but never
   * auto-seeded; logging in as it requires a matching student to be registered by hand first.
   *
   * <p>Idempotent — an already-seeded username is left untouched, so an app restart never resets a
   * password an operator or test run may since have changed.
   */
  @Transactional
  public void seedDemoAccounts() {
    DEMO_ACCOUNTS.stream()
        .filter(account -> !account.role().equals(Role.STUDENT.name()))
        .forEach(this::seedDemoAccountIfAbsent);
  }

  private void seedDemoAccountIfAbsent(DemoAccount account) {
    Username username = new Username(account.username());
    if (repository.existsByUsername(username)) {
      return;
    }
    User user = User.provisionDemo(username, Role.valueOf(account.role()), hasher.hash(account.password()));
    repository.save(user);
  }

  /** UC-24 flow 3a — role must be one of {@link Role#STAFF_ROLES}; blank/unknown values fail the same way. */
  private Role validateStaffRole(String role) {
    Role parsed;
    try {
      parsed = Role.valueOf(role);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new DomainValidationException(
          "Validation failed",
          List.of(new FieldError("role", "must be one of " + Role.STAFF_ROLES)));
    }
    if (!Role.STAFF_ROLES.contains(parsed)) {
      throw new DomainValidationException(
          "Validation failed",
          List.of(new FieldError("role", "must be one of " + Role.STAFF_ROLES)));
    }
    return parsed;
  }

  private void validatePolicy(String newPassword, String currentPassword) {
    if (newPassword.length() < MIN_PASSWORD_LENGTH || newPassword.length() > MAX_PASSWORD_LENGTH) {
      throw new DomainValidationException(
          "Validation failed",
          List.of(
              new FieldError(
                  "newPassword",
                  "must be between "
                      + MIN_PASSWORD_LENGTH
                      + " and "
                      + MAX_PASSWORD_LENGTH
                      + " characters")));
    }
    if (newPassword.equals(currentPassword)) {
      throw new DomainValidationException(
          "Validation failed",
          List.of(new FieldError("newPassword", "must differ from the current password")));
    }
  }

  /** Result of {@link #provisionStaff}: the one-time plaintext password, mirroring {@link ProvisionedAccount}. */
  public record ProvisionedStaffAccount(String username, String role, String plaintextPassword) {}

  /** Result of {@link #setAccountEnabled}. */
  public record StaffAccountStatus(String username, boolean enabled) {}

  /** One entry of {@link #listDemoAccounts}. {@code role} stays {@code String} — not the domain {@link Role} — so the Web layer can consume it without a Domain-layer dependency (LayeringRulesTest). */
  public record DemoAccount(String role, String username, String password) {}
}
