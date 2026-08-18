package org.phuchoang.management.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.StaleWriteException;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.domain.StudentCode;
import org.phuchoang.management.student.port.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-1.2 (Sprint 1) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-STU-013–020. {@code getStudent} (US-5.1) doesn't exist yet
 * this sprint, so assertions read back either the {@code PUT} response body or the database
 * directly, same as {@link StudentRegistrationIntegrationTest} already does for the account row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StudentUpdateIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StudentRepository studentRepository;

  private static String registerBody(String code, String first, String last, String email) {
    return """
        {"studentCode":"%s","firstName":"%s","lastName":"%s","email":"%s","dateOfBirth":"2000-01-01"}
        """
        .formatted(code, first, last, email);
  }

  private static String updateBody(String first, String last, String email, String dob) {
    return """
        {"firstName":"%s","lastName":"%s","email":"%s","dateOfBirth":"%s"}
        """
        .formatted(first, last, email, dob);
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void updatesNameEmailAndDob() throws Exception {
    // TC-STU-013
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00301", "Amy", "Lee", "amy.lee.301@example.edu")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/v1/students/S00301")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Amanda", "Leigh", "amanda.leigh.301@example.edu", "1999-05-05")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentCode").value("S00301"))
        .andExpect(jsonPath("$.firstName").value("Amanda"))
        .andExpect(jsonPath("$.lastName").value("Leigh"))
        .andExpect(jsonPath("$.email").value("amanda.leigh.301@example.edu"))
        .andExpect(jsonPath("$.dateOfBirth").value("1999-05-05"));

    var row =
        jdbcTemplate.queryForMap(
            "SELECT first_name, last_name, email FROM students WHERE student_code = ?", "S00301");
    assertThat(row.get("first_name")).isEqualTo("Amanda");
    assertThat(row.get("last_name")).isEqualTo("Leigh");
    assertThat(row.get("email")).isEqualTo("amanda.leigh.301@example.edu");
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsEmailThatCollidesWithAnotherStudent() throws Exception {
    // TC-STU-014
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00302", "Bob", "Smith", "bob.smith.302@example.edu")))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00303", "Carl", "Jones", "carl.jones.303@example.edu")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/v1/students/S00302")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Bob", "Smith", "carl.jones.303@example.edu", "2000-01-01")))
        .andExpect(status().isConflict());

    String email =
        jdbcTemplate.queryForObject(
            "SELECT email FROM students WHERE student_code = ?", String.class, "S00302");
    assertThat(email).isEqualTo("bob.smith.302@example.edu");
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsBlankLastName() throws Exception {
    // TC-STU-015
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00304", "Dana", "White", "dana.white.304@example.edu")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/v1/students/S00304")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Dana", "", "dana.white.304@example.edu", "2000-01-01")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectsInvalidDateOfBirth() throws Exception {
    // TC-STU-016
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00305", "Eve", "Adams", "eve.adams.305@example.edu")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/v1/students/S00305")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Eve", "Adams", "eve.adams.305@example.edu", "2023-02-30")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void studentCodeIsImmutable() throws Exception {
    // TC-STU-017 — chosen contract: UpdateStudentRequest has no studentCode field at all, so an
    // extra "studentCode" property in the JSON body is silently ignored by Jackson rather than
    // rejected; the persisted code never changes either way.
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00306", "Finn", "Cole", "finn.cole.306@example.edu")))
        .andExpect(status().isCreated());

    String bodyWithStudentCode =
        """
        {"studentCode":"S99999","firstName":"Finn","lastName":"Cole","email":"finn.cole.306@example.edu","dateOfBirth":"2000-01-01"}
        """;
    mockMvc
        .perform(
            put("/api/v1/students/S00306")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWithStudentCode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentCode").value("S00306"));

    Integer stillPresent =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM students WHERE student_code = ?", Integer.class, "S00306");
    assertThat(stillPresent).isEqualTo(1);
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void changingEmailUpdatesTheLinkedAccountsUsername() throws Exception {
    // TC-STU-018 — login isn't wired to the identity module until a later sprint
    // (SecurityConfig's UserDetailsService comment), so this checks the persisted username
    // directly rather than performing a live login as the test case's steps describe.
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00307", "Gale", "Hart", "gale.hart.307@example.edu")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/v1/students/S00307")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Gale", "Hart", "gale.new.307@example.edu", "2000-01-01")))
        .andExpect(status().isOk());

    Long studentId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM students WHERE student_code = ?", Long.class, "S00307");
    String username =
        jdbcTemplate.queryForObject(
            "SELECT username FROM users WHERE student_id = ?", String.class, studentId);
    assertThat(username).isEqualTo("gale.new.307@example.edu");
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void rejectedWhenStudentDoesNotExist() throws Exception {
    // TC-STU-019
    mockMvc
        .perform(
            put("/api/v1/students/does-not-exist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Ghost", "Nobody", "ghost.nobody@example.edu", "2000-01-01")))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void concurrentUpdateOfTheSameStudentReturns409ForTheSecondWriter() throws Exception {
    // TC-STU-020 / TC-XC-015. The API exposes no client-visible version token to race over a real
    // HTTP interleaving, so this drives StudentRepository directly to reproduce the two-readers-
    // one-stale-writer scenario deterministically: both "clients" load the same row, Client A
    // saves first, then Client B's save (still holding the pre-A version) must be rejected.
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("S00308", "Hana", "Kim", "hana.kim.308@example.edu")))
        .andExpect(status().isCreated());

    StudentCode code = new StudentCode("S00308");
    Student clientA = studentRepository.findByCode(code).orElseThrow();
    Student clientB = studentRepository.findByCode(code).orElseThrow();

    clientA.applyChanges("Hana2", "Kim2", clientA.email(), clientA.dateOfBirth());
    studentRepository.save(clientA);

    clientB.applyChanges("Hana3", "Kim3", clientB.email(), clientB.dateOfBirth());
    assertThatThrownBy(() -> studentRepository.save(clientB)).isInstanceOf(StaleWriteException.class);

    String lastName =
        jdbcTemplate.queryForObject(
            "SELECT last_name FROM students WHERE student_code = ?", String.class, "S00308");
    assertThat(lastName).isEqualTo("Kim2");
  }
}
