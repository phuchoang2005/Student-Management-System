package org.phuchoang.management.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.phuchoang.management.shared.exception.InvalidCredentialsException;
import org.phuchoang.management.shared.exception.UserNotFoundException;
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
    assertThat(principal.isEnabled()).isTrue();
    assertThat(principal.getAuthorities()).singleElement().hasToString("ROLE_STUDENT");
  }

  @Test
  void loadPrincipalIsEmptyForAnUnknownUsername() {
    when(repository.findByUsername(new Username("nobody@example.edu"))).thenReturn(Optional.empty());

    assertThat(service.loadPrincipal("nobody@example.edu")).isEmpty();
  }

  @Test
  void loadPrincipalReportsADisabledAccountAsDisabled() {
    // Identity.7 -- feeds Spring Security's DaoAuthenticationProvider pre-auth check.
    User user = anAccountOnItsInitialPassword();
    user.setEnabled(false);
    when(repository.findByUsername(new Username("jane.doe@example.edu"))).thenReturn(Optional.of(user));

    assertThat(service.loadPrincipal("jane.doe@example.edu").orElseThrow().isEnabled()).isFalse();
  }

  @Test
  void provisionStaffCreatesAnEnabledAccountOnItsInitialPassword() {
    // TC-IDN-024
    when(passwordGenerator.generate()).thenReturn("aB3xY9zQ");
    when(hasher.hash("aB3xY9zQ")).thenReturn(new PasswordHash("$2a$10$hashedvalue"));
    when(cipher.encrypt("aB3xY9zQ")).thenReturn(new EncryptedInitialPassword("base64ciphertext"));
    when(repository.existsByUsername(new Username("new.librarian"))).thenReturn(false);
    when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    IdentityService.ProvisionedStaffAccount account =
        service.provisionStaff(new ProvisionStaffCommand("new.librarian", "LIBRARIAN"));

    assertThat(account.username()).isEqualTo("new.librarian");
    assertThat(account.role()).isEqualTo("LIBRARIAN");
    assertThat(account.plaintextPassword()).isEqualTo("aB3xY9zQ");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(repository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.role()).isEqualTo(Role.LIBRARIAN);
    assertThat(saved.studentId()).isNull();
    assertThat(saved.mustChangePassword()).isTrue();
    assertThat(saved.enabled()).isTrue();
  }

  @Test
  void provisionStaffRejectsTheSystemAdministratorRoleBeforeTouchingTheRepository() {
    // TC-IDN-026 — a System Administrator account is never created through the application.
    assertThatThrownBy(
            () -> service.provisionStaff(new ProvisionStaffCommand("new.sysadmin", "SYSTEM_ADMINISTRATOR")))
        .isInstanceOf(DomainValidationException.class);

    verify(repository, never()).existsByUsername(any());
    verify(repository, never()).save(any(User.class));
  }

  @Test
  void provisionStaffRejectsAnUnrecognizedRole() {
    assertThatThrownBy(() -> service.provisionStaff(new ProvisionStaffCommand("new.staff", "NOT_A_ROLE")))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void provisionStaffRejectsAUsernameAlreadyInUse() {
    // TC-IDN-027
    when(repository.existsByUsername(new Username("taken.username"))).thenReturn(true);

    assertThatThrownBy(
            () -> service.provisionStaff(new ProvisionStaffCommand("taken.username", "REGISTRAR")))
        .isInstanceOf(DuplicateUsernameException.class);

    verify(repository, never()).save(any(User.class));
  }

  @Test
  void setAccountEnabledDisablesAnActiveAccount() {
    // TC-IDN-028
    User user = anAccountOnItsInitialPassword();
    when(repository.findById(new UserId(1L))).thenReturn(Optional.of(user));
    when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    IdentityService.StaffAccountStatus status = service.setAccountEnabled(1L, false);

    assertThat(status.username()).isEqualTo("jane.doe@example.edu");
    assertThat(status.enabled()).isFalse();

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().enabled()).isFalse();
  }

  @Test
  void setAccountEnabledReenablesADisabledAccount() {
    // TC-IDN-029
    User user = anAccountOnItsInitialPassword();
    user.setEnabled(false);
    when(repository.findById(new UserId(1L))).thenReturn(Optional.of(user));
    when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    IdentityService.StaffAccountStatus status = service.setAccountEnabled(1L, true);

    assertThat(status.enabled()).isTrue();
  }

  @Test
  void setAccountEnabledFailsFastWhenTheAccountDoesNotExist() {
    when(repository.findById(new UserId(99L))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setAccountEnabled(99L, false))
        .isInstanceOf(UserNotFoundException.class);

    verify(repository, never()).save(any(User.class));
  }

  @Test
  void listDemoAccountsReturnsTheFiveFixedIdentitiesWithPlaintextPasswords() {
    // TC-IDN-031 — hardcoded, no repository interaction at all.
    var accounts = service.listDemoAccounts();

    assertThat(accounts).hasSize(5);
    assertThat(accounts)
        .extracting(IdentityService.DemoAccount::role)
        .containsExactlyInAnyOrder(
            "SYSTEM_ADMINISTRATOR", "REGISTRAR", "LIBRARIAN", "COURSE_ADMINISTRATOR", "STUDENT");
    assertThat(accounts).allSatisfy(account -> assertThat(account.password()).isEqualTo("Demo#12345"));
    verify(repository, never()).findByUsername(any());
  }

  @Test
  void seedDemoAccountsCreatesTheFourNonStudentIdentitiesOnly() {
    when(hasher.hash("Demo#12345")).thenReturn(new PasswordHash("$2a$10$demohash"));
    when(repository.existsByUsername(any())).thenReturn(false);
    when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.seedDemoAccounts();

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(repository, times(4)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(User::role)
        .containsExactlyInAnyOrder(Role.SYSTEM_ADMINISTRATOR, Role.REGISTRAR, Role.LIBRARIAN, Role.COURSE_ADMINISTRATOR);
    assertThat(captor.getAllValues()).allSatisfy(user -> {
      assertThat(user.mustChangePassword()).isFalse();
      assertThat(user.enabled()).isTrue();
      assertThat(user.studentId()).isNull();
    });
    verify(repository, never()).existsByUsername(new Username("demo.student"));
  }

  @Test
  void seedDemoAccountsIsIdempotent() {
    // TC-IDN-032 — a re-seed never overwrites an account that's already there.
    when(repository.existsByUsername(any())).thenReturn(true);

    service.seedDemoAccounts();

    verify(repository, never()).save(any(User.class));
  }
}
