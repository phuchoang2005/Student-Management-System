package org.phuchoang.management.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.identity.domain.PasswordHash;
import org.phuchoang.management.identity.domain.Role;
import org.phuchoang.management.identity.domain.User;
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
}
