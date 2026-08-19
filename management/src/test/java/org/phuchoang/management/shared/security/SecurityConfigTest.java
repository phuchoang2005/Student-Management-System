package org.phuchoang.management.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Smoke coverage for PM-006 (04-sprint-backlog.md): an unauthenticated request to a protected
 * endpoint is rejected by the filter chain itself, before it can ever reach a controller. No
 * {@code AuthenticationEntryPoint} is configured yet (no {@code formLogin()}/{@code httpBasic()}
 * DSL call in {@link SecurityConfig}), so Spring Security falls back to {@code
 * Http403ForbiddenEntryPoint} rather than the more common 401 -- the sprint backlog anticipates
 * exactly this ambiguity ("401/403"). The full RBAC matrix is exercised once the real endpoints
 * exist (PM-010).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void unauthenticatedRequestToProtectedEndpointIsRejected() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/students")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }

  @Test
  void loginEndpointIsPubliclyAccessible() throws Exception {
    // Reaches the JSON login filter (permitAll) and fails authentication there -- 401, not 403.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
  }

  /**
   * PM-016 (04-sprint-backlog.md, TC-XC-040): SYSTEM_ADMINISTRATOR is deliberately excluded from
   * the domain-read allow-list, not merely un-granted -- without the explicit GET matcher this
   * role would otherwise fall through to {@code anyRequest().authenticated()} and pass.
   */
  @Test
  @WithMockUser(roles = "SYSTEM_ADMINISTRATOR")
  void systemAdministratorHasNoDomainReadAccess() throws Exception {
    mockMvc.perform(get("/api/v1/students")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/books")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/courses")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/enrollments/1/CS101")).andExpect(status().isForbidden());
  }

  /** PM-016 (TC-XC-039): the four pre-existing domain roles keep zero access to staff-accounts. */
  @Test
  void staffAccountCreationIsForbiddenToNonSystemAdministrators() throws Exception {
    mockMvc
        .perform(post("/api/v1/staff-accounts").with(user("registrar").roles("REGISTRAR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
  }

  /** PM-016 (TC-XC-041): SYSTEM_ADMINISTRATOR is the only role admitted to staff-accounts writes. */
  @Test
  @WithMockUser(roles = "SYSTEM_ADMINISTRATOR")
  void staffAccountWritesAreReachableForSystemAdministrator() throws Exception {
    // No StaffAccountController exists yet (US-7.1/US-7.2) -- 404 proves the filter chain let the
    // request past authorization and it fell through to Spring MVC's "no handler" response,
    // rather than being rejected at the security layer with 403.
    mockMvc
        .perform(post("/api/v1/staff-accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isNotFound());
    mockMvc.perform(patch("/api/v1/staff-accounts/1")).andExpect(status().isNotFound());
  }

  /** US-5.4: /me/** is STUDENT-only -- every other role, including SYSTEM_ADMINISTRATOR, gets 403. */
  @Test
  @WithMockUser(roles = "REGISTRAR")
  void meEndpointIsForbiddenToNonStudents() throws Exception {
    mockMvc.perform(get("/api/v1/me/books-and-courses")).andExpect(status().isForbidden());
  }
}
