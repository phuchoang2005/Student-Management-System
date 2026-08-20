package org.phuchoang.management.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PM-011 (04-sprint-backlog.md §6) — the dedicated cross-cutting suite for the must-change-password
 * gate, {@code cross-cutting.md} §2, TC-XC-012/013/014. The gate itself ({@link
 * MustChangePasswordFilter}, {@link AuthenticatedPrincipal#mustChangePassword()}, {@code
 * AuthController}'s in-session principal refresh) was already implemented and wired into {@code
 * SecurityConfig} ahead of this ticket; this file formalizes the three test cases the backlog calls
 * for. {@code LoginIntegrationTest.anAccountStillOnItsInitialPasswordCanDoNothingButChangeIt} and
 * {@code ChangePasswordIntegrationTest.theSameSessionKeepsWorkingAfterTheChangeWithoutARelogin}
 * already exercise closely related US-6.1/US-6.2 module-level scenarios and are left in place —
 * this class is the canonical, spec-numbered cross-cutting coverage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MustChangePasswordGateIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
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
                    .with(SecurityMockMvcRequestPostProcessors.user("registrar").roles("REGISTRAR"))
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

  private static MockHttpSession sessionOf(MvcResult result) {
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private void changePassword(MockHttpSession session, String current, String next) throws Exception {
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
  void anAccountOnItsInitialPasswordCanReachOnlyThePasswordChangeEndpoint() throws Exception {
    // TC-XC-012. STUDENT has zero write endpoints anywhere in the API (TC-XC-005) -- asserting
    // a write endpoint is 403 here would be indistinguishable from ordinary RBAC denial, not gate
    // denial. Two representative otherwise-allowed reads are used instead: the general students
    // list (allowed to every domain role) and /me/books-and-courses (STUDENT's own dedicated
    // endpoint) -- both must be blocked purely by the gate while mustChangePassword is true.
    String initialPassword = registerStudent("S00901", "gate.901@example.edu");
    MockHttpSession session = sessionOf(login("gate.901@example.edu", initialPassword));

    mockMvc.perform(get("/api/v1/students").session(session)).andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/me/books-and-courses").session(session))
        .andExpect(status().isForbidden());
  }

  @Test
  @Disabled(
      "TC-XC-013 (cross-cutting.md §2): only reachable once a staff-provisioning flow can set "
          + "mustChangePassword=true for a non-Student role; no such flow exists yet "
          + "(04-sprint-backlog.md PM-010/PM-011). Re-enable once staff account creation supports it.")
  void theGateAppliesToStaffRolesTooNotOnlyStudent() {}

  @Test
  void theGateClearsImmediatelyAfterASuccessfulChangeInTheSameSessionAndUnblocksThePreviouslyBlockedEndpoints()
      throws Exception {
    // TC-XC-014, composed with TC-XC-012's two representative endpoints.
    String initialPassword = registerStudent("S00902", "gate.902@example.edu");
    MockHttpSession session = sessionOf(login("gate.902@example.edu", initialPassword));

    mockMvc.perform(get("/api/v1/students").session(session)).andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/me/books-and-courses").session(session))
        .andExpect(status().isForbidden());

    changePassword(session, initialPassword, "chosenSecret1");

    mockMvc.perform(get("/api/v1/students").session(session)).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/me/books-and-courses").session(session)).andExpect(status().isOk());
  }
}
