package org.phuchoang.management.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.TestDatasource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TC-XC-042 — with {@code app.demo-accounts.enabled=false} (the {@code prod} value), {@code
 * DemoAccountsController} and {@code DemoAccountsSeeder} must never be registered as beans at all,
 * so the route 404s exactly like any other nonexistent path rather than 403ing behind a security
 * rule (04-authentication-authorization.md §8, 06-low-level-design.md §11.4).
 */
@SpringBootTest(properties = "app.demo-accounts.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class DemoAccountsDisabledIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void demoAccountsRouteDoesNotExistWhenTheFeatureIsDisabled() throws Exception {
    mockMvc.perform(get("/api/v1/auth/demo-accounts")).andExpect(status().isNotFound());
  }
}
