package org.phuchoang.management.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.port.CourseRepository;
import org.phuchoang.management.shared.exception.StaleWriteException;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-3.2 (Sprint 2) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-CRS-008–013.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CourseUpdateIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CourseRepository courseRepository;

  private static String createBody(String code, String name, int credits) {
    return """
        {"courseCode":"%s","name":"%s","description":"A course.","credits":%d}
        """
        .formatted(code, name, credits);
  }

  private static String updateBody(String name, String description, int credits) {
    return """
        {"name":"%s","description":"%s","credits":%d}
        """
        .formatted(name, description, credits);
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void updatesNameDescriptionAndCredits() throws Exception {
    // TC-CRS-008
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS201", "Intro to CS", 3)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/v1/courses/CS201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Advanced CS", "A deeper dive.", 4)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCode").value("CS201"))
        .andExpect(jsonPath("$.name").value("Advanced CS"))
        .andExpect(jsonPath("$.description").value("A deeper dive."))
        .andExpect(jsonPath("$.credits").value(4));

    var row =
        jdbcTemplate.queryForMap(
            "SELECT name, description, credits FROM courses WHERE course_code = ?", "CS201");
    assertThat(row.get("name")).isEqualTo("Advanced CS");
    assertThat(row.get("description")).isEqualTo("A deeper dive.");
    assertThat(row.get("credits")).isEqualTo(4);
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void rejectsBlankName() throws Exception {
    // TC-CRS-009
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS202", "Data Structures", 3)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/v1/courses/CS202")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("", "A deeper dive.", 4)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void rejectsNonPositiveCredits() throws Exception {
    // TC-CRS-010
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS203", "Algorithms", 3)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/v1/courses/CS203")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Algorithms II", "A deeper dive.", 0)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void courseCodeIsImmutable() throws Exception {
    // TC-CRS-011 — chosen contract: CourseUpdateRequest has no courseCode field at all, so an
    // extra "courseCode" property in the JSON body is silently ignored by Jackson rather than
    // rejected; the persisted code never changes either way.
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS204", "Databases", 3)))
        .andExpect(status().isCreated());

    String bodyWithCourseCode =
        """
        {"courseCode":"CS999","name":"Databases II","description":"A deeper dive.","credits":4}
        """;
    mockMvc
        .perform(
            put("/api/v1/courses/CS204")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWithCourseCode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCode").value("CS204"));

    Integer stillPresent =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM courses WHERE course_code = ?", Integer.class, "CS204");
    assertThat(stillPresent).isEqualTo(1);
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void rejectedWhenCourseDoesNotExist() throws Exception {
    // TC-CRS-012
    mockMvc
        .perform(
            put("/api/v1/courses/does-not-exist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Ghost Course", "A deeper dive.", 3)))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void concurrentUpdateOfTheSameCourseReturns409ForTheSecondWriter() throws Exception {
    // TC-CRS-013 / TC-XC-015. The API exposes no client-visible version token to race over a real
    // HTTP interleaving, so this drives CourseRepository directly to reproduce the two-readers-
    // one-stale-writer scenario deterministically: both "clients" load the same row, Client A
    // saves first, then Client B's save (still holding the pre-A version) must be rejected.
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CS205", "Operating Systems", 3)))
        .andExpect(status().isCreated());

    CourseCode code = new CourseCode("CS205");
    Course clientA = courseRepository.findByCode(code).orElseThrow();
    Course clientB = courseRepository.findByCode(code).orElseThrow();

    clientA.applyChanges("OS I", clientA.description(), clientA.credits());
    courseRepository.save(clientA);

    clientB.applyChanges("OS II", clientB.description(), clientB.credits());
    assertThatThrownBy(() -> courseRepository.save(clientB)).isInstanceOf(StaleWriteException.class);

    String name =
        jdbcTemplate.queryForObject(
            "SELECT name FROM courses WHERE course_code = ?", String.class, "CS205");
    assertThat(name).isEqualTo("OS I");
  }
}
