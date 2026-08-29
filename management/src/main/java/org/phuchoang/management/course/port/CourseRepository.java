package org.phuchoang.management.course.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.shared.paging.CursorPage;

/**
 * Scoped to what US-3.1 (create), US-3.2 (update), US-3.3 (remove) and US-5.3 (search) need.
 */
public interface CourseRepository {

  Optional<Course> findByCode(CourseCode code);

  boolean existsByCode(CourseCode code);

  /**
   * UC-15 — matches course code/name, keyset-paged (PM-045). {@code query} may be blank/{@code
   * null}. {@code afterKey} is the raw {@code course_code} to resume after, decoded from the
   * cursor by the caller; {@code null} starts from the first page.
   */
  CursorPage<Course> search(String query, String afterKey, int limit);

  /** Batch resolution for {@code CourseLookup.summariesOf} (PM-046). A code naming no course is absent from the result. */
  List<Course> findByCodes(Collection<CourseCode> codes);

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
