package org.phuchoang.management.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.TestDatasource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-4.2 (Sprint 3) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-ENR-007–010, plus the {@code onStudentDeleted}/{@code
 * onCourseDeleted} cascade listeners that close the US-1.3/US-3.3 stubs
 * (06-low-level-design.md §13). Ending/cascading is verified via {@code JdbcTemplate} directly
 * rather than {@code GET /enrollments/{studentCode}/{courseCode}} (added by US-5.5, see {@code
 * EnrollmentLookupIntegrationTest}) since this test predates that endpoint, mirroring {@code
 * BookRemovalIntegrationTest}/{@code CourseRemovalIntegrationTest}. {@code
 * @ApplicationModuleListener} dispatches on a separate thread ({@code shared.async.AsyncConfig}),
 * decoupled from the publishing transaction's commit and from the triggering HTTP call returning
 * -- the two cascade tests below poll for the listener's effect with Awaitility rather than
 * asserting immediately after the {@code delete} call.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EnrollmentEndIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private record RegisteredStudent(String code) {}

  private RegisteredStudent registerStudent(String code, String email) throws Exception {
    String body =
        """
        {"studentCode":"%s","firstName":"Amy","lastName":"Lee","email":"%s","dateOfBirth":"2000-01-01"}
        """
        .formatted(code, email);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/students")
                    .with(user("registrar").roles("REGISTRAR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.studentCode"))
        .isEqualTo(code);
    return new RegisteredStudent(code);
  }

  /**
   * The surrogate id the DB-level assertions below match on. It is deliberately not available from
   * any response any more (api-specification.md §5 decision #9), so a test that needs to look at
   * the {@code enrollments} table reads it straight from the database, the same place the
   * application does.
   */
  private long studentIdOf(String code) {
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM students WHERE student_code = ?", Long.class, code);
    return id == null ? -1L : id;
  }

  private void createCourse(String code, String name) throws Exception {
    String body = """
        {"courseCode":"%s","name":"%s","credits":3}
        """.formatted(code, name);

    mockMvc
        .perform(
            post("/api/v1/courses")
                .with(user("admin").roles("COURSE_ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  private void enroll(String studentCode, String courseCode) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .with(user("registrar").roles("REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentCode":"%s","courseCode":"%s"}
                    """.formatted(studentCode, courseCode)))
        .andExpect(status().isCreated());
  }

  private int enrollmentCount(String studentCode, String courseCode) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM enrollments e "
                + "JOIN courses c ON c.id = e.course_id "
                + "JOIN students s ON s.id = e.student_id "
                + "WHERE s.student_code = ? AND c.course_code = ?",
            Integer.class,
            studentCode,
            courseCode);
    return count == null ? 0 : count;
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void endsAnActiveEnrollment() throws Exception {
    // TC-ENR-007
    RegisteredStudent student = registerStudent("S00501", "amy.lee.501@example.edu");
    createCourse("CS501", "Course");
    enroll(student.code(), "CS501");

    mockMvc.perform(delete("/api/v1/enrollments/" + student.code() + "/CS501"))
        .andExpect(status().isNoContent());

    assertThat(enrollmentCount(student.code(), "CS501")).isZero();
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void endingAnEnrollmentLeavesTheStudentAndCourseUnaffected() throws Exception {
    // TC-ENR-008
    RegisteredStudent student = registerStudent("S00502", "amy.lee.502@example.edu");
    createCourse("CS502", "Course");
    enroll(student.code(), "CS502");

    mockMvc.perform(delete("/api/v1/enrollments/" + student.code() + "/CS502"))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/students/" + student.code())).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/courses/CS502")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void endRejectsWhenNoActiveEnrollmentExistsForThePair() throws Exception {
    // TC-ENR-009
    RegisteredStudent student = registerStudent("S00503", "amy.lee.503@example.edu");
    createCourse("CS503", "Course");

    mockMvc.perform(delete("/api/v1/enrollments/" + student.code() + "/CS503"))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void aStudentMayReEnrollInACourseAfterAPriorEnrollmentThereWasEnded() throws Exception {
    // TC-ENR-010
    RegisteredStudent student = registerStudent("S00504", "amy.lee.504@example.edu");
    createCourse("CS504", "Course");
    enroll(student.code(), "CS504");
    mockMvc.perform(delete("/api/v1/enrollments/" + student.code() + "/CS504"))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentCode":"%s","courseCode":"CS504"}
                    """.formatted(student.code())))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void deletingAStudentCascadesToTheirEnrollments() throws Exception {
    RegisteredStudent student = registerStudent("S00505", "amy.lee.505@example.edu");
    createCourse("CS505", "Course");
    enroll(student.code(), "CS505");

    mockMvc.perform(delete("/api/v1/students/" + student.code())).andExpect(status().isNoContent());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(enrollmentCount(student.code(), "CS505")).isZero());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void deletingACourseCascadesToItsEnrollments() throws Exception {
    RegisteredStudent student = registerStudent("S00506", "amy.lee.506@example.edu");
    createCourse("CS506", "Course");
    enroll(student.code(), "CS506");

    mockMvc
        .perform(delete("/api/v1/courses/CS506").with(user("admin").roles("COURSE_ADMINISTRATOR")))
        .andExpect(status().isNoContent());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              Integer remaining =
                  jdbcTemplate.queryForObject(
                      "SELECT COUNT(*) FROM enrollments WHERE student_id = ?",
                      Integer.class,
                      studentIdOf(student.code()));
              assertThat(remaining).isZero();
            });
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result = mockMvc.perform(delete("/api/v1/enrollments/S00501/CS101")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void wrongRoleIsForbidden() throws Exception {
    mockMvc.perform(delete("/api/v1/enrollments/S00501/CS101")).andExpect(status().isForbidden());
  }
}
