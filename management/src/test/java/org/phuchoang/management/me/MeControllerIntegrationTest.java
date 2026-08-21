package org.phuchoang.management.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of US-5.4 ({@code GET /api/v1/me/profile}, {@code /me/books}, {@code
 * /me/courses}) against a real MySQL 8 instance, mirroring {@code
 * EnrollmentLookupIntegrationTest}. Every session is a real logged-in Student —
 * {@code @WithMockUser} can't carry a real {@code studentId}, and these endpoints' whole point is
 * scoping to {@code principal.studentId}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MeControllerIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  private record Student(String studentCode, String email, MockHttpSession session) {}

  /** Registers, logs in, and clears the must-change-password gate so the session can call anything. */
  private Student aStudent(String code, String email) throws Exception {
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

    return new Student(code, email, session);
  }

  private void addBook(String isbn, String ownerStudentCode) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/books")
                .with(user("librarian").roles("LIBRARIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"isbn":"%s","title":"Book %s","author":"Some Author","ownerStudentCode":"%s"}
                    """
                        .formatted(isbn, isbn, ownerStudentCode)))
        .andExpect(status().isCreated());
  }

  private void createCourse(String code) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/courses")
                .with(user("admin").roles("COURSE_ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"courseCode":"%s","name":"Course %s","credits":3}
                    """.formatted(code, code)))
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
  void profileReturnsTheCallersOwnRecordIncludingTheStudentCodeLoginNeverRevealed() throws Exception {
    Student student = aStudent("S00700", "me.700@example.edu");

    mockMvc
        .perform(get("/api/v1/me/profile").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentCode").value("S00700"))
        .andExpect(jsonPath("$.firstName").value("Amy"))
        .andExpect(jsonPath("$.email").value("me.700@example.edu"))
        .andExpect(jsonPath("$.dateOfBirth").value("2000-01-01"))
        .andExpect(jsonPath("$.id").doesNotExist());
  }

  @Test
  void ownsNoBooksAndHoldsNoEnrollmentsReturnsEmptyPagesRatherThanAnError() throws Exception {
    // TC-IDN-020
    Student student = aStudent("S00701", "me.701@example.edu");

    mockMvc
        .perform(get("/api/v1/me/books").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));
    mockMvc
        .perform(get("/api/v1/me/courses").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void myBooksAndCoursesReturnOwnedBooksAndActiveEnrollmentsAndEachSelectsIntoItsOwnDetail()
      throws Exception {
    // TC-IDN-019 — "exactly the books owned by and courses enrolled in by this student, never another student's"
    Student student = aStudent("S00702", "me.702@example.edu");
    Student otherStudent = aStudent("S00799", "me.799@example.edu");
    addBook("ISBN-702", student.studentCode());
    addBook("ISBN-799", otherStudent.studentCode());
    createCourse("CS702");
    createCourse("CS799");
    enroll(student.studentCode(), "CS702");
    enroll(otherStudent.studentCode(), "CS799");

    mockMvc
        .perform(get("/api/v1/me/books").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].isbn").value("ISBN-702"));
    mockMvc
        .perform(get("/api/v1/me/courses").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].courseCode").value("CS702"));

    // "when I select one entry, then the system displays that book's/course's full detail"
    mockMvc
        .perform(get("/api/v1/books/ISBN-702").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isbn").value("ISBN-702"));
    mockMvc
        .perform(get("/api/v1/courses/CS702").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCode").value("CS702"));
  }

  @Test
  void booksAndCoursesPageIndependentlyOfEachOther() throws Exception {
    // TC-IDN-022 — trivially true now that each collection is its own request with its own
    // page/size, which is the reason the composed endpoint was split.
    Student student = aStudent("S00703", "me.703@example.edu");
    addBook("ISBN-703-A", student.studentCode());
    addBook("ISBN-703-B", student.studentCode());
    addBook("ISBN-703-C", student.studentCode());
    createCourse("CS703A");
    createCourse("CS703B");
    enroll(student.studentCode(), "CS703A");
    enroll(student.studentCode(), "CS703B");

    mockMvc
        .perform(get("/api/v1/me/books").session(student.session()).param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2));

    mockMvc
        .perform(get("/api/v1/me/books").session(student.session()).param("page", "1").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));

    mockMvc
        .perform(get("/api/v1/me/courses").session(student.session()).param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void nonStudentRoleIsForbiddenEvenThoughItCanReadEverythingViaOtherEndpoints() throws Exception {
    // TC-IDN-021
    mockMvc
        .perform(get("/api/v1/me/profile").with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/me/books").with(user("librarian").roles("LIBRARIAN")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/me/courses").with(user("admin").roles("COURSE_ADMINISTRATOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/me/profile")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }
}
