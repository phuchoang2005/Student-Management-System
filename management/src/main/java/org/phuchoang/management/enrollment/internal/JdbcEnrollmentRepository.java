package org.phuchoang.management.enrollment.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.enrollment.domain.Enrollment;
import org.phuchoang.management.enrollment.domain.EnrollmentId;
import org.phuchoang.management.enrollment.port.EnrollmentRepository;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.paging.CursorCodec;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.student.StudentId;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEnrollmentRepository implements EnrollmentRepository {

  private final SpringDataEnrollmentRepository springRepo;

  JdbcEnrollmentRepository(SpringDataEnrollmentRepository springRepo) {
    this.springRepo = springRepo;
  }

  @Override
  public boolean existsByStudentAndCourse(StudentId studentId, CourseCode courseCode) {
    return springRepo.countByStudentIdAndCourseCode(studentId.value(), courseCode.value()) > 0;
  }

  @Override
  public Optional<Enrollment> findByStudentAndCourse(StudentId studentId, CourseCode courseCode) {
    return springRepo
        .findByStudentIdAndCourseCode(studentId.value(), courseCode.value())
        .map(row -> toDomain(row, courseCode));
  }

  @Override
  public Enrollment save(Enrollment enrollment) {
    Long courseId = springRepo.findCourseIdByCode(enrollment.courseCode().value());
    EnrollmentRow saved = springRepo.save(toRow(enrollment, courseId));
    return toDomain(saved, enrollment.courseCode());
  }

  @Override
  public CursorPage<Enrollment> findByStudentId(StudentId studentId, String afterKey, int limit) {
    Cursor after = Cursor.parse(afterKey);
    List<Enrollment> rows =
        springRepo
            .findByStudentId(
                studentId.value(),
                after == null ? null : after.enrolledAt(),
                after == null ? null : after.id(),
                limit + 1)
            .stream()
            .map(this::toDomain)
            .toList();
    return toCursorPage(rows, limit);
  }

  @Override
  public CursorPage<Enrollment> findByCourseCode(CourseCode courseCode, String afterKey, int limit) {
    Cursor after = Cursor.parse(afterKey);
    List<Enrollment> rows =
        springRepo
            .findByCourseCode(
                courseCode.value(),
                after == null ? null : after.enrolledAt(),
                after == null ? null : after.id(),
                limit + 1)
            .stream()
            .map(row -> toDomain(row, courseCode))
            .toList();
    return toCursorPage(rows, limit);
  }

  private CursorPage<Enrollment> toCursorPage(List<Enrollment> rows, int limit) {
    boolean hasMore = rows.size() > limit;
    List<Enrollment> content = hasMore ? rows.subList(0, limit) : rows;
    String nextCursor = hasMore && !content.isEmpty() ? encode(content.get(content.size() - 1)) : null;
    return new CursorPage<>(content, nextCursor);
  }

  private static String encode(Enrollment last) {
    return CursorCodec.encode(last.enrolledAt().toEpochMilli() + "|" + last.id().value());
  }

  /**
   * Compound keyset cursor for enrollments — {@code enrolled_at} isn't unique, so the surrogate id
   * breaks ties (PM-045). Built/parsed entirely here rather than in {@code CursorCodec}, which
   * stays format-agnostic: student/course/book's single-string sort keys need no such format.
   *
   * <p>A plain class, not a record: {@code NamingConventionsTest} requires every record under
   * {@code internal/} to be a {@code *Row} persistence projection, which this isn't.
   */
  private static final class Cursor {
    private final Instant enrolledAt;
    private final Long id;

    private Cursor(Instant enrolledAt, Long id) {
      this.enrolledAt = enrolledAt;
      this.id = id;
    }

    Instant enrolledAt() {
      return enrolledAt;
    }

    Long id() {
      return id;
    }

    static Cursor parse(String rawKey) {
      if (rawKey == null) {
        return null;
      }
      int sep = rawKey.indexOf('|');
      if (sep < 0) {
        throw new DomainValidationException("Malformed pagination cursor.");
      }
      try {
        return new Cursor(
            Instant.ofEpochMilli(Long.parseLong(rawKey.substring(0, sep))),
            Long.parseLong(rawKey.substring(sep + 1)));
      } catch (NumberFormatException e) {
        throw new DomainValidationException("Malformed pagination cursor.");
      }
    }
  }

  @Override
  public void deleteByStudentAndCourse(StudentId studentId, CourseCode courseCode) {
    springRepo.deleteByStudentIdAndCourseCode(studentId.value(), courseCode.value());
  }

  @Override
  public void deleteByStudentId(StudentId studentId) {
    springRepo.deleteByStudentId(studentId.value());
  }

  @Override
  public void deleteByCourseCode(CourseCode courseCode) {
    springRepo.deleteByCourseCode(courseCode.value());
  }

  private EnrollmentRow toRow(Enrollment enrollment, Long courseId) {
    EnrollmentId id = enrollment.id();
    return new EnrollmentRow(
        id == null ? null : id.value(),
        enrollment.studentId().value(),
        courseId,
        enrollment.enrolledAt());
  }

  // courseCode is threaded through from the caller, not read back off the row -- EnrollmentRow
  // only carries the surrogate courseId, and re-resolving it to a code would be a second,
  // needless join right after the one save() already did.
  private Enrollment toDomain(EnrollmentRow row, CourseCode courseCode) {
    return Enrollment.reconstitute(
        new EnrollmentId(row.id()), new StudentId(row.studentId()), courseCode, row.enrolledAt());
  }

  private Enrollment toDomain(EnrollmentCourseRow row) {
    return Enrollment.reconstitute(
        new EnrollmentId(row.id()),
        new StudentId(row.studentId()),
        new CourseCode(row.courseCode()),
        row.enrolledAt());
  }
}
