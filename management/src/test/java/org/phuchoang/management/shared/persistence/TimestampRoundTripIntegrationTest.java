package org.phuchoang.management.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.TestDatasource;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the {@code Instant}/{@code LocalDate} ↔ MySQL round trip to UTC — TC-XC-046–045.
 *
 * <p>Every assertion here fails without {@link JdbcConversionsConfig}, and all three failures were
 * live production bugs: audit timestamps drifted by the JVM's UTC offset on every read, that drift
 * was written back on every update so it compounded per {@code version}, and {@code date_of_birth}
 * was stored a whole day early. None of it was visible to the rest of the suite, which bound a
 * Testcontainers URL with no time-zone parameter and so wrote and read in one consistent non-UTC
 * zone (see {@code TestDatasource}).
 *
 * <p>Assertions go against the raw column as well as the API, deliberately: a self-consistent round
 * trip is exactly what the bug already produced. Only the stored wall clock proves the zone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TimestampRoundTripIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void createdAtSurvivesRepeatedUpdatesAndMatchesTheStoredUtcWallClock() throws Exception {
    // TC-XC-046
    Instant before = Instant.now();
    String created = mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"courseCode":"TZ101","name":"Time Zones","description":"A course.","credits":3}
                    """))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
    assertThat(readInstant(created, "createdAt"))
        .as("the registered instant is now, not now minus the JVM's offset from UTC")
        .isBetween(before.minusSeconds(5), Instant.now().plusSeconds(5));

    // Read the *persisted* value to compare against, not the one the POST echoed back: courses.
    // created_at is a DATETIME with no fractional-seconds precision, so MySQL truncates the
    // microseconds the in-memory Instant carries. That truncation is the schema's, not the bug's.
    Instant createdAt = readInstant(getCourse(), "createdAt");

    // Two updates: the drift compounded once per version, so one update could pass by luck.
    for (int i = 1; i <= 2; i++) {
      mockMvc
          .perform(
              put("/api/v1/courses/TZ101")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("""
                      {"name":"Time Zones %d","description":"A course.","credits":3}
                      """.formatted(i)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.createdAt").exists());
    }

    assertThat(readInstant(getCourse(), "createdAt"))
        .as("createdAt is rewound by the JVM's UTC offset on every update without the converter")
        .isEqualTo(createdAt);

    // The stored wall clock is the UTC rendering of that instant, not a local-zone one.
    LocalDateTime stored =
        jdbcTemplate.queryForObject(
            "SELECT created_at FROM courses WHERE course_code = ?", LocalDateTime.class, "TZ101");
    assertThat(stored).isNotNull();
    assertThat(Duration.between(stored.toInstant(ZoneOffset.UTC), createdAt).abs())
        .isLessThan(Duration.ofSeconds(1));
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void dateOfBirthIsStoredOnTheDayItWasSubmitted() throws Exception {
    // TC-XC-047 — at a positive UTC offset the old LocalDate write converter stored 1999-12-31.
    mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentCode":"TZ0001","firstName":"Ada","lastName":"Lovelace",
                     "email":"ada.tz@example.com","dateOfBirth":"2000-01-01"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.dateOfBirth").value("2000-01-01"));

    LocalDate stored =
        jdbcTemplate.queryForObject(
            "SELECT date_of_birth FROM students WHERE student_code = ?", LocalDate.class, "TZ0001");
    assertThat(stored).isEqualTo(LocalDate.of(2000, 1, 1));

    mockMvc
        .perform(get("/api/v1/students/TZ0001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dateOfBirth").value("2000-01-01"));
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void studentCreatedAtIsNotRewoundByAnUpdate() throws Exception {
    // TC-XC-048 — the compounding half of the bug, on the aggregate the report came in about.
    String registered = mockMvc
        .perform(
            post("/api/v1/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentCode":"TZ0002","firstName":"Grace","lastName":"Hopper",
                     "email":"grace.tz@example.com","dateOfBirth":"1990-05-05"}
                    """))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
    assertThat(readInstant(registered, "createdAt"))
        .isBetween(Instant.now().minusSeconds(30), Instant.now().plusSeconds(5));
    Instant createdAt = readInstant(getStudent(), "createdAt");

    mockMvc
        .perform(
            put("/api/v1/students/TZ0002")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Grace","lastName":"Hopper-Murray",
                     "email":"grace.tz@example.com","dateOfBirth":"1990-05-05"}
                    """))
        .andExpect(status().isOk());

    assertThat(readInstant(getStudent(), "createdAt")).isEqualTo(createdAt);
  }

  private String getCourse() throws Exception {
    return mockMvc
        .perform(get("/api/v1/courses/TZ101"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String getStudent() throws Exception {
    return mockMvc
        .perform(get("/api/v1/students/TZ0002"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private Instant readInstant(String json, String field) {
    JsonNode node = objectMapper.readTree(json).get(field);
    assertThat(node).as("%s in %s", field, json).isNotNull();
    return Instant.parse(node.asString());
  }
}
