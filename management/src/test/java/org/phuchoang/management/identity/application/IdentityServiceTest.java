package org.phuchoang.management.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.identity.InitialPasswordView;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.identity.application.command.ChangePasswordCommand;
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
import org.phuchoang.management.shared.exception.InvalidCredentialsException;
import org.phuchoang.management.shared.security.AuthenticatedPrincipal;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

  @Mock private UserRepository repository;
  @Mock private PasswordHasher hasher;
  @Mock private PasswordCipher cipher;
  @Mock private InitialPasswordGenerator passwordGenerator;

  private IdentityService service;

  @BeforeEach
  void setUp() {
    service = new IdentityService(repository, hasher, cipher, passwordGenerator);
  }

  private static User anAccountOnItsInitialPassword() {
    return User.reconstitute(
        new UserId(1L),
        new Username("jane.doe@example.edu"),
        new PasswordHash("$2a$10$hashedvalue"),
        new EncryptedInitialPassword("base64ciphertext"),
        Role.STUDENT,
        1L,
        true,
        0L);
  }

  @Test
  void provisionsAStudentAccountWithUsernameEqualToEmail() {
    when(passwordGenerator.generate()).thenReturn("aB3xY9zQ");
    when(hasher.hash("aB3xY9zQ")).thenReturn(new PasswordHash("$2a$10$hashedvalue"));
    when(cipher.encrypt("aB3xY9zQ")).thenReturn(new EncryptedInitialPassword("base64ciphertext"));
    when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProvisionedAccount account = service.provisionForStudent(1L, "jane.doe@example.edu");

    assertThat(account.username()).isEqualTo("jane.doe@example.edu");
    assertThat(account.plaintextPassword()).isEqualTo("aB3xY9zQ");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(repository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.username().value()).isEqualTo("jane.doe@example.edu");
    assertThat(saved.studentId()).isEqualTo(1L);
    assertThat(saved.role()).isEqualTo(Role.STUDENT);
    assertThat(saved.mustChangePassword()).isTrue();
    assertThat(saved.passwordHash().value()).isEqualTo("$2a$10$hashedvalue");
    assertThat(saved.initialPasswordEncrypted().cipherText()).isEqualTo("base64ciphertext");
  }

  @Test
  void renameUsernameForStudentUpdatesTheLinkedAccountsUsername() {
    when(repository.findByStudentId(1L)).thenReturn(Optional.of(anAccountOnItsInitialPassword()));
    when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.renameUsernameForStudent(1L, "jane.new@example.edu");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().username().value()).isEqualTo("jane.new@example.edu");
  }

  @Test
  void renameUsernameForStudentFailsFastWhenNoAccountExists() {
    when(repository.findByStudentId(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.renameUsernameForStudent(99L, "jane.new@example.edu"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void changePasswordRehashesAndClearsTheInitialPassword() {
    // TC-IDN-006, TC-IDN-014
    User user = anAccountOnItsInitialPassword();
    when(repository.findByUsername(new Username("jane.doe@example.edu"))).thenReturn(Optional.of(user));
    when(hasher.matches("aB3xY9zQ", user.passwordHash())).thenReturn(true);
    when(hasher.hash("newSecret1")).thenReturn(new PasswordHash("$2a$10$newhash"));
    when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.changePassword(
        "jane.doe@example.edu", new ChangePasswordCommand("aB3xY9zQ", "newSecret1", "newSecret1"));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(repository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.passwordHash().value()).isEqualTo("$2a$10$newhash");
    assertThat(saved.initialPasswordEncrypted()).isNull();
    assertThat(saved.mustChangePassword()).isFalse();
  }

  @Test
  void changePasswordRejectsAMismatchedRetypeBeforeTouchingTheRepository() {
    // TC-IDN-007 — the retype check runs first, so a wrong current password can't be probed
    // through a mismatched retype (04-authentication-authorization.md §5.1).
    assertThatThrownBy(
            () ->
                service.changePassword(
                    "jane.doe@example.edu",
                    new ChangePasswordCommand("aB3xY9zQ", "newSecret1", "newSecret2")))
        .isInstanceOf(DomainValidationException.class);

    verify(repository, never()).save(any(User.class));
  }

  @Test
  void changePasswordRejectsAWrongCurrentPassword() {
    // TC-IDN-008
    User user = anAccountOnItsInitialPassword();
    when(repository.findByUsername(new Username("jane.doe@example.edu"))).thenReturn(Optional.of(user));
    when(hasher.matches("wrong", user.passwordHash())).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.changePassword(
                    "jane.doe@example.edu",
                    new ChangePasswordCommand("wrong", "newSecret1", "newSecret1")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(repository, never()).save(any(User.class));
  }

  @Test
  void changePasswordRejectsANewPasswordBelowTheMinimumLength() {
    // TC-IDN-009 — 7 characters
    User user = anAccountOnItsInitialPassword();
    when(repository.findByUsername(new Username("jane.doe@example.edu"))).thenReturn(Optional.of(user));
    when(hasher.matches("aB3xY9zQ", user.passwordHash())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.changePassword(
                    "jane.doe@example.edu",
                    new ChangePasswordCommand("aB3xY9zQ", "short12", "short12")))
        .isInstanceOf(DomainValidationException.class);

    verify(repository, never()).save(any(User.class));
  }

  @Test
  void changePasswordRejectsANewPasswordAboveTheBcryptLimit() {
    // TC-IDN-012 — 73 characters
    String tooLong = "a".repeat(73);
    User user = anAccountOnItsInitialPassword();
    when(repository.findByUsername(new Username("jane.doe@example.edu"))).thenReturn(Optional.of(user));
    when(hasher.matches("aB3xY9zQ", user.passwordHash())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.changePassword(
                    "jane.doe@example.edu",
                    new ChangePasswordCommand("aB3xY9zQ", tooLong, tooLong)))
        .isInstanceOf(DomainValidationException.class);

    verify(repository, never()).save(any(User.class));
  }

  @Test
  void changePasswordRejectsANewPasswordIdenticalToTheCurrentOne() {
    // TC-IDN-013 — otherwise a no-op "change" would still clear the must-change-password gate.
    User user = anAccountOnItsInitialPassword();
    when(repository.findByUsername(new Username("jane.doe@example.edu"))).thenReturn(Optional.of(user));
    when(hasher.matches("aB3xY9zQ", user.passwordHash())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.changePassword(
                    "jane.doe@example.edu",
                    new ChangePasswordCommand("aB3xY9zQ", "aB3xY9zQ", "aB3xY9zQ")))
        .isInstanceOf(DomainValidationException.class);

    verify(repository, never()).save(any(User.class));
  }

  @Test
  void viewInitialPasswordDecryptsWhileTheAccountIsStillOnItsIssuedPassword() {
    // TC-IDN-016
    when(repository.findByStudentId(1L)).thenReturn(Optional.of(anAccountOnItsInitialPassword()));
    when(cipher.decrypt(new EncryptedInitialPassword("base64ciphertext"))).thenReturn("aB3xY9zQ");

    Optional<InitialPasswordView> view = service.viewInitialPassword(1L);

    assertThat(view).contains(new InitialPasswordView("jane.doe@example.edu", "aB3xY9zQ"));
  }

  @Test
  void viewInitialPasswordIsEmptyOnceThePasswordHasBeenChanged() {
    // TC-IDN-017
    User user = anAccountOnItsInitialPassword();
    user.changePassword(new PasswordHash("$2a$10$newhash"));
    when(repository.findByStudentId(1L)).thenReturn(Optional.of(user));

    assertThat(service.viewInitialPassword(1L)).isEmpty();
  }

  @Test
  void viewInitialPasswordIsEmptyWhenTheStudentHasNoAccount() {
    when(repository.findByStudentId(99L)).thenReturn(Optional.empty());

    assertThat(service.viewInitialPassword(99L)).isEmpty();
  }

  @Test
  void loadPrincipalUnwrapsTheAggregateIntoTheSecurityPrincipal() {
    when(repository.findByUsername(new Username("jane.doe@example.edu")))
        .thenReturn(Optional.of(anAccountOnItsInitialPassword()));

    AuthenticatedPrincipal principal = service.loadPrincipal("jane.doe@example.edu").orElseThrow();

    assertThat(principal.getUsername()).isEqualTo("jane.doe@example.edu");
    assertThat(principal.getPassword()).isEqualTo("$2a$10$hashedvalue");
    assertThat(principal.role()).isEqualTo("STUDENT");
    assertThat(principal.studentId()).isEqualTo(1L);
    assertThat(principal.mustChangePassword()).isTrue();
    assertThat(principal.getAuthorities()).singleElement().hasToString("ROLE_STUDENT");
  }

  @Test
  void loadPrincipalIsEmptyForAnUnknownUsername() {
    when(repository.findByUsername(new Username("nobody@example.edu"))).thenReturn(Optional.empty());

    assertThat(service.loadPrincipal("nobody@example.edu")).isEmpty();
  }
}
