package org.phuchoang.management.identity.application;

import java.util.List;
import java.util.Optional;
import org.phuchoang.management.identity.AccountProvisioning;
import org.phuchoang.management.identity.InitialPasswordLookup;
import org.phuchoang.management.identity.InitialPasswordView;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.identity.application.command.ChangePasswordCommand;
import org.phuchoang.management.identity.domain.EncryptedInitialPassword;
import org.phuchoang.management.identity.domain.PasswordHash;
import org.phuchoang.management.identity.domain.User;
import org.phuchoang.management.identity.domain.Username;
import org.phuchoang.management.identity.port.InitialPasswordGenerator;
import org.phuchoang.management.identity.port.PasswordCipher;
import org.phuchoang.management.identity.port.PasswordHasher;
import org.phuchoang.management.identity.port.UserRepository;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.FieldError;
import org.phuchoang.management.shared.exception.InvalidCredentialsException;
import org.phuchoang.management.shared.security.AuthenticatedPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService implements AccountProvisioning, InitialPasswordLookup {

  /** 04-authentication-authorization.md §5.2 rules 1 and 5 (BCrypt truncates past 72 bytes). */
  private static final int MIN_PASSWORD_LENGTH = 8;

  private static final int MAX_PASSWORD_LENGTH = 72;

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
   * <p>No {@code enabled} check yet: the column and Identity.7's deactivation rule ship with
   * US-7.2 (04-authentication-authorization.md §4.1's {@code DisabledException} branch).
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
                    user.mustChangePassword()));
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
}
