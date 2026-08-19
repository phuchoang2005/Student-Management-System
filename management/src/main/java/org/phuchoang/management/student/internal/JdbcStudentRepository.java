package org.phuchoang.management.student.internal;

import java.util.List;
import java.util.Optional;
import org.phuchoang.management.shared.exception.StaleWriteException;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.domain.DateOfBirth;
import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.domain.StudentCode;
import org.phuchoang.management.student.port.StudentRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStudentRepository implements StudentRepository {

  private final SpringDataStudentRepository springRepo;

  JdbcStudentRepository(SpringDataStudentRepository springRepo) {
    this.springRepo = springRepo;
  }

  @Override
  public Optional<Student> findByCode(StudentCode code) {
    return springRepo.findByStudentCode(code.value()).map(this::toDomain);
  }

  @Override
  public Optional<Student> findById(StudentId id) {
    return springRepo.findById(id.value()).map(this::toDomain);
  }

  @Override
  public boolean existsByCode(StudentCode code) {
    return springRepo.existsByStudentCode(code.value());
  }

  @Override
  public boolean existsById(StudentId id) {
    return springRepo.existsById(id.value());
  }

  @Override
  public boolean existsByEmail(Email email) {
    return springRepo.existsByEmail(email.value());
  }

  @Override
  public boolean existsByEmailExcludingCode(Email email, StudentCode excluding) {
    return springRepo.existsByEmailAndStudentCodeNot(email.value(), excluding.value());
  }

  @Override
  public Page<Student> search(String query, Pageable pageable) {
    List<Student> content =
        springRepo.search(query, pageable.getPageSize(), pageable.getOffset()).stream()
            .map(this::toDomain)
            .toList();
    long total = springRepo.countBySearch(query);
    return new PageImpl<>(content, pageable, total);
  }

  @Override
  public Student save(Student student) {
    try {
      return toDomain(springRepo.save(toRow(student)));
    } catch (OptimisticLockingFailureException e) {
      throw new StaleWriteException("Student " + student.code().value() + " was modified concurrently");
    }
  }

  @Override
  public void deleteByCode(StudentCode code) {
    springRepo.deleteByStudentCode(code.value());
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
