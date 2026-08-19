package org.phuchoang.management.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-6.2 (Sprint 3) against a real MySQL 8 instance — TC-IDN-006–015.
 * Sessions are carried by hand for the same reason as {@link LoginIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ChangePasswordIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** A registered student plus a logged-in session, still on its system-issued password. */
  private record Account(String username, String initialPassword, MockHttpSession session) {}

  private Account anAccount(String code, String email) throws Exception {
    String body =
        """
        {"studentCode":"%s","firstName":"Jane","lastName":"Doe","email":"%s","dateOfBirth":"2000-01-01"}
        """
            .formatted(code, email);
    MvcResult registration =
        mockMvc
            .perform(
                post("/api/v1/students")
                    .with(user("registrar").roles("REGISTRAR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String initialPassword =
        JsonPath.read(registration.getResponse().getContentAsString(), "$.initialPassword");

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, initialPassword)))
            .andExpect(status().isOk())
            .andReturn();

    return new Account(email, initialPassword, (MockHttpSession) login.getRequest().getSession(false));
  }

  private ResultActions changePassword(
      Account account, String current, String next, String retype) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/password")
            .session(account.session())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"currentPassword":"%s","newPassword":"%s","retypeNewPassword":"%s"}
                """
                    .formatted(current, next, retype)));
  }

  private int loginStatus(String username, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private String storedPasswordHash(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT password_hash FROM users WHERE username = ?", String.class, username);
  }

  @Test
  void changingThePasswordSwapsTheCredentialsTheAccountLogsInWith() throws Exception {
    // TC-IDN-006
    Account account = anAccount("S00311", "change.311@example.edu");

    changePassword(account, account.initialPassword(), "chosenSecret1", "chosenSecret1")
        .andExpect(status().isOk());

    assertThat(loginStatus(account.username(), account.initialPassword())).isEqualTo(401);
    assertThat(loginStatus(account.username(), "chosenSecret1")).isEqualTo(200);
  }

  @Test
  void aMismatchedRetypeIsRejectedAndLeavesThePasswordUntouched() throws Exception {
    // TC-IDN-007
    Account account = anAccount("S00312", "change.312@example.edu");
    String before = storedPasswordHash(account.username());

    changePassword(account, account.initialPassword(), "chosenSecret1", "chosenSecret2")
        .andExpect(status().isBadRequest());

    assertThat(storedPasswordHash(account.username())).isEqualTo(before);
  }

  @Test
  void aWrongCurrentPasswordIsRejectedAndLeavesThePasswordUntouched() throws Exception {
    // TC-IDN-008
    Account account = anAccount("S00313", "change.313@example.edu");
    String before = storedPasswordHash(account.username());

    changePassword(account, "definitelyWrong1", "chosenSecret1", "chosenSecret1")
        .andExpect(status().isUnauthorized());

    assertThat(storedPasswordHash(account.username())).isEqualTo(before);
  }

  @Test
  void aSevenCharacterNewPasswordIsRejected() throws Exception {
    // TC-IDN-009 — boundary below the §5.2 minimum
    Account account = anAccount("S00314", "change.314@example.edu");

    changePassword(account, account.initialPassword(), "short12", "short12")
        .andExpect(status().isBadRequest());
  }

  @Test
  void anEightCharacterNewPasswordIsAccepted() throws Exception {
    // TC-IDN-010 — boundary at the §5.2 minimum
    Account account = anAccount("S00315", "change.315@example.edu");

    changePassword(account, account.initialPassword(), "eight8ch", "eight8ch")
        .andExpect(status().isOk());
  }

  @Test
  void aSeventyTwoCharacterNewPasswordIsAcceptedAndNotSilentlyTruncated() throws Exception {
    // TC-IDN-011 — BCrypt's 72-byte input limit, exactly at the boundary
    Account account = anAccount("S00316", "change.316@example.edu");
    String maxLength = "a".repeat(71) + "Z";

    changePassword(account, account.initialPassword(), maxLength, maxLength)
        .andExpect(status().isOk());

    assertThat(loginStatus(account.username(), maxLength)).isEqualTo(200);
  }

  @Test
  void aSeventyThreeCharacterNewPasswordIsRejected() throws Exception {
    // TC-IDN-012 — rejected explicitly rather than truncated by BCrypt
    Account account = anAccount("S00317", "change.317@example.edu");
    String tooLong = "a".repeat(73);

    changePassword(account, account.initialPassword(), tooLong, tooLong)
        .andExpect(status().isBadRequest());
  }

  @Test
  void aNewPasswordIdenticalToTheCurrentOneIsRejected() throws Exception {
    // TC-IDN-013 — a no-op "change" must not clear the must-change-password gate
    Account account = anAccount("S00318", "change.318@example.edu");

    changePassword(
            account, account.initialPassword(), account.initialPassword(), account.initialPassword())
        .andExpect(status().isBadRequest());

    Boolean mustChange =
        jdbcTemplate.queryForObject(
            "SELECT must_change_password FROM users WHERE username = ?",
            Boolean.class,
            account.username());
    assertThat(mustChange).isTrue();
  }

  @Test
  void changingThePasswordClearsTheStoredInitialPasswordPermanently() throws Exception {
    // TC-IDN-014 — Identity.4: unrecoverable afterwards, including by the Registrar
    Account account = anAccount("S00319", "change.319@example.edu");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT initial_password_encrypted FROM users WHERE username = ?",
                String.class,
                account.username()))
        .isNotNull();

    changePassword(account, account.initialPassword(), "chosenSecret1", "chosenSecret1")
        .andExpect(status().isOk());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT initial_password_encrypted FROM users WHERE username = ?",
                String.class,
                account.username()))
        .isNull();

    mockMvc
        .perform(
            get("/api/v1/students/S00319/initial-password").with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isNotFound());
  }

  @Test
  void theSameSessionKeepsWorkingAfterTheChangeWithoutARelogin() throws Exception {
    // TC-IDN-015 — the §5.1 "refresh cached principal in session" step
    Account account = anAccount("S00320", "change.320@example.edu");

    changePassword(account, account.initialPassword(), "chosenSecret1", "chosenSecret1")
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/students").session(account.session()))
        .andExpect(status().isOk());
  }

  @Test
  void changingThePasswordRequiresAnAuthenticatedSession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"currentPassword":"whatever1","newPassword":"chosenSecret1","retypeNewPassword":"chosenSecret1"}
                        """))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }
}
