package org.phuchoang.management.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.identity.domain.PasswordHash;
import org.phuchoang.management.identity.domain.Role;
import org.phuchoang.management.identity.domain.User;
import org.phuchoang.management.identity.domain.UserId;
import org.phuchoang.management.identity.domain.Username;
import org.phuchoang.management.identity.port.InitialPasswordGenerator;
import org.phuchoang.management.identity.port.PasswordHasher;
import org.phuchoang.management.identity.port.UserRepository;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

  @Mock private UserRepository repository;
  @Mock private PasswordHasher hasher;
  @Mock private InitialPasswordGenerator passwordGenerator;

  @Test
  void provisionsAStudentAccountWithUsernameEqualToEmail() {
    IdentityService service = new IdentityService(repository, hasher, passwordGenerator);
    when(passwordGenerator.generate()).thenReturn("aB3xY9zQ");
    when(hasher.hash("aB3xY9zQ")).thenReturn(new PasswordHash("$2a$10$hashedvalue"));
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
  }

  @Test
  void renameUsernameForStudentUpdatesTheLinkedAccountsUsername() {
    IdentityService service = new IdentityService(repository, hasher, passwordGenerator);
    User existing =
        User.reconstitute(
            new UserId(1L),
            new Username("jane.doe@example.edu"),
            new PasswordHash("$2a$10$hashedvalue"),
            Role.STUDENT,
            1L,
            true,
            0L);
    when(repository.findByStudentId(1L)).thenReturn(Optional.of(existing));
    when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.renameUsernameForStudent(1L, "jane.new@example.edu");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().username().value()).isEqualTo("jane.new@example.edu");
  }

  @Test
  void renameUsernameForStudentFailsFastWhenNoAccountExists() {
    IdentityService service = new IdentityService(repository, hasher, passwordGenerator);
    when(repository.findByStudentId(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.renameUsernameForStudent(99L, "jane.new@example.edu"))
        .isInstanceOf(IllegalStateException.class);
  }
}
