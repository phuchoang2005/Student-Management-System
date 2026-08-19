package org.phuchoang.management.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit coverage of the {@code User} aggregate's two state transitions (06-low-level-design.md §8.2). */
class UserTest {

  private static User aFreshlyProvisionedAccount() {
    return User.provisionForStudent(
        new Username("jane.doe@example.edu"),
        1L,
        new PasswordHash("$2a$10$initialhash"),
        new EncryptedInitialPassword("base64ciphertext"));
  }

  @Test
  void provisionForStudentStartsInTheMustChangePasswordStateWithARecoverableInitialPassword() {
    // Identity.1, Identity.3, Identity.5
    User user = aFreshlyProvisionedAccount();

    assertThat(user.role()).isEqualTo(Role.STUDENT);
    assertThat(user.studentId()).isEqualTo(1L);
    assertThat(user.mustChangePassword()).isTrue();
    assertThat(user.initialPasswordEncrypted().cipherText()).isEqualTo("base64ciphertext");
    assertThat(user.version()).isZero();
  }

  @Test
  void changePasswordReplacesTheHashClearsTheInitialPasswordAndClearsTheGate() {
    // Identity.3, Identity.4, Identity.5 — TC-IDN-014
    User user = aFreshlyProvisionedAccount();

    user.changePassword(new PasswordHash("$2a$10$newhash"));

    assertThat(user.passwordHash().value()).isEqualTo("$2a$10$newhash");
    assertThat(user.initialPasswordEncrypted()).isNull();
    assertThat(user.mustChangePassword()).isFalse();
  }

  @Test
  void renameUsernameKeepsEveryOtherFieldIntact() {
    // req.md §3 — US-1.2's email sync must not disturb the must-change-password state
    User user = aFreshlyProvisionedAccount();

    user.renameUsername(new Username("jane.new@example.edu"));

    assertThat(user.username().value()).isEqualTo("jane.new@example.edu");
    assertThat(user.mustChangePassword()).isTrue();
    assertThat(user.initialPasswordEncrypted()).isNotNull();
  }
}
