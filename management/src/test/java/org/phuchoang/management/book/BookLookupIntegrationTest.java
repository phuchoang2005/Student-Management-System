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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-5.2 (Sprint 2) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level): search (incl. owner filter) and detail lookup, mirroring {@link
 * org.phuchoang.management.course.CourseLookupIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookLookupIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  private static String createBookBody(String isbn, String title, String author) {
    return """
        {"isbn":"%s","title":"%s","author":"%s","publishedDate":"2017-09-20"}
        """
        .formatted(isbn, title, author);
  }

  private long registerStudent(String code, String firstName, String lastName, String email) throws Exception {
    String body =
        """
        {"studentCode":"%s","firstName":"%s","lastName":"%s","email":"%s","dateOfBirth":"2000-01-01"}
        """
        .formatted(code, firstName, lastName, email);

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

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void searchByIsbnReturnsMatchingBook() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBookBody("978-0-13-235088-4", "Clean Code", "Robert C. Martin")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/books").param("query", "978-0-13-235088-4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].isbn").value("978-0-13-235088-4"))
        .andExpect(jsonPath("$.content[0].title").value("Clean Code"));
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void searchWithNoMatchReturnsEmptyResultNotError() throws Exception {
    mockMvc
        .perform(get("/api/v1/books").param("query", "no-such-book-anywhere"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void searchResultsCanBeBrowsedPageByPage() throws Exception {
    // Scoped to a title term unique to this test's own rows -- other test methods in this class
    // create their own books in the same (per-class) MySQL container without cleanup between
    // tests, so an unscoped/unfiltered count would be polluted by their leftover rows.
    for (int i = 0; i < 3; i++) {
      mockMvc
          .perform(
              post("/api/v1/books")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(createBookBody("978-0-00-000" + i + "0-0", "Paging Scope Book " + i, "Author")))
          .andExpect(status().isCreated());
    }

    mockMvc
        .perform(get("/api/v1/books").param("query", "Paging Scope Book").param("size", "2").param("page", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content.length()").value(2));

    mockMvc
        .perform(get("/api/v1/books").param("query", "Paging Scope Book").param("size", "2").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void searchCanBeFilteredByOwner() throws Exception {
    long ownerId = registerStudent("S00401", "Owner", "Filter", "owner.filter@example.edu");
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBookBody("978-0-13-597444-5", "Owned Filter Book", "Author")))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            patch("/api/v1/books/978-0-13-597444-5/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentId":%d}
                    """.formatted(ownerId)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBookBody("978-0-13-597445-2", "Unowned Filter Book", "Author")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/books").param("owner", String.valueOf(ownerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].isbn").value("978-0-13-597444-5"));
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void getBookReturnsFullDetailWithNoOwnerWhenUnowned() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBookBody("978-0-13-468599-1", "Unowned Detail Book", "Author")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/books/978-0-13-468599-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isbn").value("978-0-13-468599-1"))
        .andExpect(jsonPath("$.title").value("Unowned Detail Book"))
        .andExpect(jsonPath("$.ownerId").doesNotExist())
        .andExpect(jsonPath("$.owner").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void getBookReturnsFullDetailWithOwnerSummaryWhenOwned() throws Exception {
    long ownerId = registerStudent("S00402", "Book", "Owner", "book.owner@example.edu");
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBookBody("978-0-13-597446-9", "Owned Detail Book", "Author")))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            patch("/api/v1/books/978-0-13-597446-9/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentId":%d}
                    """.formatted(ownerId)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/books/978-0-13-597446-9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerId").value(ownerId))
        .andExpect(jsonPath("$.owner.studentCode").value("S00402"))
        .andExpect(jsonPath("$.owner.firstName").value("Book"))
        .andExpect(jsonPath("$.owner.email").value("book.owner@example.edu"));
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void getBookReturns404WhenBookDoesNotExist() throws Exception {
    mockMvc.perform(get("/api/v1/books/does-not-exist")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void getBookReturns404AfterBookIsRemoved() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBookBody("978-0-13-597447-6", "Removed Book", "Author")))
        .andExpect(status().isCreated());
    mockMvc.perform(delete("/api/v1/books/978-0-13-597447-6")).andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/books/978-0-13-597447-6")).andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedSearchIsRejected() throws Exception {
    var result = mockMvc.perform(get("/api/v1/books")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }
}
