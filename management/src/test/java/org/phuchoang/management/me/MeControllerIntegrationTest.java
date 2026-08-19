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
 * Full-stack coverage of US-5.4 ({@code GET /api/v1/me/books-and-courses}) against a real MySQL 8
 * instance, mirroring {@code EnrollmentLookupIntegrationTest}. Every session is a real logged-in
 * Student — {@code @WithMockUser} can't carry a real {@code studentId}, and this endpoint's whole
 * point is scoping to {@code principal.studentId}.
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

  private record Student(long id, String studentCode, MockHttpSession session) {}

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
    long studentId = ((Number) JsonPath.read(registrationBody, "$.id")).longValue();
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

    return new Student(studentId, code, session);
  }

  private String addBook(String isbn, long ownerId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/books")
                .with(user("librarian").roles("LIBRARIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"isbn":"%s","title":"Book %s","author":"Some Author","ownerId":%d}
                    """
                        .formatted(isbn, isbn, ownerId)))
        .andExpect(status().isCreated());
    return isbn;
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

  private void enroll(long studentId, String courseCode) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .with(user("registrar").roles("REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentId":%d,"courseCode":"%s"}
                    """.formatted(studentId, courseCode)))
        .andExpect(status().isCreated());
  }

  @Test
  void ownsNoBooksAndHoldsNoEnrollmentsReturnsEmptyListsRatherThanAnError() throws Exception {
    // TC-IDN-020
    Student student = aStudent("S00701", "me.701@example.edu");

    mockMvc
        .perform(get("/api/v1/me/books-and-courses").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.books.content").isEmpty())
        .andExpect(jsonPath("$.books.totalElements").value(0))
        .andExpect(jsonPath("$.courses.content").isEmpty())
        .andExpect(jsonPath("$.courses.totalElements").value(0));
  }

  @Test
  void myBooksAndCoursesReturnsOwnedBooksAndActiveEnrollmentsAndEachSelectsIntoItsOwnDetail()
      throws Exception {
    // TC-IDN-019 — "exactly the books owned by and courses enrolled in by this student, never another student's"
    Student student = aStudent("S00702", "me.702@example.edu");
    Student otherStudent = aStudent("S00799", "me.799@example.edu");
    addBook("ISBN-702", student.id());
    addBook("ISBN-799", otherStudent.id());
    createCourse("CS702");
    createCourse("CS799");
    enroll(student.id(), "CS702");
    enroll(otherStudent.id(), "CS799");

    mockMvc
        .perform(get("/api/v1/me/books-and-courses").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.books.content.length()").value(1))
        .andExpect(jsonPath("$.books.content[0].isbn").value("ISBN-702"))
        .andExpect(jsonPath("$.books.content[0].ownerId").value(student.id()))
        .andExpect(jsonPath("$.courses.content.length()").value(1))
        .andExpect(jsonPath("$.courses.content[0].courseCode").value("CS702"));

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
    // TC-IDN-022
    Student student = aStudent("S00703", "me.703@example.edu");
    addBook("ISBN-703-A", student.id());
    addBook("ISBN-703-B", student.id());
    addBook("ISBN-703-C", student.id());
    createCourse("CS703A");
    createCourse("CS703B");
    enroll(student.id(), "CS703A");
    enroll(student.id(), "CS703B");

    mockMvc
        .perform(
            get("/api/v1/me/books-and-courses")
                .session(student.session())
                .param("booksSize", "2")
                .param("coursesSize", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.books.content.length()").value(2))
        .andExpect(jsonPath("$.books.totalElements").value(3))
        .andExpect(jsonPath("$.books.totalPages").value(2))
        .andExpect(jsonPath("$.courses.content.length()").value(1))
        .andExpect(jsonPath("$.courses.totalElements").value(2))
        .andExpect(jsonPath("$.courses.totalPages").value(2));

    // Browsing to the books' second page must not disturb the courses' first page, and vice versa.
    mockMvc
        .perform(
            get("/api/v1/me/books-and-courses")
                .session(student.session())
                .param("booksPage", "1")
                .param("booksSize", "2")
                .param("coursesSize", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.books.content.length()").value(1))
        .andExpect(jsonPath("$.courses.content.length()").value(1));
  }

  @Test
  void aNegativePageIsRejectedWith400() throws Exception {
    Student student = aStudent("S00704", "me.704@example.edu");

    mockMvc
        .perform(get("/api/v1/me/books-and-courses").session(student.session()).param("booksPage", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aSizeOutsideOneToOneHundredIsRejectedWith400() throws Exception {
    Student student = aStudent("S00705", "me.705@example.edu");

    mockMvc
        .perform(get("/api/v1/me/books-and-courses").session(student.session()).param("coursesSize", "0"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/me/books-and-courses").session(student.session()).param("coursesSize", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void nonStudentRoleIsForbiddenEvenThoughItCanReadEverythingViaOtherEndpoints() throws Exception {
    // TC-IDN-021
    mockMvc
        .perform(get("/api/v1/me/books-and-courses").with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/me/books-and-courses")).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }
}
