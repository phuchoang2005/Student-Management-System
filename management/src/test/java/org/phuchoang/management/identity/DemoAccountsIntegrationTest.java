package org.phuchoang.management.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.TestDatasource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PM-017 (04-authentication-authorization.md §8, 06-low-level-design.md §11.4) —
 * {@code app.demo-accounts.enabled} defaults to {@code true} outside {@code prod}
 * (application.properties), so this test's context runs with the endpoint and seeder both active.
 * TC-IDN-031: the endpoint's shape and content. TC-IDN-032: the 4 non-{@code STUDENT} seeded
 * identities can actually log in with the credentials the endpoint returns.
 *
 * <p>The disabled-by-config case (TC-XC-042, the route 404ing when {@code
 * app.demo-accounts.enabled=false}) is covered separately by {@link
 * DemoAccountsDisabledIntegrationTest}, which needs its own Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DemoAccountsIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void listDemoAccountsReturnsAllFiveFixedIdentities() throws Exception {
    // TC-IDN-031 — fixed order matching IdentityService.DEMO_ACCOUNTS.
    mockMvc
        .perform(get("/api/v1/auth/demo-accounts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].role").value("SYSTEM_ADMINISTRATOR"))
        .andExpect(jsonPath("$[0].username").value("demo.sysadmin"))
        .andExpect(jsonPath("$[1].role").value("REGISTRAR"))
        .andExpect(jsonPath("$[1].username").value("demo.registrar"))
        .andExpect(jsonPath("$[2].role").value("LIBRARIAN"))
        .andExpect(jsonPath("$[2].username").value("demo.librarian"))
        .andExpect(jsonPath("$[3].role").value("COURSE_ADMINISTRATOR"))
        .andExpect(jsonPath("$[3].username").value("demo.courseadmin"))
        .andExpect(jsonPath("$[4].role").value("STUDENT"))
        .andExpect(jsonPath("$[4].username").value("demo.student"))
        .andExpect(jsonPath("$[0].password").value("Demo#12345"));
  }

  @Test
  void aSeededNonStudentDemoAccountCanLogInWithTheReturnedCredentials() throws Exception {
    // TC-IDN-032
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"demo.registrar\",\"password\":\"Demo#12345\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("REGISTRAR"))
        .andExpect(jsonPath("$.mustChangePassword").value(false));
  }
}
