package org.phuchoang.management.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PM-013's "Event Publication Registry at-least-once delivery verification" sub-task
 * (04-sprint-backlog.md). Before this test's supporting change (adding {@code
 * spring-modulith-starter-jdbc}, {@code V4__add_event_publication_table.sql}), {@code
 * @ApplicationModuleListener} dispatch here was pure fire-and-forget {@code @Async} with no
 * persisted tracking or retry -- there was nothing a "registry" test could actually inspect. With
 * that infrastructure in place, {@code EventPublicationRegistry} durably records one row per
 * (event, listener) pair in {@code event_publication} and marks it complete only once the target
 * listener returns normally, which is what the tests below verify directly, going beyond what the
 * existing Awaitility-based cascade tests show (that the side effect eventually happened).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EventPublicationRegistryIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EventPublicationRegistry registry;
  @Autowired private IncompleteEventPublications incompletePublications;

  private String registerStudent(String code, String email) throws Exception {
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

    return code;
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

  private void createCourse(String code, String name) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/courses")
                .with(user("admin").roles("COURSE_ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"courseCode":"%s","name":"%s","credits":3}
                    """.formatted(code, name)))
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

  /**
   * Both tests in this class share one {@code EVENT_PUBLICATION} table across the whole
   * MySQL-per-class container (this class's static {@code @Container}, matching the rest of the
   * suite's convention), and {@code @ApplicationModuleListener}s fire unconditionally for every
   * matching event -- so a global {@code COUNT(*)} would double-count rows left behind by the
   * other test method. Scoping every query to {@code since} (captured right before each test's own
   * delete call) keeps the two tests' assertions isolated regardless of execution order.
   */
  private Integer completedCountFor(String listenerLike, Instant since) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM EVENT_PUBLICATION "
            + "WHERE LISTENER_ID LIKE ? AND COMPLETION_DATE IS NOT NULL AND PUBLICATION_DATE >= ?",
        Integer.class,
        listenerLike,
        java.sql.Timestamp.from(since));
  }

  @Test
  void studentDeletedIsDurablyTrackedAndCompletedForBothCascadeListeners() throws Exception {
    String studentCode = registerStudent("S00801", "registry.801@example.edu");
    // A book owned by the student and a course enrollment give both cascade listeners
    // (BookService.onStudentDeleted, EnrollmentService.onStudentDeleted) something to react to.
    addBook("978-0-262-03384-8", "Introduction to Algorithms", "CLRS");
    assignBookOwner("978-0-262-03384-8", studentCode);
    createCourse("CS801", "Registry Course");
    enroll(studentCode, "CS801");

    Instant since = Instant.now();
    mockMvc
        .perform(delete("/api/v1/students/S00801").with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isNoContent());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(completedCountFor("%BookService%onStudentDeleted%", since)).isEqualTo(1);
              assertThat(completedCountFor("%EnrollmentService%onStudentDeleted%", since)).isEqualTo(1);
            });

    assertThat(registry.findIncompletePublications())
        .noneMatch(
            pub -> {
              String target = pub.getTargetIdentifier().getValue();
              return target.contains("BookService") || target.contains("EnrollmentService");
            });
  }

  @Test
  void anIncompletePublicationIsRedeliveredAndCompletedOnResubmission() throws Exception {
    String studentCode = registerStudent("S00802", "registry.802@example.edu");
    addBook("978-0-13-468599-2", "Clean Architecture II", "Uncle Bob");
    assignBookOwner("978-0-13-468599-2", studentCode);

    Instant since = Instant.now();
    mockMvc
        .perform(delete("/api/v1/students/S00802").with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isNoContent());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> assertThat(completedCountFor("%BookService%onStudentDeleted%", since)).isEqualTo(1));

    // Simulate "the process crashed after the listener ran but before the registry could mark it
    // complete": reset that one publication's completion_date back to NULL directly in the DB.
    int rowsReset =
        jdbcTemplate.update(
            "UPDATE EVENT_PUBLICATION SET COMPLETION_DATE = NULL "
                + "WHERE LISTENER_ID LIKE ? AND PUBLICATION_DATE >= ?",
            "%BookService%onStudentDeleted%",
            java.sql.Timestamp.from(since));
    assertThat(rowsReset).isEqualTo(1);

    assertThat(registry.findIncompletePublications())
        .anyMatch(pub -> pub.getTargetIdentifier().getValue().contains("BookService"));

    incompletePublications.resubmitIncompletePublications(publication -> true);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(completedCountFor("%BookService%onStudentDeleted%", since)).isEqualTo(1);
              assertThat(registry.findIncompletePublications())
                  .noneMatch(pub -> pub.getTargetIdentifier().getValue().contains("BookService"));
            });

    // Idempotent re-delivery: the owner was already cleared, so re-running clearOwnerByStudentId
    // is a harmless no-op, not a second, different effect.
    Long ownerId =
        jdbcTemplate.queryForObject(
            "SELECT owner_id FROM books WHERE isbn = ?", Long.class, "978-0-13-468599-2");
    assertThat(ownerId).isNull();
  }
}
