package org.phuchoang.management.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
}
