package org.phuchoang.management.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.course.CourseDeleted;
import org.phuchoang.management.course.CourseId;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.course.application.command.CreateCourseCommand;
import org.phuchoang.management.course.application.command.UpdateCourseCommand;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.course.domain.Credits;
import org.phuchoang.management.course.port.CourseRepository;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

  @Mock private CourseRepository repository;
  @Mock private ApplicationEventPublisher events;

  private CourseService service;

  private final CreateCourseCommand command = new CreateCourseCommand("CS101", "Intro to CS", "Basics", 3);
  private final CourseCode existingCode = new CourseCode("CS101");
  private final Course existingCourse =
      Course.reconstitute(
          new CourseId(1L),
          existingCode,
          "Intro to CS",
          "Basics",
          new Credits(3),
          Instant.parse("2024-01-01T00:00:00Z"),
          Instant.parse("2024-01-01T00:00:00Z"),
          0L);

  @Test
  void createRejectsDuplicateCode() {
    service = new CourseService(repository, events);
    when(repository.existsByCode(new CourseCode("CS101"))).thenReturn(true);

    assertThatThrownBy(() -> service.create(command)).isInstanceOf(DuplicateCodeException.class);
  }

  @Test
  void createRejectsNonPositiveCredits() {
    service = new CourseService(repository, events);
    when(repository.existsByCode(any())).thenReturn(false);
    CreateCourseCommand invalid = new CreateCourseCommand("CS101", "Intro to CS", "Basics", 0);

    assertThatThrownBy(() -> service.create(invalid)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void createRejectsBlankName() {
    service = new CourseService(repository, events);
    when(repository.existsByCode(any())).thenReturn(false);
    CreateCourseCommand invalid = new CreateCourseCommand("CS101", " ", "Basics", 3);

    assertThatThrownBy(() -> service.create(invalid)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void createSavesCourseAndReturnsView() {
    service = new CourseService(repository, events);
    when(repository.existsByCode(any())).thenReturn(false);
    when(repository.save(any(Course.class)))
        .thenAnswer(
            invocation -> {
              Course toSave = invocation.getArgument(0);
              return Course.reconstitute(
                  new CourseId(1L),
                  toSave.code(),
                  toSave.name(),
                  toSave.description(),
                  toSave.credits(),
                  toSave.createdAt(),
                  toSave.updatedAt(),
                  toSave.version());
            });

    CourseService.CreatedCourse result = service.create(command);

    assertThat(result.courseCode()).isEqualTo("CS101");
    assertThat(result.name()).isEqualTo("Intro to CS");
    assertThat(result.description()).isEqualTo("Basics");
    assertThat(result.credits()).isEqualTo(3);
  }

  @Test
  void updateThrowsNotFoundWhenCourseDoesNotExist() {
    service = new CourseService(repository, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.empty());
    UpdateCourseCommand update = new UpdateCourseCommand("Advanced CS", "Deeper dive", 4);

    assertThatThrownBy(() -> service.update("CS101", update)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void updateRejectsBlankName() {
    service = new CourseService(repository, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingCourse));
    UpdateCourseCommand update = new UpdateCourseCommand(" ", "Deeper dive", 4);

    assertThatThrownBy(() -> service.update("CS101", update)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void updateRejectsNonPositiveCredits() {
    service = new CourseService(repository, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingCourse));
    UpdateCourseCommand update = new UpdateCourseCommand("Advanced CS", "Deeper dive", 0);

    assertThatThrownBy(() -> service.update("CS101", update)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void updateAppliesChangesAndReturnsView() {
    service = new CourseService(repository, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingCourse));
    when(repository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
    UpdateCourseCommand update = new UpdateCourseCommand("Advanced CS", "Deeper dive", 4);

    CourseService.UpdatedCourse result = service.update("CS101", update);

    assertThat(result.courseCode()).isEqualTo("CS101");
    assertThat(result.name()).isEqualTo("Advanced CS");
    assertThat(result.description()).isEqualTo("Deeper dive");
    assertThat(result.credits()).isEqualTo(4);
  }

  @Test
  void removeThrowsNotFoundWhenCourseDoesNotExist() {
    service = new CourseService(repository, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.remove("CS101")).isInstanceOf(NotFoundException.class);

    verify(repository, never()).deleteByCode(any());
    verifyNoInteractions(events);
  }

  @Test
  void removeDeletesTheCourseAndPublishesCourseDeleted() {
    service = new CourseService(repository, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingCourse));

    service.remove("CS101");

    verify(repository).deleteByCode(existingCode);
    verify(events).publishEvent(new CourseDeleted(existingCode));
  }

  @Test
  void searchReturnsMappedSummariesFromRepositoryPage() {
    service = new CourseService(repository, events);
    Pageable pageable = PageRequest.of(0, 20);
    Page<Course> repoPage = new PageImpl<>(java.util.List.of(existingCourse), pageable, 1);
    when(repository.search("cs101", pageable)).thenReturn(repoPage);

    Page<CourseService.CourseSummaryView> result = service.search("cs101", pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    CourseService.CourseSummaryView summary = result.getContent().get(0);
    assertThat(summary.courseCode()).isEqualTo("CS101");
    assertThat(summary.name()).isEqualTo("Intro to CS");
    assertThat(summary.credits()).isEqualTo(3);
  }

  @Test
  void searchReturnsEmptyPageWhenNothingMatches() {
    service = new CourseService(repository, events);
    Pageable pageable = PageRequest.of(0, 20);
    when(repository.search("nobody", pageable)).thenReturn(Page.empty(pageable));

    Page<CourseService.CourseSummaryView> result = service.search("nobody", pageable);

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  void getDetailThrowsNotFoundWhenCourseDoesNotExist() {
    service = new CourseService(repository, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail("CS101")).isInstanceOf(NotFoundException.class);
  }

  @Test
  void getDetailReturnsCourseFields() {
    service = new CourseService(repository, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingCourse));

    CourseService.CourseDetailView detail = service.getDetail("CS101");

    assertThat(detail.courseCode()).isEqualTo("CS101");
    assertThat(detail.name()).isEqualTo("Intro to CS");
    assertThat(detail.description()).isEqualTo("Basics");
    assertThat(detail.credits()).isEqualTo(3);
  }

  @Test
  void summaryOfThrowsNotFoundWhenCourseDoesNotExist() {
    service = new CourseService(repository, events);
    when(repository.findByCode(new CourseCode("does-not-exist"))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.summaryOf(new CourseCode("does-not-exist")))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void summaryOfReturnsCourseSummaryFields() {
    service = new CourseService(repository, events);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingCourse));

    CourseSummary summary = service.summaryOf(existingCode);

    assertThat(summary).isEqualTo(new CourseSummary("CS101", "Intro to CS", 3));
  }
}
