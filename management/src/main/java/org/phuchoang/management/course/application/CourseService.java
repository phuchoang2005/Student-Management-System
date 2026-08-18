package org.phuchoang.management.course.application;

import java.time.Instant;
import org.phuchoang.management.course.application.command.CreateCourseCommand;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.domain.CourseCode;
import org.phuchoang.management.course.domain.Credits;
import org.phuchoang.management.course.port.CourseRepository;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

  private final CourseRepository repository;

  public CourseService(CourseRepository repository) {
    this.repository = repository;
  }

  /**
   * existsByCode → {@code Course.create} → save, mirroring the uniqueness-then-validate-then-
   * persist order of {@code StudentService.register} (UC-8 main flow, Course.1–3).
   */
  @Transactional
  public CreatedCourse create(CreateCourseCommand command) {
    CourseCode code = new CourseCode(command.courseCode());
    if (repository.existsByCode(code)) {
      throw new DuplicateCodeException("Course code '" + code.value() + "' is already in use.");
    }

    Credits credits = new Credits(command.credits());
    Course course = Course.create(code, command.name(), command.description(), credits);
    course = repository.save(course);

    return new CreatedCourse(
        course.id().value(),
        course.code().value(),
        course.name(),
        course.description(),
        course.credits().value(),
        course.createdAt(),
        course.updatedAt());
  }

  /**
   * Unwraps {@code Course}'s Value Objects here rather than in {@code CourseMapper} — the web
   * layer may never call a method on a Domain-layer object directly (LayeringRulesTest), only the
   * Application layer may.
   */
  public record CreatedCourse(
      Long id,
      String courseCode,
      String name,
      String description,
      int credits,
      Instant createdAt,
      Instant updatedAt) {}
}
