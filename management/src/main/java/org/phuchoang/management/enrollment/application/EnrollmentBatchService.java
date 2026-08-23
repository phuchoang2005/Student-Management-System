package org.phuchoang.management.enrollment.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.phuchoang.management.enrollment.application.command.BatchEnrollStudentCommand;
import org.phuchoang.management.enrollment.application.command.EnrollStudentCommand;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.DuplicateEnrollmentException;
import org.phuchoang.management.shared.exception.UnknownCourseException;
import org.phuchoang.management.shared.exception.UnknownStudentException;
import org.phuchoang.management.student.StudentCode;
import org.phuchoang.management.student.StudentLookup;
import org.springframework.stereotype.Service;

/**
 * Enrolls one student into several courses in a single request (UC-26), reporting each course's
 * outcome separately so one rejected course does not cost the Registrar the others.
 *
 * <p><strong>Its own bean, and deliberately not {@code @Transactional}.</strong> That combination is
 * the whole design, not an oversight. {@code @Transactional} is honoured by a proxy, so a loop
 * inside {@link EnrollmentService} calling {@code this.enroll(...)} would be self-invocation: the
 * proxy is bypassed, {@code enroll}'s own annotation never applies, and every course would share one
 * transaction — exactly the all-or-nothing behaviour this endpoint exists to avoid. Calling through
 * an injected {@code EnrollmentService} reference crosses the proxy instead, so each course opens,
 * commits or rolls back a transaction of its own (propagation REQUIRED with no outer transaction to
 * join).
 *
 * <p>This is the one endpoint in the API where a request is not a single transaction. On a partial
 * failure the successful courses are already durable and are not undone — see api-specification.md
 * §5 decision #12.
 */
@Service
public class EnrollmentBatchService {

  private final EnrollmentService enrollmentService;
  private final StudentLookup studentLookup;

  public EnrollmentBatchService(EnrollmentService enrollmentService, StudentLookup studentLookup) {
    this.enrollmentService = enrollmentService;
    this.studentLookup = studentLookup;
  }

  /**
   * Verifies the student once, then walks the courses.
   *
   * <p>An unknown student is a whole-request 400, not N identical per-row failures: the student is
   * the <em>subject</em> of the request rather than one of its items, so an unknown one makes every
   * course unanswerable. That matches what the single-enrollment endpoint already does with a bad
   * student code (api-specification.md §5 decision #2).
   */
  public BatchEnrollmentResult enrollAll(BatchEnrollStudentCommand command) {
    StudentCode studentCode = new StudentCode(command.studentCode());
    studentLookup
        .idOf(studentCode)
        .orElseThrow(
            () ->
                new UnknownStudentException(
                    "Student '" + studentCode.value() + "' does not exist."));

    // Deduplicated, order preserved. Left alone, ["CS101","CS101"] would answer ENROLLED and then
    // ALREADY_ENROLLED for the same course -- accurate about what happened, but indistinguishable
    // from a bug to whoever reads it.
    List<String> courseCodes = List.copyOf(new LinkedHashSet<>(command.courseCodes()));

    List<BatchEnrollmentOutcome> outcomes = new ArrayList<>(courseCodes.size());
    for (String courseCode : courseCodes) {
      outcomes.add(enrollOne(studentCode.value(), courseCode));
    }
    return new BatchEnrollmentResult(studentCode.value(), outcomes);
  }

  /**
   * One course, one transaction. Each failure the single-enrollment endpoint answers with a status
   * becomes a per-course outcome here instead, because the rest of the batch is still answerable.
   */
  private BatchEnrollmentOutcome enrollOne(String studentCode, String courseCode) {
    try {
      EnrollmentService.CreatedEnrollment created =
          enrollmentService.enroll(new EnrollStudentCommand(studentCode, courseCode));
      return BatchEnrollmentOutcome.enrolled(courseCode, created.enrolledAt());
    } catch (UnknownCourseException e) {
      return BatchEnrollmentOutcome.failed(courseCode, Outcome.UNKNOWN_COURSE, e.getMessage());
    } catch (DuplicateEnrollmentException e) {
      return BatchEnrollmentOutcome.failed(courseCode, Outcome.ALREADY_ENROLLED, e.getMessage());
    } catch (DomainValidationException e) {
      // CourseCode's own constructor rejects blank/over-length values. Bean Validation on the
      // request already catches both, so this only fires if those two rules ever drift apart --
      // per-course rather than a whole-request 400, since the other codes remain answerable.
      return BatchEnrollmentOutcome.failed(courseCode, Outcome.INVALID_COURSE_CODE, e.getMessage());
    }
    // UnknownStudentException is deliberately not caught: reaching it means the student was deleted
    // between the check above and this row, which invalidates the request's precondition rather
    // than this one course, so it propagates as the same 400 enrollAll would have thrown.
  }

  /** What happened to one course. {@code ENROLLED} is the only success. */
  public enum Outcome {
    ENROLLED,
    UNKNOWN_COURSE,
    ALREADY_ENROLLED,
    INVALID_COURSE_CODE
  }

  /** Same VO-unwrapping rationale as {@code EnrollmentService.CreatedEnrollment}. */
  public record BatchEnrollmentOutcome(
      String courseCode, Outcome outcome, Instant enrolledAt, String message) {

    static BatchEnrollmentOutcome enrolled(String courseCode, Instant enrolledAt) {
      return new BatchEnrollmentOutcome(courseCode, Outcome.ENROLLED, enrolledAt, null);
    }

    static BatchEnrollmentOutcome failed(String courseCode, Outcome outcome, String message) {
      return new BatchEnrollmentOutcome(courseCode, outcome, null, message);
    }

    public boolean succeeded() {
      return outcome == Outcome.ENROLLED;
    }
  }

  /** {@code outcomes} is in request order, minus duplicates. */
  public record BatchEnrollmentResult(String studentCode, List<BatchEnrollmentOutcome> outcomes) {

    public int enrolledCount() {
      return (int) outcomes.stream().filter(BatchEnrollmentOutcome::succeeded).count();
    }

    public int failedCount() {
      return outcomes.size() - enrolledCount();
    }
  }
}
