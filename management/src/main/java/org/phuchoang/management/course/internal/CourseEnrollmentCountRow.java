package org.phuchoang.management.course.internal;

/**
 * Projection for {@code SpringDataCourseRepository.enrollmentCountsFor}, which answers with columns
 * no {@code @Table} row type carries. Same rationale as {@code
 * enrollment.internal.EnrollmentCourseRow}: Spring Data JDBC materialises an ad-hoc entity for a
 * plain record returned from a string {@code @Query}, so a join result gets its own Row type rather
 * than being forced into {@link CourseRow}, whose shape belongs to the aggregate that is saved.
 */
record CourseEnrollmentCountRow(String courseCode, long enrolledCount) {}
