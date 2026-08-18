package org.phuchoang.management.course.port;

import java.util.Optional;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.domain.CourseCode;

/**
 * Scoped to what US-3.1 (create), US-3.2 (update) and US-3.3 (remove) need. {@code search}
 * (06-low-level-design.md §5) is added when the search use case (US-5.3) is implemented.
 */
public interface CourseRepository {

  Optional<Course> findByCode(CourseCode code);

  boolean existsByCode(CourseCode code);

  Course save(Course course);

  void deleteByCode(CourseCode code);
}
