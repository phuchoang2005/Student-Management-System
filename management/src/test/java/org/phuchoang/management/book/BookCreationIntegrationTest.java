package org.phuchoang.management.book;

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
 * Full-stack coverage of US-2.1 (Sprint 2) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-BOOK-001–005.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookCreationIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  private static String createBody(String isbn, String title, String author) {
    return """
        {"isbn":"%s","title":"%s","author":"%s","publishedDate":"2017-09-20"}
        """
        .formatted(isbn, title, author);
  }

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

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void addsBookWithNoOwner() throws Exception {
    // TC-BOOK-001
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("978-0-13-468599-1", "Clean Architecture", "Robert C. Martin")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isbn").value("978-0-13-468599-1"))
        .andExpect(jsonPath("$.title").value("Clean Architecture"))
        .andExpect(jsonPath("$.ownerStudentCode").doesNotExist());
  }

  @Test
  void addsBookWithAValidOwner() throws Exception {
    // TC-BOOK-002
    String ownerCode = registerStudent("S00201", "amy.lee.201@example.edu");

    mockMvc
        .perform(
            post("/api/v1/books")
                .with(user("librarian").roles("LIBRARIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"isbn":"978-1-4919-5035-7","title":"Designing Data-Intensive Applications","author":"Martin Kleppmann","publishedDate":"2017-03-16","ownerStudentCode":"%s"}
                    """
                        .formatted(ownerCode)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ownerStudentCode").value(ownerCode));
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void rejectsDuplicateIsbn() throws Exception {
    // TC-BOOK-003
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("978-0-596-52068-7", "A Book", "An Author")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("978-0-596-52068-7", "Another Book", "Another Author")))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void rejectsUnknownOwner() throws Exception {
    // TC-BOOK-004
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"isbn":"978-1-59327-584-6","title":"Some Book","author":"Some Author","ownerStudentCode":"S00-NOBODY"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void acceptsIsbnAtTwentyCharBoundary() throws Exception {
    // TC-BOOK-005
    String isbn = "1".repeat(20);
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(isbn, "Boundary Book", "Boundary Author")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isbn").value(isbn));
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/books")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody("978-1-118-87503-1", "No Auth", "No Auth Author")))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void wrongRoleIsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("978-1-449-37320-3", "Wrong Role", "Wrong Role Author")))
        .andExpect(status().isForbidden());
  }
}
