package org.phuchoang.management.enrollment.application;

import java.time.Instant;
import java.util.List;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.course.CourseDeleted;
import org.phuchoang.management.course.CourseLookup;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.enrollment.EnrollmentLookup;
import org.phuchoang.management.enrollment.application.command.EnrollStudentCommand;
import org.phuchoang.management.enrollment.domain.Enrollment;
import org.phuchoang.management.enrollment.port.EnrollmentRepository;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.DuplicateEnrollmentException;
import org.phuchoang.management.shared.exception.FieldError;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.exception.UnknownCourseException;
import org.phuchoang.management.shared.exception.UnknownStudentException;
import org.phuchoang.management.student.StudentCode;
import org.phuchoang.management.student.StudentDeleted;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;
import org.phuchoang.management.student.StudentSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method here names a student by {@link StudentCode} and a course by {@link CourseCode} —
 * the surrogate {@code enrollments.student_id} FK is resolved from the code exactly once per call,
 * through {@code StudentLookup.idOf}, and never leaves this class (api-specification.md §5
 * decision #9).
 *
 * <p>An unresolvable code means one of two different things depending on what the caller is
 * addressing, and the two produce different statuses on purpose: a code supplied as a
 * <em>reference</em> ({@link #enroll}, {@link #search}) is malformed input — 400, via {@code
 * UnknownStudentException}/{@code UnknownCourseException}, matching every other unknown-FK case in
 * the API (api-specification.md §5 decision #2) — while a code that is part of the <em>address</em>
 * of one enrollment ({@link #getDetail}, {@link #end}) makes that enrollment unaddressable, which
 * is a 404 like any other missing resource.
 */
@Service
public class EnrollmentService implements EnrollmentLookup {

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
   * {@code studentLookup.idOf} → {@code courseLookup.existsByCode} → {@code
   * existsByStudentAndCourse} → {@code Enrollment.create} → save, that exact order
   * (06-low-level-design.md §7, 03-sequence-diagrams.md §5.1) — both cross-module existence checks
   * (Enrollment.2/3) run before the duplicate-enrollment check (Enrollment.1).
   *
   * <p>{@code idOf} does double duty here: it is the Enrollment.3 student-exists check <em>and</em>
   * the code→id resolution the FK needs, so the ordering above is unchanged from when this step was
   * a bare {@code existsById}.
   */
  @Transactional
  public CreatedEnrollment enroll(EnrollStudentCommand command) {
    StudentCode studentCode = new StudentCode(command.studentCode());
    StudentId studentId = studentLookup
        .idOf(studentCode)
        .orElseThrow(
            () -> new UnknownStudentException("Student '" + studentCode.value() + "' does not exist."));

    CourseCode courseCode = new CourseCode(command.courseCode());
    if (!courseLookup.existsByCode(courseCode)) {
      throw new UnknownCourseException("Course '" + courseCode.value() + "' does not exist.");
    }

    if (repository.existsByStudentAndCourse(studentId, courseCode)) {
      throw new DuplicateEnrollmentException(
          "Student '" + studentCode.value() + "' is already enrolled in course '" + courseCode.value() + "'.");
    }

    Enrollment enrollment = Enrollment.create(studentId, courseCode);
    enrollment = repository.save(enrollment);

    return new CreatedEnrollment(
        studentCode.value(), enrollment.courseCode().value(), enrollment.enrolledAt());
  }

  /**
   * Resolve the pair (404 if either side or the link is absent) → {@code
   * repository.deleteByStudentAndCourse}, mirroring {@code BookService.remove}'s
   * existsBy-then-delete shape. Enrollment.4 — removes only the link; neither the student nor the
   * course record is touched.
   */
  @Transactional
  public void end(String studentCode, String courseCode) {
    StudentId studentId = addressedStudent(studentCode, courseCode);
    CourseCode code = new CourseCode(courseCode);
    if (!repository.existsByStudentAndCourse(studentId, code)) {
      throw missingEnrollment(studentCode, courseCode);
    }

    repository.deleteByStudentAndCourse(studentId, code);
  }

  /**
   * Closes the {@code StudentService.remove} stub (06-low-level-design.md §13, US-1.3/US-4.2) — the
   * DB-level {@code ON DELETE CASCADE} on {@code enrollments.student_id} was the only cascade in
   * effect until this listener existed.
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
   *
   * <p>No caller-scoping parameter: {@code SecurityConfig} restricts every {@code GET
   * /api/v1/enrollments/**} to REGISTRAR and COURSE_ADMINISTRATOR, so no Student principal can
   * reach this method and there is no own-records check left to make.
   */
  @Transactional(readOnly = true)
  public EnrollmentDetailView getDetail(String studentCode, String courseCode) {
    StudentId studentId = addressedStudent(studentCode, courseCode);
    CourseCode code = new CourseCode(courseCode);
    Enrollment enrollment = repository
        .findByStudentAndCourse(studentId, code)
        .orElseThrow(() -> missingEnrollment(studentCode, courseCode));

    StudentSummary student = studentLookup.summaryOf(enrollment.studentId());
    CourseSummary course = courseLookup.summaryOf(enrollment.courseCode());

    return new EnrollmentDetailView(student, course, enrollment.enrolledAt());
  }

  /**
   * UC-11/UC-20's list view — every enrollment of one student, or every enrollment in one course,
   * paged. Exactly one filter must be supplied: with neither, this would be an "enumerate every
   * enrollment in the system" endpoint no use case asks for; with both, the answer is a single
   * enrollment, which {@link #getDetail} already addresses directly.
   *
   * <p>The constant side of the page is resolved once, outside the {@code map} — filtering by
   * {@code studentCode} means every row shares one {@code StudentSummary}, and by {@code
   * courseCode} one {@code CourseSummary} — so a page of 20 costs one lookup for that side rather
   * than 20 identical ones.
   */
  @Transactional(readOnly = true)
  public Page<EnrollmentDetailView> search(String studentCode, String courseCode, Pageable pageable) {
    boolean byStudent = studentCode != null && !studentCode.isBlank();
    boolean byCourse = courseCode != null && !courseCode.isBlank();
    if (byStudent == byCourse) {
      String message = "Supply exactly one of 'studentCode' or 'courseCode'.";
      throw new DomainValidationException(
          message,
          List.of(new FieldError("studentCode", message), new FieldError("courseCode", message)));
    }

    if (byStudent) {
      StudentCode code = new StudentCode(studentCode);
      StudentId studentId = studentLookup
          .idOf(code)
          .orElseThrow(() -> new UnknownStudentException("Student '" + code.value() + "' does not exist."));
      StudentSummary student = studentLookup.summaryOf(studentId);
      return repository
          .findByStudentId(studentId, pageable)
          .map(e -> new EnrollmentDetailView(student, courseLookup.summaryOf(e.courseCode()), e.enrolledAt()));
    }

    CourseCode code = new CourseCode(courseCode);
    if (!courseLookup.existsByCode(code)) {
      throw new UnknownCourseException("Course '" + code.value() + "' does not exist.");
    }
    CourseSummary course = courseLookup.summaryOf(code);
    return repository
        .findByCourseCode(code, pageable)
        .map(e -> new EnrollmentDetailView(studentLookup.summaryOf(e.studentId()), course, e.enrolledAt()));
  }

  /**
   * findByStudentId → resolve each row's course side via {@code CourseLookup.summaryOf}. Backs
   * {@code EnrollmentLookup.findByStudent} (US-5.4, {@code GET /api/v1/me/courses}), whose caller
   * already holds a {@code StudentId} from its session principal and so needs no code resolution.
   */
  @Override
  @Transactional(readOnly = true)
  public Page<CourseSummary> findByStudent(StudentId studentId, Pageable pageable) {
    return repository.findByStudentId(studentId, pageable).map(e -> courseLookup.summaryOf(e.courseCode()));
  }

  /**
   * Resolves the student half of an enrollment's address. Unlike {@link #enroll}'s resolution, an
   * unknown code is a 404 rather than a 400: the caller is naming one enrollment, and an enrollment
   * whose student does not exist cannot exist either — the same answer a valid student with no such
   * enrollment gets, so no student-existence signal leaks through a differently-shaped error.
   */
  private StudentId addressedStudent(String studentCode, String courseCode) {
    return studentLookup
        .idOf(new StudentCode(studentCode))
        .orElseThrow(() -> missingEnrollment(studentCode, courseCode));
  }

  private NotFoundException missingEnrollment(String studentCode, String courseCode) {
    return new NotFoundException(
        "No active enrollment for student '" + studentCode + "' in course '" + courseCode + "'.");
  }

  /**
   * Unwraps {@code Enrollment}'s Value Objects here rather than in {@code EnrollmentMapper} — the
   * web layer may never call a method on a Domain-layer object directly (LayeringRulesTest), only
   * the Application layer may, mirroring {@code BookService.AddedBook}. Carries {@code studentCode}
   * rather than the saved row's {@code studentId}, and no enrollment {@code id}: neither is a value
   * any endpoint accepts back.
   */
  public record CreatedEnrollment(String studentCode, String courseCode, Instant enrolledAt) {}

  /**
   * Same VO-unwrapping rationale as {@link CreatedEnrollment}, for {@link #getDetail}'s and {@link
   * #search}'s results. No {@code id} — the OpenAPI contract keys enrollment detail by the
   * student/course pair, not the surrogate id.
   */
  public record EnrollmentDetailView(StudentSummary student, CourseSummary course, Instant enrolledAt) {}
}
