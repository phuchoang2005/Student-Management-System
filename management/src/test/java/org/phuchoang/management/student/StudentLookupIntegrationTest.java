package org.phuchoang.management.student;

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
 * Full-stack coverage of US-5.1 (Sprint 1) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level): search and detail lookup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StudentLookupIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;

  private static String registerBody(String code, String first, String last, String email) {
    return """
        {"studentCode":"%s","firstName":"%s","lastName":"%s","email":"%s","dateOfBirth":"2000-01-01"}
        """
        .formatted(code, first, last, email);
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void searchByCodeReturnsMatchingStudent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00301", "Amy", "Lee", "amy.lee.301@example.edu")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/students").param("query", "S00301"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].studentCode").value("S00301"))
        .andExpect(jsonPath("$.content[0].email").value("amy.lee.301@example.edu"));
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void searchWithNoMatchReturnsEmptyResultNotError() throws Exception {
    mockMvc
        .perform(get("/api/v1/students").param("query", "no-such-student-anywhere"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void searchResultsCanBeBrowsedPageByPage() throws Exception {
    // Scoped to a query term unique to this test's own rows -- other test methods in this class
    // register their own students in the same (per-class) MySQL container without cleanup between
    // tests, so an unscoped/unfiltered count would be polluted by their leftover rows.
    for (int i = 0; i < 3; i++) {
      mockMvc
          .perform(
              post("/api/v1/students")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(registerBody("S004" + i, "Page", "Test" + i, "paging-scope-" + i + "@example.edu")))
          .andExpect(status().isCreated());
    }

    mockMvc
        .perform(get("/api/v1/students").param("query", "paging-scope-").param("size", "2").param("page", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content.length()").value(2));

    mockMvc
        .perform(get("/api/v1/students").param("query", "paging-scope-").param("size", "2").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void getStudentReturnsFullDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00305", "Full", "Detail", "full.detail.305@example.edu")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/students/S00305"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentCode").value("S00305"))
        .andExpect(jsonPath("$.firstName").value("Full"))
        .andExpect(jsonPath("$.email").value("full.detail.305@example.edu"))
        .andExpect(jsonPath("$.dateOfBirth").exists())
        // Owned books and enrolled courses are their own endpoints now
        // (GET /api/v1/books?ownerStudentCode=, GET /api/v1/enrollments?studentCode=).
        .andExpect(jsonPath("$.books").doesNotExist())
        .andExpect(jsonPath("$.courses").doesNotExist())
        .andExpect(jsonPath("$.id").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void getStudentReturns404WhenStudentDoesNotExist() throws Exception {
    mockMvc.perform(get("/api/v1/students/S99999")).andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedSearchIsRejected() throws Exception {
    var result = mockMvc.perform(get("/api/v1/students")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }
}
