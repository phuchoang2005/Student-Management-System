package org.phuchoang.management.enrollment.application;

import java.time.Instant;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.course.CourseDeleted;
import org.phuchoang.management.course.CourseLookup;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.enrollment.application.command.EnrollStudentCommand;
import org.phuchoang.management.enrollment.domain.Enrollment;
import org.phuchoang.management.enrollment.port.EnrollmentRepository;
import org.phuchoang.management.shared.exception.DuplicateEnrollmentException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.exception.UnknownCourseException;
import org.phuchoang.management.shared.exception.UnknownStudentException;
import org.phuchoang.management.student.StudentDeleted;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;
import org.phuchoang.management.student.StudentSummary;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentService {

  private final EnrollmentRepository repository;
  private final StudentLookup studentLookup;
  private final CourseLookup courseLookup;

  public EnrollmentService(
      EnrollmentRepository repository, StudentLookup studentLookup, CourseLookup courseLookup) {
    this.repository = repository;
    this.studentLookup = studentLookup;
    this.courseLookup = courseLookup;
  }

  /**
   * {@code studentLookup.existsById} → {@code courseLookup.existsByCode} → {@code
   * existsByStudentAndCourse} → {@code Enrollment.create} → save, that exact order
   * (06-low-level-design.md §7, 03-sequence-diagrams.md §5.1) — both cross-module existence
   * checks (Enrollment.2/3) run before the duplicate-enrollment check (Enrollment.1).
   */
  @Transactional
  public CreatedEnrollment enroll(EnrollStudentCommand command) {
    StudentId studentId = new StudentId(command.studentId());
    if (!studentLookup.existsById(studentId)) {
      throw new UnknownStudentException("Student '" + studentId.value() + "' does not exist.");
    }

    CourseCode courseCode = new CourseCode(command.courseCode());
    if (!courseLookup.existsByCode(courseCode)) {
      throw new UnknownCourseException("Course '" + courseCode.value() + "' does not exist.");
    }

    if (repository.existsByStudentAndCourse(studentId, courseCode)) {
      throw new DuplicateEnrollmentException(
          "Student " + studentId.value() + " is already enrolled in course '" + courseCode.value() + "'.");
    }

    Enrollment enrollment = Enrollment.create(studentId, courseCode);
    enrollment = repository.save(enrollment);

    return new CreatedEnrollment(
        enrollment.id().value(),
        enrollment.studentId().value(),
        enrollment.courseCode().value(),
        enrollment.enrolledAt());
  }

  /**
   * existsByStudentAndCourse (404 if absent) → {@code repository.deleteByStudentAndCourse},
   * mirroring {@code BookService.remove}'s existsBy-then-delete shape. Enrollment.4 — removes
   * only the link; neither the student nor the course record is touched.
   */
  @Transactional
  public void end(Long studentId, String courseCode) {
    StudentId id = new StudentId(studentId);
    CourseCode code = new CourseCode(courseCode);
    if (!repository.existsByStudentAndCourse(id, code)) {
      throw new NotFoundException(
          "No active enrollment for student " + studentId + " in course '" + courseCode + "'.");
    }

    repository.deleteByStudentAndCourse(id, code);
  }

  /**
   * Closes the {@code StudentService.remove} stub (06-low-level-design.md §13, US-1.3/US-4.2) —
   * the DB-level {@code ON DELETE CASCADE} on {@code enrollments.student_id} was the only cascade
   * in effect until this listener existed.
   */
  @ApplicationModuleListener
  void onStudentDeleted(StudentDeleted event) {
    repository.deleteByStudentId(event.studentId());
  }

  /** Closes the {@code CourseService.remove} stub (06-low-level-design.md §13, US-3.3/US-4.2). */
  @ApplicationModuleListener
  void onCourseDeleted(CourseDeleted event) {
    repository.deleteByCourseCode(event.courseCode());
  }

  /**
   * findByStudentAndCourse (404 if the enrollment ended since it was listed — Enrollment.4) →
   * resolve both sides' summaries via {@code StudentLookup}/{@code CourseLookup}, mirroring {@code
   * BookService.getDetail}'s findByIsbn-then-compose shape (06-low-level-design.md §7, UC-20).
   */
  @Transactional(readOnly = true)
  public EnrollmentDetailView getDetail(Long studentId, String courseCode) {
    StudentId id = new StudentId(studentId);
    CourseCode code = new CourseCode(courseCode);
    Enrollment enrollment =
        repository
            .findByStudentAndCourse(id, code)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "No active enrollment for student " + studentId + " in course '" + courseCode + "'."));

    StudentSummary student = studentLookup.summaryOf(enrollment.studentId());
    CourseSummary course = courseLookup.summaryOf(enrollment.courseCode());

    return new EnrollmentDetailView(student, course, enrollment.enrolledAt());
  }

  /**
   * Unwraps {@code Enrollment}'s Value Objects here rather than in {@code EnrollmentMapper} — the
   * web layer may never call a method on a Domain-layer object directly (LayeringRulesTest), only
   * the Application layer may, mirroring {@code BookService.AddedBook}.
   */
  public record CreatedEnrollment(Long id, Long studentId, String courseCode, Instant enrolledAt) {}

  /** Same VO-unwrapping rationale as {@link CreatedEnrollment}, for {@link #getDetail}'s result. No {@code id} — the OpenAPI contract keys enrollment detail by the student/course pair, not the surrogate id. */
  public record EnrollmentDetailView(StudentSummary student, CourseSummary course, Instant enrolledAt) {}
}
