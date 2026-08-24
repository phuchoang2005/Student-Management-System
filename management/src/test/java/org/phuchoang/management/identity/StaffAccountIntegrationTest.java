package org.phuchoang.management.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.identity.domain.Username;
import org.phuchoang.management.identity.port.UserRepository;
import org.phuchoang.management.shared.TestDatasource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-7.1/US-7.2 (UC-24/UC-25) against a real MySQL 8 instance
 * (01-test-strategy.md §2's "API / contract" level) — TC-IDN-024, 026–030. TC-IDN-025 (RBAC
 * rejection) is covered by {@link org.phuchoang.management.shared.security.SecurityConfigTest}.
 *
 * <p>{@code createStaffAccount}'s response is fixed as {@code {username, role, initialPassword}}
 * (06-low-level-design.md §8.7) — no {@code id} field, so most {@code setStatus} tests below resolve
 * the numeric id via {@link UserRepository} directly, the same way {@code
 * StudentUpdateIntegrationTest} drives {@code StudentRepository}. {@code GET /api/v1/staff-accounts}
 * now also exposes that id over HTTP, which is what {@link
 * #listStaffAccountsExposesTheIdThatDeactivationRequires} covers: UC-24 → UC-25 is reachable by an
 * API client alone, without the repository back door.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StaffAccountIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;

  private static final RequestPostProcessor SYSADMIN = user("sysadmin").roles("SYSTEM_ADMINISTRATOR");

  private MvcResult createStaffAccount(String username, String role) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/staff-accounts")
                .with(SYSADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"role\":\"%s\"}".formatted(username, role)))
        .andReturn();
  }

  private MvcResult setStatus(long userId, boolean enabled) throws Exception {
    return mockMvc
        .perform(
            patch("/api/v1/staff-accounts/%d/status".formatted(userId))
                .with(SYSADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":%s}".formatted(enabled)))
        .andReturn();
  }

  private MvcResult login(String username, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
        .andReturn();
  }

  private long idOf(String username) {
    return userRepository.findByUsername(new Username(username)).orElseThrow().id().value();
  }

  @Test
  void createStaffAccountProvisionsAnEnabledAccountOnItsInitialPassword() throws Exception {
    // TC-IDN-024
    mockMvc
        .perform(
            post("/api/v1/staff-accounts")
                .with(SYSADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"staff.librarian.024\",\"role\":\"LIBRARIAN\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("staff.librarian.024"))
        .andExpect(jsonPath("$.role").value("LIBRARIAN"))
        .andExpect(jsonPath("$.initialPassword").exists());

    String initialPassword =
        JsonPath.read(
            createStaffAccount("staff.librarian.024b", "LIBRARIAN").getResponse().getContentAsString(),
            "$.initialPassword");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"staff.librarian.024b\",\"password\":\"%s\"}"
                        .formatted(initialPassword)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("LIBRARIAN"))
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  void createStaffAccountRejectsTheSystemAdministratorRole() throws Exception {
    // TC-IDN-026 — a System Administrator account is never created through the application.
    createStaffAccount("staff.sysadmin.026", "SYSTEM_ADMINISTRATOR");
    mockMvc
        .perform(
            post("/api/v1/staff-accounts")
                .with(SYSADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"staff.sysadmin.026b\",\"role\":\"SYSTEM_ADMINISTRATOR\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createStaffAccountRejectsAUsernameAlreadyInUse() throws Exception {
    // TC-IDN-027
    createStaffAccount("staff.dup.027", "REGISTRAR");

    mockMvc
        .perform(
            post("/api/v1/staff-accounts")
                .with(SYSADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"staff.dup.027\",\"role\":\"REGISTRAR\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void disablingAnActiveStaffAccountBlocksItsNextLogin() throws Exception {
    // TC-IDN-028, TC-IDN-030
    String username = "staff.disable.028";
    String initialPassword =
        JsonPath.read(
            createStaffAccount(username, "COURSE_ADMINISTRATOR").getResponse().getContentAsString(),
            "$.initialPassword");

    mockMvc
        .perform(
            patch("/api/v1/staff-accounts/%d/status".formatted(idOf(username)))
                .with(SYSADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(username))
        .andExpect(jsonPath("$.enabled").value(false));

    // TC-IDN-030 -- same generic 401 shape as an unknown username/wrong password, not a distinct
    // "account disabled" message (04-authentication-authorization.md §4.1).
    MvcResult loginAttempt = login(username, initialPassword);
    assertThat(loginAttempt.getResponse().getStatus()).isEqualTo(401);
    assertThat(loginAttempt.getResponse().getContentAsString())
        .contains("Invalid username or password");
  }

  @Test
  void reenablingADisabledStaffAccountRestoresLogin() throws Exception {
    // TC-IDN-029
    String username = "staff.reenable.029";
    String initialPassword =
        JsonPath.read(
            createStaffAccount(username, "REGISTRAR").getResponse().getContentAsString(),
            "$.initialPassword");
    long userId = idOf(username);

    setStatus(userId, false);

    mockMvc
        .perform(
            patch("/api/v1/staff-accounts/%d/status".formatted(userId))
                .with(SYSADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, initialPassword)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("REGISTRAR"));
  }

  @Test
  void setStatusOnAnUnknownAccountIsRejected() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/staff-accounts/999999/status")
                .with(SYSADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isNotFound());
  }

  /**
   * The UC-24 → UC-25 round trip driven purely over HTTP: create an account, discover its id from
   * the list, then deactivate it by that id. Before {@code GET /api/v1/staff-accounts} existed the
   * middle step was impossible for any API client, because the create response carries no id and
   * nothing else surfaced one.
   */
  @Test
  void listStaffAccountsExposesTheIdThatDeactivationRequires() throws Exception {
    String username = "staff.list.031";
    createStaffAccount(username, "LIBRARIAN");

    String listing =
        mockMvc
            .perform(get("/api/v1/staff-accounts").with(SYSADMIN).param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Number> matchedIds =
        JsonPath.read(listing, "$.content[?(@.username=='%s')].id".formatted(username));
    assertThat(matchedIds).hasSize(1);
    long listedId = matchedIds.get(0).longValue();
    assertThat(listedId).isEqualTo(idOf(username));

    // No password material is ever re-readable from the list (Identity.6).
    assertThat(listing).doesNotContain("initialPassword");

    mockMvc
        .perform(
            patch("/api/v1/staff-accounts/%d/status".formatted(listedId))
                .with(SYSADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(username))
        .andExpect(jsonPath("$.enabled").value(false));
  }

  /**
   * The listing is scoped to {@code Role.STAFF_ROLES}, so UC-25 can never be pointed at a
   * System Administrator or a Student account — neither is a staff account it governs.
   */
  @Test
  void listStaffAccountsExcludesNonStaffRoles() throws Exception {
    mockMvc
        .perform(get("/api/v1/staff-accounts").with(SYSADMIN).param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.role=='SYSTEM_ADMINISTRATOR')]").isEmpty())
        .andExpect(jsonPath("$.content[?(@.role=='STUDENT')]").isEmpty());
  }
}
