package org.phuchoang.management.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-1.3 (Sprint 1) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-STU-021, TC-STU-026. {@code getStudent} (US-5.1) doesn't
 * exist yet this sprint, so "the student is gone" is asserted directly against the database, same
 * as {@link StudentUpdateIntegrationTest} already does. {@code book}/{@code enrollment}'s cascade
 * listeners (PM-018, Sprint 4) and their own removal-cascade coverage live in {@code
 * BookRemovalIntegrationTest}/{@code EnrollmentEndIntegrationTest}, not here. {@code identity}'s
 * deprovisioning (PM-018) is synchronous — {@code IdentityService.deprovisionForStudent}, called
 * from {@code StudentService.remove} in the same transaction, not an event listener (see {@code
 * AccountProvisioning#deprovisionForStudent}'s Javadoc) — so {@code
 * removingAStudentAlsoRemovesTheLinkedUserAccount} below asserts immediately, no polling needed;
 * it can't currently distinguish that call from the DB-level {@code ON DELETE CASCADE} on {@code
 * users.student_id} (05-database-schema.md §5), which would remove the row either way.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Testcontainers
class StudentRemovalIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static String registerBody(String code, String first, String last, String email) {
    return """
        {"studentCode":"%s","firstName":"%s","lastName":"%s","email":"%s","dateOfBirth":"2000-01-01"}
        """
        .formatted(code, first, last, email);
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void removesAStudentWithNoBooksOrEnrollments(ApplicationEvents events) throws Exception {
    // TC-STU-021
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00401", "Amy", "Lee", "amy.lee.401@example.edu")))
        .andExpect(status().isCreated());

    Long studentId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM students WHERE student_code = ?", Long.class, "S00401");

    mockMvc.perform(delete("/api/v1/students/S00401")).andExpect(status().isNoContent());

    Integer remaining =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM students WHERE student_code = ?", Integer.class, "S00401");
    assertThat(remaining).isZero();

    List<StudentDeleted> published = events.stream(StudentDeleted.class).toList();
    assertThat(published).contains(new StudentDeleted(new StudentId(studentId)));
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void removingAStudentAlsoRemovesTheLinkedUserAccount() throws Exception {
    // TC-STU-024 (DB-level safety-net path — no live login yet, same caveat as
    // StudentUpdateIntegrationTest's TC-STU-018)
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00402", "Bob", "Smith", "bob.smith.402@example.edu")))
        .andExpect(status().isCreated());

    Long studentId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM students WHERE student_code = ?", Long.class, "S00402");

    mockMvc.perform(delete("/api/v1/students/S00402")).andExpect(status().isNoContent());

    Integer remainingAccounts =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE student_id = ?", Integer.class, studentId);
    assertThat(remainingAccounts).isZero();
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectedWhenStudentDoesNotExist() throws Exception {
    // TC-STU-026
    mockMvc.perform(delete("/api/v1/students/does-not-exist")).andExpect(status().isNotFound());
  }
}
