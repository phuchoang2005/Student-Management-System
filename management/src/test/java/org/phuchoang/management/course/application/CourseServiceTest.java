package org.phuchoang.management.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.course.CourseId;
import org.phuchoang.management.course.application.command.CreateCourseCommand;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.domain.CourseCode;
import org.phuchoang.management.course.port.CourseRepository;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.DuplicateCodeException;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

  @Mock private CourseRepository repository;

  private CourseService service;

  private final CreateCourseCommand command = new CreateCourseCommand("CS101", "Intro to CS", "Basics", 3);

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
}
