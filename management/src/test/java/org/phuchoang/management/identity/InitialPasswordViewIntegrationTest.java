package org.phuchoang.management.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
 * Full-stack coverage of US-6.3 (Sprint 3) against a real MySQL 8 instance — TC-IDN-016–018.
 * The endpoint is served by {@code StudentController} rather than {@code AuthController}; see
 * {@link InitialPasswordLookup} for why, and note that neither its path nor its contract changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InitialPasswordViewIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;

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

  private void changePassword(String email, String initialPassword) throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, initialPassword)))
            .andExpect(status().isOk())
            .andReturn();

    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .session((MockHttpSession) login.getRequest().getSession(false))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"%s","newPassword":"chosenSecret1","retypeNewPassword":"chosenSecret1"}
                    """
                        .formatted(initialPassword)))
        .andExpect(status().isOk());
  }

  @Test
  void registrarReadsBackAStillUnchangedInitialPassword() throws Exception {
    // TC-IDN-016 — the value returned is the same one issued at registration
    String initialPassword = registerStudent("S00331", "initial.331@example.edu");

    mockMvc
        .perform(
            get("/api/v1/students/S00331/initial-password")
                .with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("initial.331@example.edu"))
        .andExpect(jsonPath("$.initialPassword").value(initialPassword));
  }

  @Test
  void anAlreadyChangedPasswordIsNoLongerReadableByAnyone() throws Exception {
    // TC-IDN-017 — Identity.4
    String initialPassword = registerStudent("S00332", "initial.332@example.edu");
    changePassword("initial.332@example.edu", initialPassword);

    mockMvc
        .perform(
            get("/api/v1/students/S00332/initial-password")
                .with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.initialPassword").doesNotExist());
  }

  @Test
  void anAlreadyChangedPasswordAndAnUnknownStudentAreIndistinguishable() throws Exception {
    // TC-IDN-018 — api-specification.md §5.5's deliberate information-hiding
    String initialPassword = registerStudent("S00333", "initial.333@example.edu");
    changePassword("initial.333@example.edu", initialPassword);

    String changed =
        mockMvc
            .perform(
                get("/api/v1/students/S00333/initial-password")
                    .with(user("registrar").roles("REGISTRAR")))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String neverExisted =
        mockMvc
            .perform(
                get("/api/v1/students/S00999/initial-password")
                    .with(user("registrar").roles("REGISTRAR")))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The bodies differ only in the code the caller themselves supplied -- nothing in either
    // distinguishes "changed" from "never existed".
    assertThat(JsonPath.<String>read(changed, "$.message").replace("S00333", "CODE"))
        .isEqualTo(JsonPath.<String>read(neverExisted, "$.message").replace("S00999", "CODE"));
    assertThat(JsonPath.<String>read(changed, "$.error"))
        .isEqualTo(JsonPath.read(neverExisted, "$.error"));
  }

  @Test
  void aNonRegistrarRoleIsForbidden() throws Exception {
    registerStudent("S00334", "initial.334@example.edu");

    mockMvc
        .perform(
            get("/api/v1/students/S00334/initial-password")
                .with(user("librarian").roles("LIBRARIAN")))
        .andExpect(status().isForbidden());
  }

  @Test
  void anUnauthenticatedCallerIsRejected() throws Exception {
    MvcResult result =
        mockMvc.perform(get("/api/v1/students/S00335/initial-password")).andReturn();

    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }
}
