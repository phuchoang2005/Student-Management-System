package org.phuchoang.management.course.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.course.CourseDeleted;
import org.phuchoang.management.course.CourseLookup;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.course.application.command.CreateCourseCommand;
import org.phuchoang.management.course.application.command.UpdateCourseCommand;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.domain.Credits;
import org.phuchoang.management.course.port.CourseRepository;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService implements CourseLookup {

  private final CourseRepository repository;
  private final ApplicationEventPublisher events;

  public CourseService(CourseRepository repository, ApplicationEventPublisher events) {
    this.repository = repository;
    this.events = events;
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
        course.code().value(),
        course.name(),
        course.description(),
        course.credits().value(),
        course.createdAt(),
        course.updatedAt());
  }

  /**
   * findByCode (404 if absent) → {@code repository.deleteByCode} → publish {@code CourseDeleted}
   * (06-low-level-design.md §2.3, §13), mirroring {@code StudentService.remove}. The {@code
   * enrollment} module's cascade listener doesn't exist until it ships in Sprint 3
   * (04-sprint-backlog.md §3) — for now, the DB-level {@code ON DELETE CASCADE} on {@code
   * enrollments.course_id} (05-database-schema.md §5) is the only cascade actually in effect;
   * publishing here just makes sure the event is on the classpath and fires so that listener can
   * be wired in without touching this method again.
   */
  @Transactional
  public void remove(String code) {
    CourseCode courseCode = new CourseCode(code);
    Course course =
        repository
            .findByCode(courseCode)
            .orElseThrow(() -> new NotFoundException("Course '" + code + "' does not exist."));

    repository.deleteByCode(courseCode);
    events.publishEvent(new CourseDeleted(course.code()));
  }

  /**
   * UC-15 — matches code/name, paged. {@code query} may be blank/{@code null}.
   *
   * <p>The enrolled-student count is fetched once for the whole page rather than per row: a page of
   * 20 costs one extra query, not 20. {@code readOnly} because this is now two statements — the
   * page and its counts should come from one snapshot, or a concurrent enrollment could land
   * between them and produce a count for a course the page doesn't contain.
   *
   * <p>That count is read outside any enrolling transaction, so it is a snapshot rather than a
   * guarantee — it must not be used as a capacity check.
   */
  @Transactional(readOnly = true)
  public Page<CourseSummaryView> search(String query, Pageable pageable) {
    Page<Course> page = repository.search(query, pageable);
    List<String> codes = page.getContent().stream().map(course -> course.code().value()).toList();
    Map<String, Long> counts = repository.enrollmentCountsFor(codes);
    return page.map(
        course ->
            new CourseSummaryView(
                course.code().value(),
                course.name(),
                course.credits().value(),
                counts.getOrDefault(course.code().value(), 0L)));
  }

  /**
   * findByCode (404 if absent), plus the enrolled-student count. The roster itself is still
   * <em>not</em> embedded: it is its own paged, separately authorized read ({@code GET
   * /api/v1/enrollments?courseCode=}), so a Student who may browse the catalogue never receives the
   * roster as a side effect of opening a course. A count carries no student's name, which is what
   * makes it safe on the same DTO the roster is deliberately absent from.
   */
  @Transactional(readOnly = true)
  public CourseDetailView getDetail(String code) {
    CourseCode courseCode = new CourseCode(code);
    Course course =
        repository
            .findByCode(courseCode)
            .orElseThrow(() -> new NotFoundException("Course '" + code + "' does not exist."));

    // A count, not the roster itself -- "how many" is not "who", and the distinction is the whole
    // reason this is safe to hand a Student browsing the catalogue.
    return new CourseDetailView(
        course.code().value(),
        course.name(),
        course.description(),
        course.credits().value(),
        repository.enrollmentCountOf(courseCode),
        course.createdAt(),
        course.updatedAt());
  }

  /** Backs {@code CourseLookup.existsByCode} (Enrollment.2). */
  @Override
  public boolean existsByCode(CourseCode code) {
    return repository.existsByCode(code);
  }

  /**
   * findByCode (404 if absent — callers only pass codes already known to reference an existing
   * course, per Enrollment.2, so this only fires on a genuine data race), mirroring {@code
   * StudentService.summaryOf}.
   */
  @Override
  public CourseSummary summaryOf(CourseCode code) {
    Course course =
        repository
            .findByCode(code)
            .orElseThrow(() -> new NotFoundException("Course '" + code.value() + "' does not exist."));

    return new CourseSummary(course.code().value(), course.name(), course.credits().value());
  }

  /**
   * Unwraps {@code Course}'s Value Objects here rather than in {@code CourseMapper} — the web
   * layer may never call a method on a Domain-layer object directly (LayeringRulesTest), only the
   * Application layer may.
   */
  public record CreatedCourse(
      String courseCode,
      String name,
      String description,
      int credits,
      Instant createdAt,
      Instant updatedAt) {}

  /** Same VO-unwrapping rationale as {@link CreatedCourse}, for {@link #update}'s result. */
  public record UpdatedCourse(
      String courseCode,
      String name,
      String description,
      int credits,
      Instant createdAt,
      Instant updatedAt) {}

  /** Same VO-unwrapping rationale as {@link CreatedCourse}, for one {@link #search} result. */
  public record CourseSummaryView(String courseCode, String name, int credits, long enrolledCount) {}

  /** Same VO-unwrapping rationale as {@link CreatedCourse}, for {@link #getDetail}'s result. */
  public record CourseDetailView(
      String courseCode,
      String name,
      String description,
      int credits,
      long enrolledCount,
      Instant createdAt,
      Instant updatedAt) {}
}
