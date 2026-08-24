package org.phuchoang.management.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.TestDatasource;
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
 * Full-stack coverage of UC-27/UC-28 (US-7.3, US-7.4) — TC-IDN-033–038.
 *
 * <p>{@link #aRealLoginAppearsInTheSessionList} is the one that pins the wiring: the login filter is
 * installed with {@code addFilterAt}, so no DSL configurer injects the {@code
 * SessionAuthenticationStrategy} that registers a session, and without {@code SecurityConfig}
 * setting it by hand this list would always be empty. {@link
 * #arevokedSessionIsRejectedOnItsNextRequest} pins the other half — that revocation answers 401
 * rather than the framework default of 200 with a plain-text sentence.
 *
 * <p>Sessions are carried by hand through {@code MockHttpSession}, as in {@link
 * LoginIntegrationTest}: MockMvc does not emulate the container's {@code JSESSIONID} cookie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ActiveSessionIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;

  private static int seq = 0;

  /** Registers a student and logs it in for real, returning its live session. */
  private MockHttpSession signIn() throws Exception {
    seq++;
    String email = "session.%d@example.edu".formatted(seq);
    MvcResult registered =
        mockMvc
            .perform(
                post("/api/v1/students")
                    .with(user("registrar").roles("REGISTRAR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"studentCode":"SS%04d","firstName":"Ada","lastName":"Lovelace",
                         "email":"%s","dateOfBirth":"2000-01-01"}
                        """.formatted(seq, email)))
            .andExpect(status().isCreated())
            .andReturn();
    String initialPassword =
        JsonPath.read(registered.getResponse().getContentAsString(), "$.initialPassword");

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, initialPassword)))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) login.getRequest().getSession(false);
  }

  private String listSessions() throws Exception {
    return mockMvc
        .perform(get("/api/v1/sessions").with(user("sysadmin").roles("SYSTEM_ADMINISTRATOR")))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void aRealLoginAppearsInTheSessionList() throws Exception {
    // TC-IDN-033
    MockHttpSession session = signIn();
    assertThat(session).isNotNull();

    String body = listSessions();
    List<String> usernames = JsonPath.read(body, "$[*].username");
    assertThat(usernames).contains("session.%d@example.edu".formatted(seq));

    List<String> roles = JsonPath.read(body, "$[*].role");
    assertThat(roles).contains("STUDENT");
  }

  @Test
  void theListPublishesADigestOfTheSessionIdRatherThanTheIdItself() throws Exception {
    // TC-IDN-034 — a session id is a replayable credential: anything holding one can present it as
    // a JSESSIONID cookie and become that user. Only its digest may leave the server.
    MockHttpSession session = signIn();
    String body = listSessions();
    List<String> handles = JsonPath.read(body, "$[*].handle");

    // Asserted against the handles rather than by scanning the whole body for the id as a
    // substring: MockHttpSession hands out tiny sequential ids like "20", which occur by chance
    // inside the ISO timestamps and would fail a substring check while proving nothing.
    assertThat(handles).isNotEmpty().doesNotContain(session.getId());
    assertThat(handles).allMatch(handle -> handle.matches("[0-9a-f]{64}"));

    // Stable and derived from this session, which is what makes it usable as an address.
    assertThat(handles).contains(sha256Hex(session.getId()));
  }

  private static String sha256Hex(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void arevokedSessionIsRejectedOnItsNextRequest() throws Exception {
    // TC-IDN-035 — the whole point of the feature.
    MockHttpSession session = signIn();
    String handle = handleFor(listSessions(), "session.%d@example.edu".formatted(seq));

    mockMvc
        .perform(
            delete("/api/v1/sessions/{handle}", handle)
                .with(user("sysadmin").roles("SYSTEM_ADMINISTRATOR")))
        .andExpect(status().isNoContent());

    // 401 in the standard envelope -- ConcurrentSessionFilter's default would be 200 with prose.
    mockMvc
        .perform(get("/api/v1/me/profile").session(session))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void aRevokedSessionDropsOutOfTheList() throws Exception {
    // TC-IDN-036
    signIn();
    String username = "session.%d@example.edu".formatted(seq);
    String handle = handleFor(listSessions(), username);

    mockMvc
        .perform(
            delete("/api/v1/sessions/{handle}", handle)
                .with(user("sysadmin").roles("SYSTEM_ADMINISTRATOR")))
        .andExpect(status().isNoContent());

    List<String> remaining = JsonPath.read(listSessions(), "$[*].username");
    assertThat(remaining).doesNotContain(username);
  }

  @Test
  void anUnknownHandleIsNotFound() throws Exception {
    // TC-IDN-037
    mockMvc
        .perform(
            delete("/api/v1/sessions/{handle}", "0".repeat(64))
                .with(user("sysadmin").roles("SYSTEM_ADMINISTRATOR")))
        .andExpect(status().isNotFound());
  }

  @Test
  void isClosedToEveryRoleButTheSystemAdministrator() throws Exception {
    // TC-IDN-038
    for (String role : new String[] {"REGISTRAR", "LIBRARIAN", "COURSE_ADMINISTRATOR", "STUDENT"}) {
      mockMvc
          .perform(get("/api/v1/sessions").with(user("someone").roles(role)))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(
              delete("/api/v1/sessions/{handle}", "0".repeat(64)).with(user("someone").roles(role)))
          .andExpect(status().isForbidden());
    }
  }

  private String handleFor(String body, String username) {
    List<String> handles =
        JsonPath.read(body, "$[?(@.username == '%s')].handle".formatted(username));
    assertThat(handles).as("handle for %s in %s", username, body).hasSize(1);
    return handles.get(0);
  }
}
