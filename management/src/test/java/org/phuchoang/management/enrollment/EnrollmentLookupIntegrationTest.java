package org.phuchoang.management.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Full-stack coverage of US-5.5 (Sprint 3) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-ENR detail-view cases, mirroring {@code
 * CourseLookupIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EnrollmentLookupIntegrationTest {

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

  private void enroll(long studentId, String courseCode) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .with(user("registrar").roles("REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentId":%d,"courseCode":"%s"}
                    """.formatted(studentId, courseCode)))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void getEnrollmentReturnsStudentAndCourseSummaries() throws Exception {
    // TC-ENR-011
    long studentId = registerStudent("S00601", "amy.lee.601@example.edu");
    createCourse("CS601", "Detail View Course");
    enroll(studentId, "CS601");

    mockMvc
        .perform(get("/api/v1/enrollments/" + studentId + "/CS601"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.student.studentCode").value("S00601"))
        .andExpect(jsonPath("$.student.firstName").value("Amy"))
        .andExpect(jsonPath("$.course.courseCode").value("CS601"))
        .andExpect(jsonPath("$.course.name").value("Detail View Course"))
        .andExpect(jsonPath("$.enrolledAt").exists());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void getEnrollmentIsVisibleToCourseAdministratorToo() throws Exception {
    // TC-ENR-012
    long studentId = registerStudent("S00602", "amy.lee.602@example.edu");
    createCourse("CS602", "Detail View Course");
    enroll(studentId, "CS602");

    mockMvc.perform(get("/api/v1/enrollments/" + studentId + "/CS602")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "STUDENT")
  void getEnrollmentIsVisibleToStudentToo() throws Exception {
    // TC-ENR-013
    long studentId = registerStudent("S00603", "amy.lee.603@example.edu");
    createCourse("CS603", "Detail View Course");
    enroll(studentId, "CS603");

    mockMvc.perform(get("/api/v1/enrollments/" + studentId + "/CS603")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void getEnrollmentReturns404WhenNeverEnrolled() throws Exception {
    // TC-ENR-014
    long studentId = registerStudent("S00604", "amy.lee.604@example.edu");
    createCourse("CS604", "Detail View Course");

    mockMvc.perform(get("/api/v1/enrollments/" + studentId + "/CS604")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void getEnrollmentReturns404AfterTheEnrollmentHasEnded() throws Exception {
    // TC-ENR-015 -- "ended since the list was shown" case from the US-5.5 acceptance criteria
    long studentId = registerStudent("S00605", "amy.lee.605@example.edu");
    createCourse("CS605", "Detail View Course");
    enroll(studentId, "CS605");
    mockMvc.perform(delete("/api/v1/enrollments/" + studentId + "/CS605")).andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/enrollments/" + studentId + "/CS605")).andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/enrollments/1/CS101")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }
}
