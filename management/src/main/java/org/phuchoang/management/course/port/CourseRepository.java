package org.phuchoang.management.course.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.CourseCode;
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

  /**
   * How many students are enrolled in each of {@code courseCodes}, for the course list (US-5.3). A
   * code with no enrollments is present with {@code 0}; a code naming no course is absent.
   *
   * <p>Keyed by the raw code string rather than {@code CourseCode} so the caller can look the count
   * up per row without re-wrapping a value object it already unwrapped for the view.
   */
  Map<String, Long> enrollmentCountsFor(Collection<String> courseCodes);

  /** The same count for one course, for the detail read (UC-19). */
  long enrollmentCountOf(CourseCode code);

  Course save(Course course);

  void deleteByCode(CourseCode code);
}
