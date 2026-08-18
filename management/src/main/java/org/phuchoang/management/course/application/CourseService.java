package org.phuchoang.management.course.application;

import java.time.Instant;
import org.phuchoang.management.course.application.command.CreateCourseCommand;
import org.phuchoang.management.course.application.command.UpdateCourseCommand;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.domain.CourseCode;
import org.phuchoang.management.course.domain.Credits;
import org.phuchoang.management.course.port.CourseRepository;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.NotFoundException;
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
   * findByCode (404 if absent) → {@code Course.applyChanges} → save, mirroring {@code
   * StudentService.update} minus the uniqueness re-check — Course.2-3 have no uniqueness rule,
   * unlike Student's email.
   *
   * <p>Takes the raw {@code String code} the controller receives from the path, not {@code
   * CourseCode}: like {@link #create}, {@code CourseCode} is constructed here rather than
   * accepted as a parameter so the Web layer never depends on a Domain-layer type
   * (LayeringRulesTest). {@code courseCode} itself is immutable — never accepted from the
   * command.
   */
  @Transactional
  public UpdatedCourse update(String code, UpdateCourseCommand command) {
    CourseCode courseCode = new CourseCode(code);
    Course course =
        repository
            .findByCode(courseCode)
            .orElseThrow(() -> new NotFoundException("Course '" + code + "' does not exist."));

    Credits credits = new Credits(command.credits());
    course.applyChanges(command.name(), command.description(), credits);
    course = repository.save(course);

    return new UpdatedCourse(
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

  /** Same VO-unwrapping rationale as {@link CreatedCourse}, for {@link #update}'s result. */
  public record UpdatedCourse(
      Long id,
      String courseCode,
      String name,
      String description,
      int credits,
      Instant createdAt,
      Instant updatedAt) {}
}
