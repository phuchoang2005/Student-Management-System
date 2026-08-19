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
import org.phuchoang.management.shared.exception.DuplicateEnrollmentException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.exception.UnknownCourseException;
import org.phuchoang.management.shared.exception.UnknownStudentException;
import org.phuchoang.management.student.StudentDeleted;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;
import org.phuchoang.management.student.StudentSummary;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

  @Mock private EnrollmentRepository repository;
  @Mock private StudentLookup studentLookup;
  @Mock private CourseLookup courseLookup;

  private EnrollmentService service;

  private final EnrollStudentCommand command = new EnrollStudentCommand(1L, "CS101");

  @Test
  void enrollRejectsUnknownStudent() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(studentLookup.existsById(new StudentId(1L))).thenReturn(false);

    assertThatThrownBy(() -> service.enroll(command)).isInstanceOf(UnknownStudentException.class);

    verify(courseLookup, never()).existsByCode(any());
  }

  @Test
  void enrollRejectsUnknownCourse() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(studentLookup.existsById(new StudentId(1L))).thenReturn(true);
    when(courseLookup.existsByCode(new CourseCode("CS101"))).thenReturn(false);

    assertThatThrownBy(() -> service.enroll(command)).isInstanceOf(UnknownCourseException.class);

    verify(repository, never()).existsByStudentAndCourse(any(), any());
  }

  @Test
  void enrollRejectsDuplicateEnrollment() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(studentLookup.existsById(new StudentId(1L))).thenReturn(true);
    when(courseLookup.existsByCode(new CourseCode("CS101"))).thenReturn(true);
    when(repository.existsByStudentAndCourse(new StudentId(1L), new CourseCode("CS101"))).thenReturn(true);

    assertThatThrownBy(() -> service.enroll(command)).isInstanceOf(DuplicateEnrollmentException.class);

    verify(repository, never()).save(any());
  }

  @Test
  void enrollChecksStudentThenCourseThenDuplicateInThatOrder() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(studentLookup.existsById(new StudentId(1L))).thenReturn(true);
    when(courseLookup.existsByCode(new CourseCode("CS101"))).thenReturn(true);
    when(repository.existsByStudentAndCourse(new StudentId(1L), new CourseCode("CS101"))).thenReturn(false);
    when(repository.save(any(Enrollment.class)))
        .thenAnswer(
            invocation -> {
              Enrollment toSave = invocation.getArgument(0);
              return Enrollment.reconstitute(
                  new EnrollmentId(1L), toSave.studentId(), toSave.courseCode(), toSave.enrolledAt());
            });

    service.enroll(command);

    InOrder order = inOrder(studentLookup, courseLookup, repository);
    order.verify(studentLookup).existsById(new StudentId(1L));
    order.verify(courseLookup).existsByCode(new CourseCode("CS101"));
    order.verify(repository).existsByStudentAndCourse(new StudentId(1L), new CourseCode("CS101"));
    order.verify(repository).save(any(Enrollment.class));
  }

  @Test
  void enrollSavesEnrollmentAndReturnsResult() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(studentLookup.existsById(new StudentId(1L))).thenReturn(true);
    when(courseLookup.existsByCode(new CourseCode("CS101"))).thenReturn(true);
    when(repository.existsByStudentAndCourse(new StudentId(1L), new CourseCode("CS101"))).thenReturn(false);
    when(repository.save(any(Enrollment.class)))
        .thenAnswer(
            invocation -> {
              Enrollment toSave = invocation.getArgument(0);
              return Enrollment.reconstitute(
                  new EnrollmentId(1L),
                  toSave.studentId(),
                  toSave.courseCode(),
                  toSave.enrolledAt());
            });

    EnrollmentService.CreatedEnrollment result = service.enroll(command);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.studentId()).isEqualTo(1L);
    assertThat(result.courseCode()).isEqualTo("CS101");
    assertThat(result.enrolledAt()).isNotNull();
  }

  @Test
  void endRejectsWhenNoActiveEnrollmentExists() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(repository.existsByStudentAndCourse(new StudentId(1L), new CourseCode("CS101"))).thenReturn(false);

    assertThatThrownBy(() -> service.end(1L, "CS101")).isInstanceOf(NotFoundException.class);

    verify(repository, never()).deleteByStudentAndCourse(any(), any());
  }

  @Test
  void endDeletesAnExistingEnrollment() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(repository.existsByStudentAndCourse(new StudentId(1L), new CourseCode("CS101"))).thenReturn(true);

    service.end(1L, "CS101");

    verify(repository).deleteByStudentAndCourse(new StudentId(1L), new CourseCode("CS101"));
  }

  @Test
  void onStudentDeletedCascadesToEnrollments() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);

    service.onStudentDeleted(new StudentDeleted(new StudentId(1L)));

    verify(repository).deleteByStudentId(new StudentId(1L));
  }

  @Test
  void onCourseDeletedCascadesToEnrollments() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);

    service.onCourseDeleted(new CourseDeleted(new CourseCode("CS101")));

    verify(repository).deleteByCourseCode(new CourseCode("CS101"));
  }

  @Test
  void getDetailThrowsNotFoundWhenEnrollmentDoesNotExist() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    when(repository.findByStudentAndCourse(new StudentId(1L), new CourseCode("CS101")))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail(1L, "CS101")).isInstanceOf(NotFoundException.class);

    verifyNoInteractions(studentLookup, courseLookup);
  }

  @Test
  void getDetailReturnsStudentAndCourseSummaries() {
    service = new EnrollmentService(repository, studentLookup, courseLookup);
    Instant enrolledAt = Instant.parse("2024-01-01T00:00:00Z");
    Enrollment enrollment =
        Enrollment.reconstitute(
            new EnrollmentId(1L), new StudentId(1L), new CourseCode("CS101"), enrolledAt);
    when(repository.findByStudentAndCourse(new StudentId(1L), new CourseCode("CS101")))
        .thenReturn(Optional.of(enrollment));
    StudentSummary studentSummary = new StudentSummary(1L, "S00123", "Jane", "Doe", "jane.doe@example.edu");
    CourseSummary courseSummary = new CourseSummary(1L, "CS101", "Intro to CS", 3);
    when(studentLookup.summaryOf(new StudentId(1L))).thenReturn(studentSummary);
    when(courseLookup.summaryOf(new CourseCode("CS101"))).thenReturn(courseSummary);

    EnrollmentService.EnrollmentDetailView detail = service.getDetail(1L, "CS101");

    assertThat(detail.student()).isEqualTo(studentSummary);
    assertThat(detail.course()).isEqualTo(courseSummary);
    assertThat(detail.enrolledAt()).isEqualTo(enrolledAt);
  }
}
