package org.phuchoang.management.course.port;

import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.domain.CourseCode;

/**
 * Scoped to what US-3.1 (create) needs. {@code findByCode}/{@code search}/{@code deleteByCode}
 * (06-low-level-design.md §5) are added when the update/remove/search use cases (US-3.2/US-3.3/
 * US-5.3) are implemented.
 */
public interface CourseRepository {

  boolean existsByCode(CourseCode code);

  Course save(Course course);
}
