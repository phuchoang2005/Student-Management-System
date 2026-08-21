package org.phuchoang.management.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.course.CourseDeleted;
import org.phuchoang.management.course.CourseLookup;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.enrollment.application.command.EnrollStudentCommand;
import org.phuchoang.management.enrollment.domain.Enrollment;
import org.phuchoang.management.enrollment.domain.EnrollmentId;
import org.phuchoang.management.enrollment.port.EnrollmentRepository;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.DuplicateEnrollmentException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.exception.UnknownCourseException;
import org.phuchoang.management.shared.exception.UnknownStudentException;
import org.phuchoang.management.student.StudentCode;
import org.phuchoang.management.student.StudentDeleted;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;
import org.phuchoang.management.student.StudentSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

  @Mock private EnrollmentRepository repository;
  @Mock private StudentLookup studentLookup;
  @Mock private CourseLookup courseLookup;

  private EnrollmentService service;

  private static final StudentCode STUDENT_CODE = new StudentCode("S00123");
  private static final StudentId STUDENT_ID = new StudentId(1L);
  private static final CourseCode COURSE_CODE = new CourseCode("CS101");

  private final EnrollStudentCommand command = new EnrollStudentCommand("S00123", "CS101");

  private void knownStudent() {
    when(studentLookup.idOf(STUDENT_CODE)).thenReturn(Optional.of(STUDENT_ID));
  }

  @Test
  void enrollRejectsUnknownStudent() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(studentLookup.idOf(STUDENT_CODE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.enroll(command)).isInstanceOf(UnknownStudentException.class);

    verify(courseLookup, never()).existsByCode(any());
  }

  @Test
  void enrollRejectsUnknownCourse() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    knownStudent();
    when(courseLookup.existsByCode(COURSE_CODE)).thenReturn(false);

    assertThatThrownBy(() -> service.enroll(command)).isInstanceOf(UnknownCourseException.class);

    verify(repository, never()).existsByStudentAndCourse(any(), any());
  }

  @Test
  void enrollRejectsDuplicateEnrollment() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    knownStudent();
    when(courseLookup.existsByCode(COURSE_CODE)).thenReturn(true);
    when(repository.existsByStudentAndCourse(STUDENT_ID, COURSE_CODE)).thenReturn(true);

    assertThatThrownBy(() -> service.enroll(command)).isInstanceOf(DuplicateEnrollmentException.class);

    verify(repository, never()).save(any());
  }

  @Test
  void enrollChecksStudentThenCourseThenDuplicateInThatOrder() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    knownStudent();
    when(courseLookup.existsByCode(COURSE_CODE)).thenReturn(true);
    when(repository.existsByStudentAndCourse(STUDENT_ID, COURSE_CODE)).thenReturn(false);
    when(repository.save(any(Enrollment.class))).thenAnswer(EnrollmentServiceTest::savedWithId);

    service.enroll(command);

    InOrder order = inOrder(studentLookup, courseLookup, repository);
    order.verify(studentLookup).idOf(STUDENT_CODE);
    order.verify(courseLookup).existsByCode(COURSE_CODE);
    order.verify(repository).existsByStudentAndCourse(STUDENT_ID, COURSE_CODE);
    order.verify(repository).save(any(Enrollment.class));
  }

  @Test
  void enrollResolvesTheStudentCodeToTheIdItPersists() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    knownStudent();
    when(courseLookup.existsByCode(COURSE_CODE)).thenReturn(true);
    when(repository.existsByStudentAndCourse(STUDENT_ID, COURSE_CODE)).thenReturn(false);
    when(repository.save(any(Enrollment.class))).thenAnswer(EnrollmentServiceTest::savedWithId);

    EnrollmentService.CreatedEnrollment result = service.enroll(command);

    // The response echoes the caller's business key, while the saved row carries the surrogate id.
    assertThat(result.studentCode()).isEqualTo("S00123");
    assertThat(result.courseCode()).isEqualTo("CS101");
    assertThat(result.enrolledAt()).isNotNull();

    verify(repository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                enrollment -> enrollment.studentId().equals(STUDENT_ID)));
  }

  @Test
  void endRejectsWhenTheStudentCodeResolvesToNothing() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(studentLookup.idOf(STUDENT_CODE)).thenReturn(Optional.empty());

    // A 404, not the 400 enroll() raises: the caller is addressing one enrollment, and an
    // enrollment whose student does not exist is simply not there.
    assertThatThrownBy(() -> service.end("S00123", "CS101")).isInstanceOf(NotFoundException.class);

    verify(repository, never()).deleteByStudentAndCourse(any(), any());
  }

  @Test
  void endRejectsWhenNoActiveEnrollmentExists() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    knownStudent();
    when(repository.existsByStudentAndCourse(STUDENT_ID, COURSE_CODE)).thenReturn(false);

    assertThatThrownBy(() -> service.end("S00123", "CS101")).isInstanceOf(NotFoundException.class);

    verify(repository, never()).deleteByStudentAndCourse(any(), any());
  }

  @Test
  void endDeletesAnExistingEnrollment() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    knownStudent();
    when(repository.existsByStudentAndCourse(STUDENT_ID, COURSE_CODE)).thenReturn(true);

    service.end("S00123", "CS101");

    verify(repository).deleteByStudentAndCourse(STUDENT_ID, COURSE_CODE);
  }

  @Test
  void onStudentDeletedCascadesToEnrollments() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);

    service.onStudentDeleted(new StudentDeleted(STUDENT_ID));

    verify(repository).deleteByStudentId(STUDENT_ID);
  }

  @Test
  void onCourseDeletedCascadesToEnrollments() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);

    service.onCourseDeleted(new CourseDeleted(COURSE_CODE));

    verify(repository).deleteByCourseCode(COURSE_CODE);
  }

  @Test
  void getDetailThrowsNotFoundWhenTheStudentCodeResolvesToNothing() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(studentLookup.idOf(STUDENT_CODE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail("S00123", "CS101")).isInstanceOf(NotFoundException.class);

    verifyNoInteractions(repository, courseLookup);
  }

  @Test
  void getDetailThrowsNotFoundWhenEnrollmentDoesNotExist() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    knownStudent();
    when(repository.findByStudentAndCourse(STUDENT_ID, COURSE_CODE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail("S00123", "CS101")).isInstanceOf(NotFoundException.class);

    verifyNoInteractions(courseLookup);
  }

  @Test
  void getDetailReturnsStudentAndCourseSummaries() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    Instant enrolledAt = Instant.parse("2024-01-01T00:00:00Z");
    knownStudent();
    when(repository.findByStudentAndCourse(STUDENT_ID, COURSE_CODE))
        .thenReturn(Optional.of(anEnrollment(STUDENT_ID, enrolledAt)));
    StudentSummary studentSummary = aStudentSummary();
    CourseSummary courseSummary = aCourseSummary();
    when(studentLookup.summaryOf(STUDENT_ID)).thenReturn(studentSummary);
    when(courseLookup.summaryOf(COURSE_CODE)).thenReturn(courseSummary);

    EnrollmentService.EnrollmentDetailView detail = service.getDetail("S00123", "CS101");

    assertThat(detail.student()).isEqualTo(studentSummary);
    assertThat(detail.course()).isEqualTo(courseSummary);
    assertThat(detail.enrolledAt()).isEqualTo(enrolledAt);
  }

  @Test
  void searchRejectsNeitherFilter() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);

    assertThatThrownBy(() -> service.search(null, null, PageRequest.of(0, 20)))
        .isInstanceOf(DomainValidationException.class);

    verifyNoInteractions(repository, studentLookup, courseLookup);
  }

  @Test
  void searchRejectsBothFilters() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);

    assertThatThrownBy(() -> service.search("S00123", "CS101", PageRequest.of(0, 20)))
        .isInstanceOf(DomainValidationException.class);

    verifyNoInteractions(repository, studentLookup, courseLookup);
  }

  @Test
  void searchRejectsAnUnknownStudentCode() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(studentLookup.idOf(STUDENT_CODE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.search("S00123", null, PageRequest.of(0, 20)))
        .isInstanceOf(UnknownStudentException.class);
  }

  @Test
  void searchRejectsAnUnknownCourseCode() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(courseLookup.existsByCode(COURSE_CODE)).thenReturn(false);

    assertThatThrownBy(() -> service.search(null, "CS101", PageRequest.of(0, 20)))
        .isInstanceOf(UnknownCourseException.class);
  }

  @Test
  void searchByStudentCodeResolvesTheStudentOnceForThePage() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    Pageable pageable = PageRequest.of(0, 20);
    Instant enrolledAt = Instant.parse("2024-01-01T00:00:00Z");
    knownStudent();
    when(studentLookup.summaryOf(STUDENT_ID)).thenReturn(aStudentSummary());
    when(repository.findByStudentId(STUDENT_ID, pageable))
        .thenReturn(
            new PageImpl<>(
                List.of(anEnrollment(STUDENT_ID, enrolledAt), anEnrollment(STUDENT_ID, enrolledAt)),
                pageable,
                2));
    when(courseLookup.summaryOf(COURSE_CODE)).thenReturn(aCourseSummary());

    Page<EnrollmentService.EnrollmentDetailView> page =
        service.search("S00123", null, pageable);

    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getContent().get(0).course().courseCode()).isEqualTo("CS101");
    // Two rows, one student lookup -- the constant side is resolved outside the per-row mapping.
    verify(studentLookup).summaryOf(STUDENT_ID);
  }

  @Test
  void searchByCourseCodeResolvesTheCourseOnceForThePage() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    Pageable pageable = PageRequest.of(0, 20);
    Instant enrolledAt = Instant.parse("2024-01-01T00:00:00Z");
    when(courseLookup.existsByCode(COURSE_CODE)).thenReturn(true);
    when(courseLookup.summaryOf(COURSE_CODE)).thenReturn(aCourseSummary());
    when(repository.findByCourseCode(COURSE_CODE, pageable))
        .thenReturn(
            new PageImpl<>(
                List.of(anEnrollment(STUDENT_ID, enrolledAt), anEnrollment(new StudentId(2L), enrolledAt)),
                pageable,
                2));
    when(studentLookup.summaryOf(STUDENT_ID)).thenReturn(aStudentSummary());
    when(studentLookup.summaryOf(new StudentId(2L)))
        .thenReturn(new StudentSummary("S00124", "John", "Roe", "john.roe@example.edu"));

    Page<EnrollmentService.EnrollmentDetailView> page = service.search(null, "CS101", pageable);

    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getContent())
        .extracting(view -> view.student().studentCode())
        .containsExactly("S00123", "S00124");
    verify(courseLookup).summaryOf(COURSE_CODE);
  }

  private static Enrollment anEnrollment(StudentId studentId, Instant enrolledAt) {
    return Enrollment.reconstitute(new EnrollmentId(1L), studentId, COURSE_CODE, enrolledAt);
  }

  private static StudentSummary aStudentSummary() {
    return new StudentSummary("S00123", "Jane", "Doe", "jane.doe@example.edu");
  }

  private static CourseSummary aCourseSummary() {
    return new CourseSummary("CS101", "Intro to CS", 3);
  }

  private static Enrollment savedWithId(org.mockito.invocation.InvocationOnMock invocation) {
    Enrollment toSave = invocation.getArgument(0);
    return Enrollment.reconstitute(
        new EnrollmentId(1L), toSave.studentId(), toSave.courseCode(), toSave.enrolledAt());
  }
}
