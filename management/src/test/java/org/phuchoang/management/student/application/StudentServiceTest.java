package org.phuchoang.management.student.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.identity.AccountProvisioning;
import org.phuchoang.management.identity.ProvisionedAccount;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.DuplicateEmailException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.student.StudentDeleted;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentSummary;
import org.phuchoang.management.student.application.command.RegisterStudentCommand;
import org.phuchoang.management.student.application.command.UpdateStudentCommand;
import org.phuchoang.management.student.domain.DateOfBirth;
import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.domain.StudentCode;
import org.phuchoang.management.student.port.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

  @Mock private StudentRepository repository;
  @Mock private AccountProvisioning accountProvisioning;
  @Mock private ApplicationEventPublisher events;

  private StudentService service;

  private final RegisterStudentCommand command =
      new RegisterStudentCommand("S00123", "Jane", "Doe", "jane.doe@example.edu", LocalDate.of(2000, 1, 1));

  @Test
  void registerRejectsDuplicateCodeBeforeCheckingEmailOrProvisioning() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.existsByCode(new StudentCode("S00123"))).thenReturn(true);

    assertThatThrownBy(() -> service.register(command)).isInstanceOf(DuplicateCodeException.class);

    verifyNoInteractions(accountProvisioning);
  }

  @Test
  void registerRejectsDuplicateEmail() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.existsByCode(any())).thenReturn(false);
    when(repository.existsByEmail(any())).thenReturn(true);

    assertThatThrownBy(() -> service.register(command)).isInstanceOf(DuplicateEmailException.class);

    verifyNoInteractions(accountProvisioning);
  }

  @Test
  void registerSavesStudentAndProvisionsAccountInOrder() {
    service = new StudentService(repository, accountProvisioning, events);
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

  private final StudentCode existingCode = new StudentCode("S00123");
  private final Student existingStudent =
      Student.reconstitute(
          new StudentId(1L),
          existingCode,
          "Jane",
          "Doe",
          new Email("jane.doe@example.edu"),
          new DateOfBirth(LocalDate.of(2000, 1, 1)),
          Instant.parse("2024-01-01T00:00:00Z"),
          Instant.parse("2024-01-01T00:00:00Z"),
          0L);

  @Test
  void updateThrowsNotFoundWhenStudentDoesNotExist() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.empty());
    UpdateStudentCommand update =
        new UpdateStudentCommand("Jane", "Doe", "jane.doe@example.edu", LocalDate.of(2000, 1, 1));

    assertThatThrownBy(() -> service.update("S00123", update)).isInstanceOf(NotFoundException.class);

    verifyNoInteractions(accountProvisioning);
  }

  @Test
  void updateRejectsEmailThatCollidesWithAnotherStudent() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingStudent));
    when(repository.existsByEmailExcludingCode(new Email("taken@example.edu"), existingCode)).thenReturn(true);
    UpdateStudentCommand update =
        new UpdateStudentCommand("Jane", "Doe", "taken@example.edu", LocalDate.of(2000, 1, 1));

    assertThatThrownBy(() -> service.update("S00123", update)).isInstanceOf(DuplicateEmailException.class);

    verifyNoInteractions(accountProvisioning);
  }

  @Test
  void updateRejectsBlankLastName() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingStudent));
    UpdateStudentCommand update =
        new UpdateStudentCommand("Jane", "", "jane.doe@example.edu", LocalDate.of(2000, 1, 1));

    assertThatThrownBy(() -> service.update("S00123", update)).isInstanceOf(DomainValidationException.class);

    verifyNoInteractions(accountProvisioning);
  }

  @Test
  void updateAppliesChangesAndSkipsUsernameRenameWhenEmailUnchanged() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingStudent));
    when(repository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));
    UpdateStudentCommand update =
        new UpdateStudentCommand("Janet", "Roe", "jane.doe@example.edu", LocalDate.of(1999, 5, 5));

    StudentService.UpdatedStudent result = service.update("S00123", update);

    assertThat(result.firstName()).isEqualTo("Janet");
    assertThat(result.lastName()).isEqualTo("Roe");
    assertThat(result.email()).isEqualTo("jane.doe@example.edu");
    verify(repository, never()).existsByEmailExcludingCode(any(), any());
    verifyNoInteractions(accountProvisioning);
  }

  @Test
  void updateAppliesChangesAndRenamesLinkedAccountUsernameWhenEmailChanged() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingStudent));
    when(repository.existsByEmailExcludingCode(new Email("jane.new@example.edu"), existingCode))
        .thenReturn(false);
    when(repository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));
    UpdateStudentCommand update =
        new UpdateStudentCommand("Jane", "Doe", "jane.new@example.edu", LocalDate.of(2000, 1, 1));

    StudentService.UpdatedStudent result = service.update("S00123", update);

    assertThat(result.email()).isEqualTo("jane.new@example.edu");
    verify(accountProvisioning).renameUsernameForStudent(1L, "jane.new@example.edu");
  }

  @Test
  void removeThrowsNotFoundWhenStudentDoesNotExist() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.remove("S00123")).isInstanceOf(NotFoundException.class);

    verify(repository, never()).deleteByCode(any());
    verifyNoInteractions(events);
  }

  @Test
  void removeDeletesTheStudentAndPublishesStudentDeleted() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingStudent));

    service.remove("S00123");

    verify(repository).deleteByCode(existingCode);
    verify(events).publishEvent(new StudentDeleted(new StudentId(1L)));
  }

  @Test
  void searchReturnsMappedSummariesFromRepositoryPage() {
    service = new StudentService(repository, accountProvisioning, events);
    Pageable pageable = PageRequest.of(0, 20);
    Page<Student> repoPage = new PageImpl<>(java.util.List.of(existingStudent), pageable, 1);
    when(repository.search("jane", pageable)).thenReturn(repoPage);

    Page<StudentService.StudentSummaryView> result = service.search("jane", pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    StudentService.StudentSummaryView summary = result.getContent().get(0);
    assertThat(summary.id()).isEqualTo(1L);
    assertThat(summary.studentCode()).isEqualTo("S00123");
    assertThat(summary.email()).isEqualTo("jane.doe@example.edu");
  }

  @Test
  void searchReturnsEmptyPageWhenNothingMatches() {
    service = new StudentService(repository, accountProvisioning, events);
    Pageable pageable = PageRequest.of(0, 20);
    when(repository.search("nobody", pageable)).thenReturn(Page.empty(pageable));

    Page<StudentService.StudentSummaryView> result = service.search("nobody", pageable);

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  void getDetailThrowsNotFoundWhenStudentDoesNotExist() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail("S00123")).isInstanceOf(NotFoundException.class);
  }

  @Test
  void getDetailReturnsStudentFieldsWithEmptyBooksAndCoursesStub() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingStudent));

    StudentService.StudentDetailView detail = service.getDetail("S00123");

    assertThat(detail.studentCode()).isEqualTo("S00123");
    assertThat(detail.firstName()).isEqualTo("Jane");
    assertThat(detail.ownedBooks()).isEmpty();
    assertThat(detail.activeCourses()).isEmpty();
  }

  @Test
  void summaryOfThrowsNotFoundWhenStudentDoesNotExist() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findById(new StudentId(99L))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.summaryOf(new StudentId(99L))).isInstanceOf(NotFoundException.class);
  }

  @Test
  void summaryOfReturnsStudentSummaryFields() {
    service = new StudentService(repository, accountProvisioning, events);
    when(repository.findById(new StudentId(1L))).thenReturn(Optional.of(existingStudent));

    StudentSummary summary = service.summaryOf(new StudentId(1L));

    assertThat(summary)
        .isEqualTo(new StudentSummary(1L, "S00123", "Jane", "Doe", "jane.doe@example.edu"));
  }
}
