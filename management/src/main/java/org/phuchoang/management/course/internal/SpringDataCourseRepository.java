package org.phuchoang.management.course.internal;

import org.springframework.data.repository.CrudRepository;

interface SpringDataCourseRepository extends CrudRepository<CourseRow, Long> {

  boolean existsByCourseCode(String courseCode);
}
