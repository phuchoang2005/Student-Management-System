package org.phuchoang.management.student.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.identity.AccountProvisioning;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.DuplicateEmailException;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.application.command.RegisterStudentCommand;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.domain.StudentCode;
import org.phuchoang.management.student.port.StudentRepository;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

  @Mock private StudentRepository repository;
  @Mock private AccountProvisioning accountProvisioning;

  private StudentService service;

  private final RegisterStudentCommand command =
      new RegisterStudentCommand("S00123", "Jane", "Doe", "jane.doe@example.edu", LocalDate.of(2000, 1, 1));

  @Test
  void registerRejectsDuplicateCodeBeforeCheckingEmailOrProvisioning() {
    service = new StudentService(repository, accountProvisioning);
    when(repository.existsByCode(new StudentCode("S00123"))).thenReturn(true);

    assertThatThrownBy(() -> service.register(command)).isInstanceOf(DuplicateCodeException.class);

    verifyNoInteractions(accountProvisioning);
  }

  @Test
  void registerRejectsDuplicateEmail() {
    service = new StudentService(repository, accountProvisioning);
    when(repository.existsByCode(any())).thenReturn(false);
    when(repository.existsByEmail(any())).thenReturn(true);

    assertThatThrownBy(() -> service.register(command)).isInstanceOf(DuplicateEmailException.class);

    verifyNoInteractions(accountProvisioning);
  }

  @Test
  void registerSavesStudentAndProvisionsAccountInOrder() {
    service = new StudentService(repository, accountProvisioning);
    when(repository.existsByCode(any())).thenReturn(false);
    when(repository.existsByEmail(any())).thenReturn(false);
    when(repository.save(any(Student.class)))
        .thenAnswer(
            invocation -> {
              Student toSave = invocation.getArgument(0);
              return Student.reconstitute(
                  new StudentId(1L),
                  toSave.code(),
                  toSave.firstName(),
                  toSave.lastName(),
                  toSave.email(),
                  toSave.dateOfBirth(),
                  toSave.createdAt(),
                  toSave.updatedAt(),
                  toSave.version());
            });
    when(accountProvisioning.provisionForStudent(any(), any()))
        .thenReturn(new ProvisionedAccount("jane.doe@example.edu", "aB3xY9zQ"));

    StudentService.ProvisionedStudent result = service.register(command);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.username()).isEqualTo("jane.doe@example.edu");
    assertThat(result.initialPassword()).isEqualTo("aB3xY9zQ");
  }
}
