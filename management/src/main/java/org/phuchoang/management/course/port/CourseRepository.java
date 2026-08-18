package org.phuchoang.management.course.port;

import java.util.Optional;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.domain.CourseCode;

/**
 * Scoped to what US-3.1 (create) and US-3.2 (update) need. {@code search}/{@code deleteByCode}
 * (06-low-level-design.md §5) are added when the remove/search use cases (US-3.3/US-5.3) are
 * implemented.
 */
public interface CourseRepository {

  Optional<Course> findByCode(CourseCode code);

  boolean existsByCode(CourseCode code);

  Course save(Course course);
}
