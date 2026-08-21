package org.phuchoang.management.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Full-stack coverage of US-3.1 (Sprint 2) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-CRS-001–007.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CourseCreationIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  private static String createBody(String code, String name, int credits) {
    return """
        {"courseCode":"%s","name":"%s","description":"A course.","credits":%d}
        """
        .formatted(code, name, credits);
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void createsCourseWithFullyValidData() throws Exception {
    // TC-CRS-001
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS101", "Intro to CS", 3)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.courseCode").value("CS101"))
        .andExpect(jsonPath("$.name").value("Intro to CS"))
        .andExpect(jsonPath("$.credits").value(3))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.createdAt").exists());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void rejectsDuplicateCourseCode() throws Exception {
    // TC-CRS-002
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS102", "Data Structures", 4)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS102", "Another Course", 3)))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void rejectsBlankName() throws Exception {
    // TC-CRS-003
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS103", "", 3)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void rejectsZeroCredits() throws Exception {
    // TC-CRS-004
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS104", "Algorithms", 0)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void rejectsNegativeCredits() throws Exception {
    // TC-CRS-005
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS105", "Databases", -1)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void acceptsCreditsAtMinimumValidValue() throws Exception {
    // TC-CRS-006
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS106", "Operating Systems", 1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.credits").value(1));
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void acceptsCourseCodeAtTwentyCharBoundary() throws Exception {
    // TC-CRS-007
    String code = "C".repeat(20);
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(code, "Boundary Course", 3)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.courseCode").value(code));
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/courses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody("CS107", "No Auth", 3)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void wrongRoleIsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS108", "Wrong Role", 3)))
        .andExpect(status().isForbidden());
  }
}
