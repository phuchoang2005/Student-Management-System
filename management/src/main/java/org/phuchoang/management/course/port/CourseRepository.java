package org.phuchoang.management.course.port;

import java.util.Optional;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.domain.CourseCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Scoped to what US-3.1 (create), US-3.2 (update), US-3.3 (remove) and US-5.3 (search) need.
 */
public interface CourseRepository {

  Optional<Course> findByCode(CourseCode code);

  boolean existsByCode(CourseCode code);

  /** UC-15 — matches course code/name, paged. {@code query} may be blank/{@code null}. */
  Page<Course> search(String query, Pageable pageable);

  Course save(Course course);

  void deleteByCode(CourseCode code);
}
