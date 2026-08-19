package org.phuchoang.management.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Full-stack coverage of US-2.3 (Sprint 2) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-BOOK-010–011.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookUnassignmentIntegrationTest {

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

  private void addBook(String isbn, String title, String author) throws Exception {
    String body =
        """
        {"isbn":"%s","title":"%s","author":"%s"}
        """
        .formatted(isbn, title, author);

    mockMvc
        .perform(
            post("/api/v1/books")
                .with(user("librarian").roles("LIBRARIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void unassignsAnOwnedBook() throws Exception {
    // TC-BOOK-010
    addBook("978-0-13-468599-1", "Clean Architecture", "Robert C. Martin");
    long ownerId = registerStudent("S00210", "amy.lee.210@example.edu");
    mockMvc
        .perform(
            patch("/api/v1/books/978-0-13-468599-1/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentId":%d}
                    """.formatted(ownerId)))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/v1/books/978-0-13-468599-1/owner"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerId").doesNotExist())
        .andExpect(jsonPath("$.isbn").value("978-0-13-468599-1"));
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void unassigningAnAlreadyUnownedBookIsIdempotent() throws Exception {
    // TC-BOOK-011
    addBook("978-0-596-52068-7", "A Book", "An Author");

    mockMvc
        .perform(delete("/api/v1/books/978-0-596-52068-7/owner"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerId").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void unassignRejectsUnknownBook() throws Exception {
    mockMvc
        .perform(delete("/api/v1/books/does-not-exist/owner"))
        .andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result =
        mockMvc.perform(delete("/api/v1/books/978-0-13-468599-1/owner")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void wrongRoleIsForbidden() throws Exception {
    mockMvc
        .perform(delete("/api/v1/books/978-0-13-468599-1/owner"))
        .andExpect(status().isForbidden());
  }
}
