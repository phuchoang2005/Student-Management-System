package org.phuchoang.management.course.internal;

import java.util.Optional;
import org.phuchoang.management.course.CourseId;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.domain.CourseCode;
import org.phuchoang.management.course.domain.Credits;
import org.phuchoang.management.course.port.CourseRepository;
import org.phuchoang.management.shared.exception.StaleWriteException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCourseRepository implements CourseRepository {

  private final SpringDataCourseRepository springRepo;

  JdbcCourseRepository(SpringDataCourseRepository springRepo) {
    this.springRepo = springRepo;
  }

  @Override
  public Optional<Course> findByCode(CourseCode code) {
    return springRepo.findByCourseCode(code.value()).map(this::toDomain);
  }

  @Override
  public boolean existsByCode(CourseCode code) {
    return springRepo.existsByCourseCode(code.value());
  }

  @Override
  public Course save(Course course) {
    try {
      return toDomain(springRepo.save(toRow(course)));
    } catch (OptimisticLockingFailureException e) {
      throw new StaleWriteException("Course " + course.code().value() + " was modified concurrently");
    }
  }

  private CourseRow toRow(Course course) {
    CourseId id = course.id();
    return new CourseRow(
        id == null ? null : id.value(),
        course.code().value(),
        course.name(),
        course.description(),
        course.credits().value(),
        course.version(),
        course.createdAt(),
        course.updatedAt());
  }

  private Course toDomain(CourseRow row) {
    return Course.reconstitute(
        new CourseId(row.id()),
        new CourseCode(row.courseCode()),
        row.name(),
        row.description(),
        new Credits(row.credits()),
        row.createdAt(),
        row.updatedAt(),
        row.version());
  }
}
