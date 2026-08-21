package org.phuchoang.management.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TC-XC-020–024 (cross-cutting.md §4) — cross-module cascade/lifecycle scenarios that span
 * {@code student}, {@code book}, {@code enrollment}, and {@code identity}, so they belong here
 * rather than in any single module's test package, mirroring {@code shared/security}'s PM-010/011
 * suites. The per-module removal tests ({@code StudentRemovalIntegrationTest}, {@code
 * CourseRemovalIntegrationTest}, {@code EnrollmentEndIntegrationTest}, {@code
 * BookRemovalIntegrationTest}) already cover each cascade in isolation; this class adds the
 * <em>combined</em> single-run view the spec asks for (TC-XC-020/022) and the DB-level {@code ON
 * DELETE} safety-net checks that bypass the application layer entirely (TC-XC-021/023/024).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CascadeLifecycleIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private record RegisteredStudent(long id, String code, String email, String initialPassword) {}

  private RegisteredStudent registerStudent(String code, String email) throws Exception {
    String body =
        """
        {"studentCode":"%s","firstName":"Amy","lastName":"Lee","email":"%s","dateOfBirth":"2000-01-01"}
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

    String responseBody = result.getResponse().getContentAsString();
    String initialPassword = JsonPath.read(responseBody, "$.initialPassword");
    // The surrogate id is no longer in any response (api-specification.md §5 decision #9), but the
    // raw-SQL assertions below match on it -- so it comes from the database, the one place it lives.
    return new RegisteredStudent(studentIdOf(code), code, email, initialPassword);
  }

  /** The surrogate id the raw-SQL assertions match on, read from the one place it still lives. */
  private long studentIdOf(String code) {
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM students WHERE student_code = ?", Long.class, code);
    return id == null ? -1L : id;
  }

  private void createCourse(String code, String name) throws Exception {
    String body = """
        {"courseCode":"%s","name":"%s","credits":3}
        """.formatted(code, name);

    mockMvc
        .perform(
            post("/api/v1/courses")
                .with(user("admin").roles("COURSE_ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  private void enroll(String studentCode, String courseCode) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .with(user("registrar").roles("REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentCode":"%s","courseCode":"%s"}
                    """.formatted(studentCode, courseCode)))
        .andExpect(status().isCreated());
  }

  private void addBook(String isbn, String title, String author) throws Exception {
    String body = """
        {"isbn":"%s","title":"%s","author":"%s"}
        """.formatted(isbn, title, author);

    mockMvc
        .perform(
            post("/api/v1/books")
                .with(user("librarian").roles("LIBRARIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  private void assignBookOwner(String isbn, String studentCode) throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/books/" + isbn + "/owner")
                .with(user("librarian").roles("LIBRARIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentCode":"%s"}
                    """.formatted(studentCode)))
        .andExpect(status().isOk());
  }

  private int loginStatus(String username, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  // -- TC-XC-020 -------------------------------------------------------------------------------

  @Test
  void deletingAStudentCascadesToBooksEnrollmentsAndTheirAccountInOneRun() throws Exception {
    // TC-XC-020 -- combines TC-STU-022-025 into one end-to-end run against the real API.
    RegisteredStudent student = registerStudent("S00701", "cascade.701@example.edu");
    addBook("978-0-13-110362-7", "The C Programming Language", "Kernighan & Ritchie");
    assignBookOwner("978-0-13-110362-7", student.code());
    createCourse("CS701", "Cascade Course");
    enroll(student.code(), "CS701");

    mockMvc
        .perform(
            delete("/api/v1/students/" + student.code())
                .with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isNoContent());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              Long ownerId =
                  jdbcTemplate.queryForObject(
                      "SELECT owner_id FROM books WHERE isbn = ?", Long.class, "978-0-13-110362-7");
              assertThat(ownerId).isNull();

              Integer remainingEnrollments =
                  jdbcTemplate.queryForObject(
                      "SELECT COUNT(*) FROM enrollments WHERE student_id = ?",
                      Integer.class,
                      student.id());
              assertThat(remainingEnrollments).isZero();
            });

    assertThat(loginStatus(student.email(), student.initialPassword())).isEqualTo(401);
  }

  // -- TC-XC-021 -------------------------------------------------------------------------------

  @Test
  void deletingAStudentRowDirectlyCascadesAtTheDatabaseLevel() throws Exception {
    // TC-XC-021 -- bypasses the application layer entirely (raw SQL DELETE), proving the
    // ON DELETE SET NULL / ON DELETE CASCADE constraints from V1__init_schema.sql hold
    // independent of @ApplicationModuleListener.
    RegisteredStudent student = registerStudent("S00702", "cascade.702@example.edu");
    addBook("978-0-201-63361-0", "Design Patterns", "Gang of Four");
    assignBookOwner("978-0-201-63361-0", student.code());
    createCourse("CS702", "Cascade Course");
    enroll(student.code(), "CS702");

    jdbcTemplate.update("DELETE FROM students WHERE id = ?", student.id());

    Long ownerId =
        jdbcTemplate.queryForObject(
            "SELECT owner_id FROM books WHERE isbn = ?", Long.class, "978-0-201-63361-0");
    assertThat(ownerId).isNull();

    Integer remainingEnrollments =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM enrollments WHERE student_id = ?", Integer.class, student.id());
    assertThat(remainingEnrollments).isZero();

    Integer remainingAccounts =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE student_id = ?", Integer.class, student.id());
    assertThat(remainingAccounts).isZero();
  }

  // -- TC-XC-022 -------------------------------------------------------------------------------

  @Test
  void deletingACourseCascadesToEnrollmentsVerifiedViaTheEnrollmentLookupEndpoint() throws Exception {
    // TC-XC-022 -- unlike CourseRemovalIntegrationTest's DB-only assertion, this verifies the
    // effect via the actual enrollment lookup endpoint (US-5.5), per the spec's literal steps.
    RegisteredStudent student = registerStudent("S00703", "cascade.703@example.edu");
    createCourse("CS703", "Cascade Course");
    enroll(student.code(), "CS703");

    mockMvc
        .perform(delete("/api/v1/courses/CS703").with(user("admin").roles("COURSE_ADMINISTRATOR")))
        .andExpect(status().isNoContent());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                mockMvc
                    .perform(
                        get("/api/v1/enrollments/" + student.code() + "/CS703")
                            .with(user("registrar").roles("REGISTRAR")))
                    .andExpect(status().isNotFound()));
  }

  // -- TC-XC-023 -------------------------------------------------------------------------------

  @Test
  void deletingACourseRowDirectlyCascadesAtTheDatabaseLevel() throws Exception {
    // TC-XC-023 -- raw SQL DELETE on courses, bypassing the application layer.
    RegisteredStudent student = registerStudent("S00704", "cascade.704@example.edu");
    createCourse("CS704", "Cascade Course");
    enroll(student.code(), "CS704");

    jdbcTemplate.update("DELETE FROM courses WHERE course_code = ?", "CS704");

    Integer remainingEnrollments =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM enrollments WHERE student_id = ?", Integer.class, student.id());
    assertThat(remainingEnrollments).isZero();
  }

  // -- TC-XC-024 -------------------------------------------------------------------------------

  @Test
  void deletingABookRowDirectlyHasNoCascadeEffect() throws Exception {
    // TC-XC-024 -- the direct-SQL counterpart to BookRemovalIntegrationTest's app-level check:
    // a book has no dependents (05-database-schema.md §5), so removing it touches nothing else.
    RegisteredStudent student = registerStudent("S00705", "cascade.705@example.edu");
    addBook("978-0-596-00712-6", "Head First Design Patterns", "Freeman & Freeman");
    assignBookOwner("978-0-596-00712-6", student.code());

    jdbcTemplate.update("DELETE FROM books WHERE isbn = ?", "978-0-596-00712-6");

    Integer remainingStudents =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM students WHERE id = ?", Integer.class, student.id());
    assertThat(remainingStudents).isEqualTo(1);

    Integer remainingAccounts =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE student_id = ?", Integer.class, student.id());
    assertThat(remainingAccounts).isEqualTo(1);
  }
}
