package org.phuchoang.management.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Full-stack coverage of US-2.4 (Sprint 2) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-BOOK-012–014. Removal never cascades (req.md §5 "When a book
 * is removed"), unlike {@code CourseService.remove}, so there's no event to assert here — just
 * that the owning student, if any, is left untouched. There's no {@code GET /books/{isbn}}
 * endpoint yet (that's US-5.2, not this sprint), so removal is verified via {@code JdbcTemplate}
 * directly, mirroring {@code CourseRemovalIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookRemovalIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String registerStudent(String code, String email) throws Exception {
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

    return JsonPath.read(result.getResponse().getContentAsString(), "$.studentCode");
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
  void removesAnUnownedBook() throws Exception {
    // TC-BOOK-012
    addBook("978-0-13-468599-1", "Clean Architecture", "Robert C. Martin");

    mockMvc.perform(delete("/api/v1/books/978-0-13-468599-1")).andExpect(status().isNoContent());

    Integer remaining =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM books WHERE isbn = ?", Integer.class, "978-0-13-468599-1");
    assertThat(remaining).isZero();
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void removingAnOwnedBookLeavesTheOwningStudentUnaffected() throws Exception {
    // TC-BOOK-013
    addBook("978-1-4919-5035-7", "Designing Data-Intensive Applications", "Martin Kleppmann");
    String ownerCode = registerStudent("S00213", "amy.lee.213@example.edu");
    long ownerId =
        ((Number)
                JsonPath.read(
                    mockMvc
                        .perform(get("/api/v1/students/" + ownerCode))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                    "$.id"))
            .longValue();
    mockMvc
        .perform(
            patch("/api/v1/books/978-1-4919-5035-7/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentId":%d}
                    """.formatted(ownerId)))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/api/v1/books/978-1-4919-5035-7")).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/students/" + ownerCode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentCode").value(ownerCode));
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void removeRejectsUnknownBook() throws Exception {
    // TC-BOOK-014
    mockMvc.perform(delete("/api/v1/books/does-not-exist")).andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result = mockMvc.perform(delete("/api/v1/books/978-0-13-468599-1")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void wrongRoleIsForbidden() throws Exception {
    mockMvc.perform(delete("/api/v1/books/978-0-13-468599-1")).andExpect(status().isForbidden());
  }
}
