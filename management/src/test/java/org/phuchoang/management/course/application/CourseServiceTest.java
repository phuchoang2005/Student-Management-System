package org.phuchoang.management.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.course.CourseId;
import org.phuchoang.management.course.application.command.CreateCourseCommand;
import org.phuchoang.management.course.application.command.UpdateCourseCommand;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.domain.CourseCode;
import org.phuchoang.management.course.domain.Credits;
import org.phuchoang.management.course.port.CourseRepository;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

  @Mock private CourseRepository repository;

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
    service = new CourseService(repository);
    when(repository.existsByCode(new CourseCode("CS101"))).thenReturn(true);

    assertThatThrownBy(() -> service.create(command)).isInstanceOf(DuplicateCodeException.class);
  }

  @Test
  void createRejectsNonPositiveCredits() {
    service = new CourseService(repository);
    when(repository.existsByCode(any())).thenReturn(false);
    CreateCourseCommand invalid = new CreateCourseCommand("CS101", "Intro to CS", "Basics", 0);

    assertThatThrownBy(() -> service.create(invalid)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void createRejectsBlankName() {
    service = new CourseService(repository);
    when(repository.existsByCode(any())).thenReturn(false);
    CreateCourseCommand invalid = new CreateCourseCommand("CS101", " ", "Basics", 3);

    assertThatThrownBy(() -> service.create(invalid)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void createSavesCourseAndReturnsView() {
    service = new CourseService(repository);
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

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.courseCode()).isEqualTo("CS101");
    assertThat(result.name()).isEqualTo("Intro to CS");
    assertThat(result.description()).isEqualTo("Basics");
    assertThat(result.credits()).isEqualTo(3);
  }

  @Test
  void updateThrowsNotFoundWhenCourseDoesNotExist() {
    service = new CourseService(repository);
    when(repository.findByCode(existingCode)).thenReturn(Optional.empty());
    UpdateCourseCommand update = new UpdateCourseCommand("Advanced CS", "Deeper dive", 4);

    assertThatThrownBy(() -> service.update("CS101", update)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void updateRejectsBlankName() {
    service = new CourseService(repository);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingCourse));
    UpdateCourseCommand update = new UpdateCourseCommand(" ", "Deeper dive", 4);

    assertThatThrownBy(() -> service.update("CS101", update)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void updateRejectsNonPositiveCredits() {
    service = new CourseService(repository);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingCourse));
    UpdateCourseCommand update = new UpdateCourseCommand("Advanced CS", "Deeper dive", 0);

    assertThatThrownBy(() -> service.update("CS101", update)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void updateAppliesChangesAndReturnsView() {
    service = new CourseService(repository);
    when(repository.findByCode(existingCode)).thenReturn(Optional.of(existingCourse));
    when(repository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
    UpdateCourseCommand update = new UpdateCourseCommand("Advanced CS", "Deeper dive", 4);

    CourseService.UpdatedCourse result = service.update("CS101", update);

    assertThat(result.courseCode()).isEqualTo("CS101");
    assertThat(result.name()).isEqualTo("Advanced CS");
    assertThat(result.description()).isEqualTo("Deeper dive");
    assertThat(result.credits()).isEqualTo(4);
  }
}
