package org.phuchoang.management.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-6.1 (Sprint 3) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-IDN-001–005, plus the must-change-password gate US-6.1's third
 * acceptance criterion requires.
 *
 * <p>Every account here is a student account created through UC-1: staff accounts don't exist
 * until US-7.1, so the pre-seeded {@code staff-registrar-01} the test cases name isn't available
 * yet — a freshly registered student that has already changed its password stands in for it.
 *
 * <p>Registration is authorized with the {@code user(...)} request post-processor rather than
 * {@code @WithMockUser}: the latter installs a test {@code SecurityContext} for *every* request in
 * the test, which would mask the real session-cookie authentication these tests are about.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LoginIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  /** Returns the account's system-issued initial password (UC-1's identity tail). */
  private String registerStudent(String code, String email) throws Exception {
    String body =
        """
        {"studentCode":"%s","firstName":"Jane","lastName":"Doe","email":"%s","dateOfBirth":"2000-01-01"}
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
    return JsonPath.read(result.getResponse().getContentAsString(), "$.initialPassword");
  }

  private MvcResult login(String username, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
        .andReturn();
  }

  /** MockMvc doesn't emulate the container's {@code JSESSIONID} cookie, so the session is carried by hand. */
  private static MockHttpSession sessionOf(MvcResult result) {
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private void changePassword(MockHttpSession session, String current, String next)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"%s","newPassword":"%s","retypeNewPassword":"%s"}
                    """
                        .formatted(current, next, next)))
        .andExpect(status().isOk());
  }

  @Test
  void loginWithValidCredentialsOpensASessionScopedToTheAccountsRole() throws Exception {
    // TC-IDN-001
    String initialPassword = registerStudent("S00301", "login.301@example.edu");
    MvcResult firstLogin = login("login.301@example.edu", initialPassword);
    changePassword(sessionOf(firstLogin), initialPassword, "chosenSecret1");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"login.301@example.edu\",\"password\":\"chosenSecret1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andExpect(jsonPath("$.mustChangePassword").value(false))
            .andReturn();

    assertThat(sessionOf(result)).isNotNull();
  }

  @Test
  void loginWithAnUnknownUsernameIsRejected() throws Exception {
    // TC-IDN-002
    MvcResult result = login("nobody@example.edu", "whatever1");

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(result.getResponse().getContentAsString()).contains("Invalid username or password");
  }

  @Test
  void loginWithAWrongPasswordIsRejectedIndistinguishablyFromAnUnknownUsername() throws Exception {
    // TC-IDN-003 — no username-enumeration signal
    registerStudent("S00302", "login.302@example.edu");

    String unknownBody = login("nobody@example.edu", "whatever1").getResponse().getContentAsString();
    String wrongPasswordBody =
        login("login.302@example.edu", "definitelyWrong1").getResponse().getContentAsString();

    assertThat(JsonPath.<String>read(wrongPasswordBody, "$.message"))
        .isEqualTo(JsonPath.read(unknownBody, "$.message"));
    assertThat(JsonPath.<Integer>read(wrongPasswordBody, "$.status")).isEqualTo(401);
  }

  @Test
  void loginWithAnInitialPasswordReportsThatAChangeIsRequired() throws Exception {
    // TC-IDN-004
    String initialPassword = registerStudent("S00303", "login.303@example.edu");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"login.303@example.edu\",\"password\":\"%s\"}"
                        .formatted(initialPassword)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("STUDENT"))
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  void aMalformedLoginBodyFailsCleanlyRatherThanWithAServerError() throws Exception {
    // TC-IDN-005 — the JSON filter's AuthenticationServiceException path, not a 500.
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("username=nobody&password=wrong"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isIn(400, 401);
  }

  @Test
  void anAccountStillOnItsInitialPasswordCanDoNothingButChangeIt() throws Exception {
    // US-6.1 third acceptance criterion — the MustChangePasswordFilter gate.
    String initialPassword = registerStudent("S00304", "login.304@example.edu");
    MockHttpSession session = sessionOf(login("login.304@example.edu", initialPassword));

    mockMvc.perform(get("/api/v1/students").session(session)).andExpect(status().isForbidden());

    changePassword(session, initialPassword, "chosenSecret1");

    mockMvc.perform(get("/api/v1/students").session(session)).andExpect(status().isOk());
  }
}
