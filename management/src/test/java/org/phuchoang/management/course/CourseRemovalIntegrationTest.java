package org.phuchoang.management.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.course.domain.CourseCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-3.3 (Sprint 2) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-CRS-014–016. There's no enrollment-creation endpoint yet this
 * sprint ({@code enrollment} ships in Sprint 3, 04-sprint-backlog.md §3), so TC-CRS-015's
 * enrollment rows are seeded directly via {@code JdbcTemplate}, mirroring how {@link
 * StudentRemovalIntegrationTest} confirms the DB-level {@code ON DELETE CASCADE} safety net
 * (05-database-schema.md §5) independent of any application listener, and that {@code
 * CourseDeleted} is actually published (the "verify publishable/consumable" task) for the
 * {@code EnrollmentService.onCourseDeleted} listener Sprint 3 wires in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Testcontainers
class CourseRemovalIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static String createCourseBody(String code, String name, int credits) {
    return """
        {"courseCode":"%s","name":"%s","description":"A course.","credits":%d}
        """
        .formatted(code, name, credits);
  }

  private Long seedStudent(String code, String email) {
    jdbcTemplate.update(
        "INSERT INTO students (student_code, first_name, last_name, email, date_of_birth) "
            + "VALUES (?, ?, ?, ?, '2000-01-01')",
        code,
        "Test",
        "Student",
        email);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM students WHERE student_code = ?", Long.class, code);
  }

  private void seedEnrollment(Long studentId, Long courseId) {
    jdbcTemplate.update(
        "INSERT INTO enrollments (student_id, course_id) VALUES (?, ?)", studentId, courseId);
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void removesACourseWithNoEnrollments(ApplicationEvents events) throws Exception {
    // TC-CRS-014
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createCourseBody("CS201", "No Enrollments", 3)))
        .andExpect(status().isCreated());

    mockMvc.perform(delete("/api/v1/courses/CS201")).andExpect(status().isNoContent());

    Integer remaining =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM courses WHERE course_code = ?", Integer.class, "CS201");
    assertThat(remaining).isZero();

    List<CourseDeleted> published = events.stream(CourseDeleted.class).toList();
    assertThat(published).contains(new CourseDeleted(new CourseCode("CS201")));
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void removingACourseRemovesItsEnrollmentsButLeavesStudentsUntouched() throws Exception {
    // TC-CRS-015 (P0)
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createCourseBody("CS202", "With Enrollments", 3)))
        .andExpect(status().isCreated());
    Long courseId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM courses WHERE course_code = ?", Long.class, "CS202");

    Long student1 = seedStudent("S00501", "s501@example.edu");
    Long student2 = seedStudent("S00502", "s502@example.edu");
    seedEnrollment(student1, courseId);
    seedEnrollment(student2, courseId);

    mockMvc.perform(delete("/api/v1/courses/CS202")).andExpect(status().isNoContent());

    Integer remainingEnrollments =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM enrollments WHERE course_id = ?", Integer.class, courseId);
    assertThat(remainingEnrollments).isZero();

    Integer remainingStudents =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM students WHERE student_code IN (?, ?)",
            Integer.class,
            "S00501",
            "S00502");
    assertThat(remainingStudents).isEqualTo(2);
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void rejectedWhenCourseDoesNotExist() throws Exception {
    // TC-CRS-016
    mockMvc.perform(delete("/api/v1/courses/does-not-exist")).andExpect(status().isNotFound());
  }
}
