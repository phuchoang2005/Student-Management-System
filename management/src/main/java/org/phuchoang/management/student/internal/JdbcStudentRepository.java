package org.phuchoang.management.student.internal;

import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.domain.DateOfBirth;
import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.domain.StudentCode;
import org.phuchoang.management.student.port.StudentRepository;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStudentRepository implements StudentRepository {

  private final SpringDataStudentRepository springRepo;

  JdbcStudentRepository(SpringDataStudentRepository springRepo) {
    this.springRepo = springRepo;
  }

  @Override
  public boolean existsByCode(StudentCode code) {
    return springRepo.existsByStudentCode(code.value());
  }

  @Override
  public boolean existsByEmail(Email email) {
    return springRepo.existsByEmail(email.value());
  }

  @Override
  public Student save(Student student) {
    return toDomain(springRepo.save(toRow(student)));
  }

  private StudentRow toRow(Student student) {
    StudentId id = student.id();
    return new StudentRow(
        id == null ? null : id.value(),
        student.code().value(),
        student.firstName(),
        student.lastName(),
        student.email().value(),
        student.dateOfBirth().value(),
        student.version(),
        student.createdAt(),
        student.updatedAt());
  }

  private Student toDomain(StudentRow row) {
    return Student.reconstitute(
        new StudentId(row.id()),
        new StudentCode(row.studentCode()),
        row.firstName(),
        row.lastName(),
        new Email(row.email()),
        new DateOfBirth(row.dateOfBirth()),
        row.createdAt(),
        row.updatedAt(),
        row.version());
  }
}
