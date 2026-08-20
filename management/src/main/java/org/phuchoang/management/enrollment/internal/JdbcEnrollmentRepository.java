package org.phuchoang.management.enrollment.internal;

import java.util.List;
import java.util.Optional;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.enrollment.domain.Enrollment;
import org.phuchoang.management.enrollment.domain.EnrollmentId;
import org.phuchoang.management.enrollment.port.EnrollmentRepository;
import org.phuchoang.management.student.StudentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
  public Page<Enrollment> findByStudentId(StudentId studentId, Pageable pageable) {
    List<Enrollment> content =
        springRepo.findByStudentId(studentId.value(), pageable.getPageSize(), pageable.getOffset()).stream()
            .map(this::toDomain)
            .toList();
    long total = springRepo.countByStudentId(studentId.value());
    return new PageImpl<>(content, pageable, total);
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
