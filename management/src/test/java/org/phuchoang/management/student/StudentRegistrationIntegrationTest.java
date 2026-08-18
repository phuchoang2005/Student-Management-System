package org.phuchoang.management.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-1.1 (Sprint 1) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-STU-001–010.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StudentRegistrationIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  private static String registerBody(String code, String first, String last, String email) {
    return """
        {"studentCode":"%s","firstName":"%s","lastName":"%s","email":"%s","dateOfBirth":"2000-01-01"}
        """
        .formatted(code, first, last, email);
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void registrationAutoProvisionsExactlyOneAccountWithHashedOneTimePassword() throws Exception {
    // TC-STU-001, TC-STU-008, TC-STU-009, TC-STU-010
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/students")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody("S00201", "Jane", "Doe", "jane.doe.201@example.edu")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.studentCode").value("S00201"))
            .andExpect(jsonPath("$.username").value("jane.doe.201@example.edu"))
            .andExpect(jsonPath("$.initialPassword").exists())
            .andReturn();

    String initialPassword =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.initialPassword");
    assertThat(initialPassword).hasSize(8).matches("[A-Za-z0-9]{8}");

    Long studentId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM students WHERE student_code = ?", Long.class, "S00201");

    var accounts =
        jdbcTemplate.queryForList(
            "SELECT username, role, must_change_password, password_hash FROM users WHERE student_id = ?",
            studentId);
    assertThat(accounts).hasSize(1);
    Map<String, Object> account = accounts.get(0);
    assertThat(account.get("username")).isEqualTo("jane.doe.201@example.edu");
    assertThat(account.get("role")).isEqualTo("STUDENT");
    assertThat(account.get("must_change_password")).isEqualTo(true);
    assertThat(passwordEncoder.matches(initialPassword, (String) account.get("password_hash"))).isTrue();
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsDuplicateStudentCode() throws Exception {
    // TC-STU-002
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00202", "John", "Roe", "john.roe.202@example.edu")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00202", "Other", "Person", "other.person.202@example.edu")))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsDuplicateEmail() throws Exception {
    // TC-STU-003
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00203", "Amy", "Lee", "amy.lee.203@example.edu")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00204", "Amy2", "Lee2", "amy.lee.203@example.edu")))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsMalformedEmail() throws Exception {
    // TC-STU-004
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00205", "Bob", "Smith", "not-an-email")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsBlankFirstName() throws Exception {
    // TC-STU-005
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00206", "", "Smith", "blank.first.206@example.edu")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsBlankLastName() throws Exception {
    // TC-STU-006
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00207", "Bob", "", "blank.last.207@example.edu")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/students")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody("S00208", "No", "Auth", "no.auth.208@example.edu")))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }

  @Test
  @WithMockUser(roles = "STUDENT")
  void wrongRoleIsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00209", "Wrong", "Role", "wrong.role.209@example.edu")))
        .andExpect(status().isForbidden());
  }
}
