package org.phuchoang.management.shared.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.TestDatasource;
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
 * PM-010 (04-sprint-backlog.md §6) — STUDENT "own records only" scoping, {@code cross-cutting.md}
 * §1.3 (TC-XC-009/010/011) plus the analogous book/enrollment cases the backlog's PM-010 task table
 * calls for. This is a real production gap fixed alongside these tests, not pre-existing behavior:
 * {@code 02-component-diagram.md} §4 mandates STUDENT reads of {@code student}/{@code book}/{@code
 * enrollment} be scoped to {@code principal.studentId}, but only {@code /me/**} implemented it
 * before this ticket. The {@code enrollment} half has since been withdrawn rather than scoped: a
 * Student no longer reaches {@code /api/v1/enrollments/**} at all (their enrolled courses come from
 * {@code GET /api/v1/me/courses}), so the cases below assert a flat 403 there instead of scoping. Every STUDENT session here is a real registered+logged-in account (mirroring
 * {@code MeControllerIntegrationTest.aStudent}) — {@code @WithMockUser} can't carry a real {@code
 * studentId}, and that's exactly what this scoping depends on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OwnRecordsScopingIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;

  private record Student(String code, MockHttpSession session) {}

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

    return new Student(code, session);
  }

  private void addUnownedBook(String isbn) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/books")
                .with(user("librarian").roles("LIBRARIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"isbn":"%s","title":"Book %s","author":"Some Author"}
                    """.formatted(isbn, isbn)))
        .andExpect(status().isCreated());
  }

  private void addBookOwnedBy(String isbn, String ownerStudentCode) throws Exception {
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
  void aStudentReadingTheirOwnDetailSucceeds() throws Exception {
    // TC-XC-009
    Student student = aStudent("S01001", "scope.1001@example.edu");

    mockMvc
        .perform(get("/api/v1/students/" + student.code()).session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentCode").value("S01001"));
  }

  @Test
  void aStudentReadingAnotherStudentsDetailIsForbidden() throws Exception {
    // TC-XC-010 -- 403, not 404: the resource exists, only authorization fails.
    Student student = aStudent("S01002", "scope.1002@example.edu");
    Student other = aStudent("S01003", "scope.1003@example.edu");

    mockMvc
        .perform(get("/api/v1/students/" + other.code()).session(student.session()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aStudentsSearchIsTransparentlyScopedToTheirOwnRecordOnly() throws Exception {
    // TC-XC-011 -- 200 always, never 403; 0/1 results, never another student's row.
    Student student = aStudent("S01004", "scope.1004@example.edu");
    aStudent("S01005", "scope.1005@example.edu");

    mockMvc
        .perform(get("/api/v1/students").session(student.session()).param("query", "S010"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].studentCode").value("S01004"));
  }

  @Test
  void aStudentReadingTheirOwnBookDetailSucceeds() throws Exception {
    Student student = aStudent("S01006", "scope.1006@example.edu");
    addBookOwnedBy("ISBN-1006", student.code());

    mockMvc
        .perform(get("/api/v1/books/ISBN-1006").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isbn").value("ISBN-1006"));
  }

  @Test
  void aStudentReadingAnotherStudentsOwnedBookDetailIsForbidden() throws Exception {
    Student student = aStudent("S01007", "scope.1007@example.edu");
    Student other = aStudent("S01008", "scope.1008@example.edu");
    addBookOwnedBy("ISBN-1008", other.code());

    mockMvc
        .perform(get("/api/v1/books/ISBN-1008").session(student.session()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aStudentReadingAnUnownedBookDetailIsForbidden() throws Exception {
    // Resolved product decision: "own records only" means an unowned book isn't the caller's
    // record either -- 403, not 200, consistent with how student/enrollment scoping works.
    Student student = aStudent("S01009", "scope.1009@example.edu");
    addUnownedBook("ISBN-1009");

    mockMvc
        .perform(get("/api/v1/books/ISBN-1009").session(student.session()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aStudentsBookSearchIsScopedToTheirOwnBooksRegardlessOfTheOwnerStudentCodeQueryParam()
      throws Exception {
    Student student = aStudent("S01010", "scope.1010@example.edu");
    Student other = aStudent("S01011", "scope.1011@example.edu");
    addBookOwnedBy("ISBN-1010", student.code());
    addBookOwnedBy("ISBN-1011", other.code());

    // Omitting owner still narrows to the caller's own books.
    mockMvc
        .perform(get("/api/v1/books").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].isbn").value("ISBN-1010"));

    // Passing another student's code as the owner filter is silently overridden, never rejected or
    // honored.
    mockMvc
        .perform(get("/api/v1/books").session(student.session()).param("ownerStudentCode", other.code()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].isbn").value("ISBN-1010"));
  }

  @Test
  void aStudentReadsTheirOwnEnrolledCoursesThroughMeRatherThanTheEnrollmentEndpoints()
      throws Exception {
    Student student = aStudent("S01012", "scope.1012@example.edu");
    createCourse("CS1012");
    enroll(student.code(), "CS1012");

    // Not scoped -- withdrawn. Even their *own* enrollment is a 403 here, because the whole
    // resource is off the Student's read allow-list (SecurityConfig).
    mockMvc
        .perform(
            get("/api/v1/enrollments/" + student.code() + "/CS1012").session(student.session()))
        .andExpect(status().isForbidden());

    // The supported path, scoped by the session principal rather than by a code the caller types.
    mockMvc
        .perform(get("/api/v1/me/courses").session(student.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].courseCode").value("CS1012"));
  }

  @Test
  void aStudentGetsAnIdentical403ForAnotherStudentsEnrollmentWhetherOrNotItExists()
      throws Exception {
    Student student = aStudent("S01013", "scope.1013@example.edu");
    Student other = aStudent("S01014", "scope.1014@example.edu");
    createCourse("CS1013A");
    createCourse("CS1013B");
    enroll(other.code(), "CS1013A");
    // CS1013B: other student is never enrolled -- the pairing doesn't exist at all.

    // Identical 403 whether the target enrollment exists...
    mockMvc
        .perform(get("/api/v1/enrollments/" + other.code() + "/CS1013A").session(student.session()))
        .andExpect(status().isForbidden());
    // ...or doesn't -- no existence signal leaks through a differently-shaped response.
    mockMvc
        .perform(get("/api/v1/enrollments/" + other.code() + "/CS1013B").session(student.session()))
        .andExpect(status().isForbidden());
  }

  @Test
  void staffRolesAreNeverScopedRegardlessOfOwnership() throws Exception {
    // Guards against accidentally over-scoping staff callers: REGISTRAR/LIBRARIAN/
    // COURSE_ADMINISTRATOR must still see full, unscoped detail/search results.
    Student student = aStudent("S01015", "scope.1015@example.edu");
    addBookOwnedBy("ISBN-1015", student.code());

    mockMvc
        .perform(get("/api/v1/students/S01015").with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/books/ISBN-1015").with(user("librarian").roles("LIBRARIAN")))
        .andExpect(status().isOk());
    // The Librarian's student-detail page loads that student's borrowed books this way.
    mockMvc
        .perform(
            get("/api/v1/books")
                .with(user("librarian").roles("LIBRARIAN"))
                .param("ownerStudentCode", "S01015"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].isbn").value("ISBN-1015"));
    mockMvc
        .perform(get("/api/v1/students").with(user("admin").roles("COURSE_ADMINISTRATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }
}
