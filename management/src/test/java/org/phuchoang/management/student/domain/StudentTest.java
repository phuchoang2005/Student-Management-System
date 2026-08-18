package org.phuchoang.management.student.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.DomainValidationException;

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
}
