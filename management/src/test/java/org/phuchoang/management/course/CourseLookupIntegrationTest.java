package org.phuchoang.management.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.TestDatasource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-5.3 (Sprint 2) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level): search and detail lookup, mirroring {@link
 * org.phuchoang.management.student.StudentLookupIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CourseLookupIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;

  private static String createCourseBody(String code, String name, int credits) {
    return """
        {"courseCode":"%s","name":"%s","description":"A course.","credits":%d}
        """
        .formatted(code, name, credits);
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void searchByCodeReturnsMatchingCourse() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createCourseBody("CS301", "Data Structures", 4)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/courses").param("query", "CS301"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].courseCode").value("CS301"))
        .andExpect(jsonPath("$.content[0].name").value("Data Structures"));
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void searchWithNoMatchReturnsEmptyResultNotError() throws Exception {
    mockMvc
        .perform(get("/api/v1/courses").param("query", "no-such-course-anywhere"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextCursor").doesNotExist())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void searchResultsCanBeBrowsedPageByPageWithCursor() throws Exception {
    // Scoped to a name term unique to this test's own rows -- other test methods in this class
    // create their own courses in the same (per-class) MySQL container without cleanup between
    // tests, so an unscoped/unfiltered result would be polluted by their leftover rows.
    for (int i = 0; i < 3; i++) {
      mockMvc
          .perform(
              post("/api/v1/courses")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(createCourseBody("CS40" + i, "Paging Scope Course " + i, 3)))
          .andExpect(status().isCreated());
    }

    var firstPage =
        mockMvc
            .perform(get("/api/v1/courses").param("query", "Paging Scope Course").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.nextCursor").exists())
            .andReturn();

    String cursor =
        com.jayway.jsonpath.JsonPath.read(
            firstPage.getResponse().getContentAsString(), "$.nextCursor");

    mockMvc
        .perform(
            get("/api/v1/courses")
                .param("query", "Paging Scope Course")
                .param("size", "2")
                .param("cursor", cursor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void searchPageExactlyMatchingSizeHasNoPhantomNextCursor() throws Exception {
    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(
              post("/api/v1/courses")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(createCourseBody("CS41" + i, "Exact Fit Course " + i, 3)))
          .andExpect(status().isCreated());
    }

    mockMvc
        .perform(get("/api/v1/courses").param("query", "Exact Fit Course").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void searchWithCursorPastTheEndReturnsEmptyContentNotError() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createCourseBody("CS420", "Cursor Past End Course", 3)))
        .andExpect(status().isCreated());

    String farCursor =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("ZZZZZZZZZZ".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    mockMvc
        .perform(
            get("/api/v1/courses")
                .param("query", "Cursor Past End Course")
                .param("cursor", farCursor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void searchWithFulltextOperatorCharacterDoesNotError() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createCourseBody("CS430", "Foo Bar Course", 3)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/courses").param("query", "foo+bar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void getCourseReturnsFullDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createCourseBody("CS305", "Full Detail Course", 3)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/courses/CS305"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCode").value("CS305"))
        .andExpect(jsonPath("$.name").value("Full Detail Course"))
        .andExpect(jsonPath("$.credits").value(3))
        // The roster lives on GET /api/v1/enrollments?courseCode= now, not on this response.
        .andExpect(jsonPath("$.roster").doesNotExist())
        .andExpect(jsonPath("$.id").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void getCourseReturns404WhenCourseDoesNotExist() throws Exception {
    mockMvc.perform(get("/api/v1/courses/does-not-exist")).andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedSearchIsRejected() throws Exception {
    var result = mockMvc.perform(get("/api/v1/courses")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }
}
