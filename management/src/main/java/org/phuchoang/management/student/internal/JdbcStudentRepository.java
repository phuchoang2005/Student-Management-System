package org.phuchoang.management.student.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.phuchoang.management.shared.exception.StaleWriteException;
import org.phuchoang.management.shared.paging.CursorCodec;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.student.StudentCode;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.domain.DateOfBirth;
import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.port.StudentRepository;
import org.springframework.dao.OptimisticLockingFailureException;
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
  public List<Student> findByIds(Collection<StudentId> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    List<Long> rawIds = ids.stream().map(StudentId::value).toList();
    List<Student> students = new ArrayList<>();
    springRepo.findAllById(rawIds).forEach(row -> students.add(toDomain(row)));
    return students;
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
  public boolean existsByEmailExcludingCode(Email email, StudentCode excluding) {
    return springRepo.existsByEmailAndStudentCodeNot(email.value(), excluding.value());
  }

  @Override
  public CursorPage<Student> search(String query, StudentId scopeToId, String afterKey, int limit) {
    Long scopeId = scopeToId == null ? null : scopeToId.value();
    String booleanQuery = toBooleanModeQuery(query);
    List<StudentRow> rows = springRepo.search(booleanQuery, scopeId, afterKey, limit + 1);

    boolean hasMore = rows.size() > limit;
    List<Student> content =
        (hasMore ? rows.subList(0, limit) : rows).stream().map(this::toDomain).toList();

    String nextCursor =
        hasMore && !content.isEmpty()
            ? CursorCodec.encode(content.get(content.size() - 1).code().value())
            : null;
    return new CursorPage<>(content, nextCursor);
  }

  /**
   * Builds a MySQL boolean-mode FULLTEXT expression requiring every token in the raw query as a
   * prefix match, mirroring how the built-in FULLTEXT parser tokenizes indexed text on
   * non-alphanumeric boundaries. Naively gluing one trailing {@code '*'} onto the raw string
   * breaks on any query containing its own word separators -- e.g. an email like
   * "paging-scope-0@example.edu" indexes as five separate tokens, and searching for one glued
   * "pagingscope0exampleedu*" token matches none of them; and a plain multi-word {@code AGAINST}
   * with no {@code +} defaults to OR, so "Jane Doe" would match any row containing just "Doe".
   * Splitting on non-alphanumeric characters and requiring ({@code +}) each resulting token as a
   * prefix ({@code *}) fixes both. Tokens under {@code innodb_ft_min_token_size}'s default of 3
   * characters are dropped rather than required: MySQL never indexes them at all, so requiring one
   * as a mandatory term would make the whole query unsatisfiable (e.g. an ISBN's single-digit
   * segments). {@code null}/blank pass through untouched -- the query itself already treats either
   * as "no filter" via its {@code :query IS NULL OR :query = ''} clause.
   */
  private static String toBooleanModeQuery(String raw) {
    if (raw == null || raw.isBlank()) {
      return raw;
    }
    StringBuilder booleanQuery = new StringBuilder();
    for (String token : raw.split("[^\\p{Alnum}]+")) {
      if (token.length() < 3) {
        continue;
      }
      if (booleanQuery.length() > 0) {
        booleanQuery.append(' ');
      }
      booleanQuery.append('+').append(token).append('*');
    }
    return booleanQuery.isEmpty() ? null : booleanQuery.toString();
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
