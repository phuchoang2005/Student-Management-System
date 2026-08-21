package org.phuchoang.management.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-5.5 (Sprint 3) against a real MySQL 8 instance (01-test-strategy.md
 * §2's "API / contract" level) — TC-ENR detail-view cases, mirroring {@code
 * CourseLookupIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EnrollmentLookupIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;

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

    assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.studentCode"))
        .isEqualTo(code);
    return code;
  }

  private record LoggedInStudent(String code, MockHttpSession session) {}

  /**
   * Registers, logs in, and clears the must-change-password gate, mirroring {@code
   * MeControllerIntegrationTest.aStudent} — {@code @WithMockUser} can't carry a real {@code
   * studentId}, and TC-ENR-013 needs a genuine Student principal to exercise the filter-chain rule
   * that now keeps Students off this endpoint entirely.
   */
  private LoggedInStudent aLoggedInStudent(String code, String email) throws Exception {
    String body =
        """
        {"studentCode":"%s","firstName":"Amy","lastName":"Lee","email":"%s","dateOfBirth":"2000-01-01"}
        """
        .formatted(code, email);

    MvcResult registration =
        mockMvc
            .perform(
                post("/api/v1/students")
                    .with(user("registrar").roles("REGISTRAR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String registrationBody = registration.getResponse().getContentAsString();
    String initialPassword = JsonPath.read(registrationBody, "$.initialPassword");

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, initialPassword)))
            .andExpect(status().isOk())
            .andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"%s","newPassword":"chosenSecret1","retypeNewPassword":"chosenSecret1"}
                    """
                        .formatted(initialPassword)))
        .andExpect(status().isOk());

    return new LoggedInStudent(code, session);
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

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void getEnrollmentReturnsStudentAndCourseSummaries() throws Exception {
    // TC-ENR-011
    String studentCode = registerStudent("S00601", "amy.lee.601@example.edu");
    createCourse("CS601", "Detail View Course");
    enroll(studentCode, "CS601");

    mockMvc
        .perform(get("/api/v1/enrollments/" + studentCode + "/CS601"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.student.studentCode").value("S00601"))
        .andExpect(jsonPath("$.student.firstName").value("Amy"))
        .andExpect(jsonPath("$.course.courseCode").value("CS601"))
        .andExpect(jsonPath("$.course.name").value("Detail View Course"))
        .andExpect(jsonPath("$.enrolledAt").exists())
        .andExpect(jsonPath("$.student.id").doesNotExist())
        .andExpect(jsonPath("$.course.id").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void getEnrollmentIsVisibleToCourseAdministratorToo() throws Exception {
    // TC-ENR-012
    String studentCode = registerStudent("S00602", "amy.lee.602@example.edu");
    createCourse("CS602", "Detail View Course");
    enroll(studentCode, "CS602");

    mockMvc.perform(get("/api/v1/enrollments/" + studentCode + "/CS602")).andExpect(status().isOk());
  }

  @Test
  void aStudentCannotReachTheEnrollmentEndpointsAtAllAndUsesMeCoursesInstead() throws Exception {
    // TC-ENR-013 — a real logged-in Student session, not @WithMockUser: the latter carries no real
    // studentId, and this asserts a filter-chain rule that only a genuine STUDENT principal trips.
    // A Student has no enrollment surface of their own: their enrolled courses come from
    // GET /api/v1/me/courses, scoped by the session principal rather than by a code they type.
    LoggedInStudent student = aLoggedInStudent("S00603", "amy.lee.603@example.edu");
    createCourse("CS603", "Detail View Course");
    enroll(student.code(), "CS603");

    mockMvc
        .perform(get("/api/v1/enrollments/" + student.code() + "/CS603").session(student.session()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/enrollments").param("studentCode", student.code()).session(student.session()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/me/courses").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].courseCode").value("CS603"));
  }

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void aLibrarianCannotReachTheEnrollmentEndpointsEither() throws Exception {
    mockMvc.perform(get("/api/v1/enrollments/S00601/CS601")).andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/enrollments").param("studentCode", "S00601"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void searchByStudentCodeListsThatStudentsEnrolledCourses() throws Exception {
    String studentCode = registerStudent("S00606", "amy.lee.606@example.edu");
    createCourse("CS606A", "Course A");
    createCourse("CS606B", "Course B");
    enroll(studentCode, "CS606A");
    enroll(studentCode, "CS606B");

    mockMvc
        .perform(get("/api/v1/enrollments").param("studentCode", studentCode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].student.studentCode").value(studentCode))
        .andExpect(jsonPath("$.content[*].course.courseCode").value(hasItems("CS606A", "CS606B")));
  }

  @Test
  @WithMockUser(roles = "COURSE_ADMINISTRATOR")
  void searchByCourseCodeListsThatCoursesRoster() throws Exception {
    String first = registerStudent("S00607A", "amy.lee.607a@example.edu");
    String second = registerStudent("S00607B", "amy.lee.607b@example.edu");
    createCourse("CS607", "Shared Course");
    enroll(first, "CS607");
    enroll(second, "CS607");

    mockMvc
        .perform(get("/api/v1/enrollments").param("courseCode", "CS607"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].course.courseCode").value("CS607"))
        .andExpect(jsonPath("$.content[*].student.studentCode").value(hasItems(first, second)));
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void searchPagesTheRoster() throws Exception {
    String first = registerStudent("S00608A", "amy.lee.608a@example.edu");
    String second = registerStudent("S00608B", "amy.lee.608b@example.edu");
    createCourse("CS608", "Paged Course");
    enroll(first, "CS608");
    enroll(second, "CS608");

    mockMvc
        .perform(get("/api/v1/enrollments").param("courseCode", "CS608").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void searchRequiresExactlyOneFilter() throws Exception {
    mockMvc.perform(get("/api/v1/enrollments")).andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/enrollments").param("studentCode", "S00601").param("courseCode", "CS601"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void searchRejectsAnUnresolvableFilterValue() throws Exception {
    mockMvc
        .perform(get("/api/v1/enrollments").param("studentCode", "S00-NOBODY"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/enrollments").param("courseCode", "NOPE"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void getEnrollmentReturns404WhenNeverEnrolled() throws Exception {
    // TC-ENR-014
    String studentCode = registerStudent("S00604", "amy.lee.604@example.edu");
    createCourse("CS604", "Detail View Course");

    mockMvc.perform(get("/api/v1/enrollments/" + studentCode + "/CS604")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = "REGISTRAR")
  void getEnrollmentReturns404AfterTheEnrollmentHasEnded() throws Exception {
    // TC-ENR-015 -- "ended since the list was shown" case from the US-5.5 acceptance criteria
    String studentCode = registerStudent("S00605", "amy.lee.605@example.edu");
    createCourse("CS605", "Detail View Course");
    enroll(studentCode, "CS605");
    mockMvc.perform(delete("/api/v1/enrollments/" + studentCode + "/CS605")).andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/enrollments/" + studentCode + "/CS605")).andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/enrollments/S00601/CS101")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }
}
