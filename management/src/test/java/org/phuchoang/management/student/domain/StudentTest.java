package org.phuchoang.management.student.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.student.StudentCode;

class StudentTest {

  private final StudentCode code = new StudentCode("S00123");
  private final Email email = new Email("jane.doe@example.edu");
  private final DateOfBirth dob = new DateOfBirth(LocalDate.of(2000, 1, 1));

  @Test
  void registerCreatesStudentWithGeneratedTimestampsAndZeroVersion() {
    Student student = Student.register(code, "Jane", "Doe", email, dob);

    assertThat(student.id()).isNull();
    assertThat(student.code()).isEqualTo(code);
    assertThat(student.firstName()).isEqualTo("Jane");
    assertThat(student.lastName()).isEqualTo("Doe");
    assertThat(student.email()).isEqualTo(email);
    assertThat(student.dateOfBirth()).isEqualTo(dob);
    assertThat(student.createdAt()).isNotNull().isEqualTo(student.updatedAt());
    assertThat(student.version()).isZero();
  }

  @Test
  void registerRejectsBlankFirstName() {
    assertThatThrownBy(() -> Student.register(code, " ", "Doe", email, dob))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void registerRejectsBlankLastName() {
    assertThatThrownBy(() -> Student.register(code, "Jane", "", email, dob))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void applyChangesUpdatesFieldsAndRefreshesUpdatedAtOnly() {
    Student student = Student.register(code, "Jane", "Doe", email, dob);
    Instant createdAt = student.createdAt();
    Email newEmail = new Email("jane.new@example.edu");
    DateOfBirth newDob = new DateOfBirth(LocalDate.of(1999, 5, 5));

    student.applyChanges("Janet", "Roe", newEmail, newDob);

    assertThat(student.firstName()).isEqualTo("Janet");
    assertThat(student.lastName()).isEqualTo("Roe");
    assertThat(student.email()).isEqualTo(newEmail);
    assertThat(student.dateOfBirth()).isEqualTo(newDob);
    assertThat(student.code()).isEqualTo(code);
    assertThat(student.createdAt()).isEqualTo(createdAt);
    assertThat(student.updatedAt()).isAfterOrEqualTo(createdAt);
  }

  @Test
  void applyChangesRejectsBlankFirstName() {
    Student student = Student.register(code, "Jane", "Doe", email, dob);

    assertThatThrownBy(() -> student.applyChanges(" ", "Doe", email, dob))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void applyChangesRejectsBlankLastName() {
    Student student = Student.register(code, "Jane", "Doe", email, dob);

    assertThatThrownBy(() -> student.applyChanges("Jane", "", email, dob))
        .isInstanceOf(DomainValidationException.class);
  }
}
