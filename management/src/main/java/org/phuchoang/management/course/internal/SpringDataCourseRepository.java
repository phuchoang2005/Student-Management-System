package org.phuchoang.management.course.internal;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface SpringDataCourseRepository extends CrudRepository<CourseRow, Long> {

  Optional<CourseRow> findByCourseCode(String courseCode);

  boolean existsByCourseCode(String courseCode);

  void deleteByCourseCode(String courseCode);
}
