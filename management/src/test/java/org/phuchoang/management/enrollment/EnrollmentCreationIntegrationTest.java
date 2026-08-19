package org.phuchoang.management.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-4.1 (Sprint 3) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-ENR-001–006.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EnrollmentCreationIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  private long registerStudent(String code, String email) throws Exception {
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

    return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
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

  private String enrollBody(long studentId, String courseCode) {
    return """
        {"studentId":%d,"courseCode":"%s"}
        """.formatted(studentId, courseCode);
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void enrollsAnExistingStudentInAnExistingCourse() throws Exception {
    // TC-ENR-001
    long studentId = registerStudent("S00401", "amy.lee.401@example.edu");
    createCourse("CS401", "Enrollment Test Course");

    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody(studentId, "CS401")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.studentId").value(studentId))
        .andExpect(jsonPath("$.courseCode").value("CS401"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.enrolledAt").exists());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsDuplicateEnrollment() throws Exception {
    // TC-ENR-002
    long studentId = registerStudent("S00402", "amy.lee.402@example.edu");
    createCourse("CS402", "Enrollment Test Course");
    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody(studentId, "CS402")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody(studentId, "CS402")))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsEnrollmentInAnUnknownCourse() throws Exception {
    // TC-ENR-003
    long studentId = registerStudent("S00403", "amy.lee.403@example.edu");

    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody(studentId, "does-not-exist")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsEnrollmentOfAnUnknownStudent() throws Exception {
    // TC-ENR-004
    createCourse("CS404", "Enrollment Test Course");

    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody(999999L, "CS404")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void aStudentMayHoldMultipleDistinctEnrollmentsSimultaneously() throws Exception {
    // TC-ENR-005
    long studentId = registerStudent("S00405", "amy.lee.405@example.edu");
    createCourse("CS405A", "Course A");
    createCourse("CS405B", "Course B");

    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody(studentId, "CS405A")))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody(studentId, "CS405B")))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void aCourseMayHaveMultipleStudentsEnrolledSimultaneously() throws Exception {
    // TC-ENR-006
    long studentA = registerStudent("S00406A", "amy.lee.406a@example.edu");
    long studentB = registerStudent("S00406B", "amy.lee.406b@example.edu");
    createCourse("CS406", "Shared Course");

    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody(studentA, "CS406")))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody(studentB, "CS406")))
        .andExpect(status().isCreated());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/enrollments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(enrollBody(1L, "CS101")))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void wrongRoleIsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(enrollBody(1L, "CS101")))
        .andExpect(status().isForbidden());
  }
}
