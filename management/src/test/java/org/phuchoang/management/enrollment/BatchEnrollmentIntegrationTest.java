package org.phuchoang.management.enrollment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.TestDatasource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack coverage of UC-26 / US-4.3 — TC-ENR-022–019.
 *
 * <p>The load-bearing case is {@link #enrollsTheValidCoursesEvenWhenOneInTheMiddleIsRejected}: it is
 * the whole reason {@code EnrollmentBatchService} is a separate bean rather than a loop inside
 * {@code EnrollmentService}. Written as one method there, Spring's proxy would be bypassed by
 * self-invocation and every course would share a transaction, so this test would find the successful
 * rows rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BatchEnrollmentIntegrationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    TestDatasource.bind(registry, MYSQL);
  }

  @Autowired private MockMvc mockMvc;

  private static int seq = 0;
  private String studentCode;

  @BeforeEach
  void seedStudentAndCourses() throws Exception {
    seq++;
    studentCode = "B%04d".formatted(seq);
    mockMvc
        .perform(
            post("/api/v1/students")
                .with(user("registrar").roles("REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentCode":"%s","firstName":"Amy","lastName":"Lee",
                     "email":"amy%d@example.com","dateOfBirth":"2000-01-01"}
                    """.formatted(studentCode, seq)))
        .andExpect(status().isCreated());

    for (String code : new String[] {"BA%d".formatted(seq), "BB%d".formatted(seq), "BC%d".formatted(seq)}) {
      mockMvc
          .perform(
              post("/api/v1/courses")
                  .with(user("courseadmin").roles("COURSE_ADMINISTRATOR"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("""
                      {"courseCode":"%s","name":"Course %s","credits":3}
                      """.formatted(code, code)))
          .andExpect(status().isCreated());
    }
  }

  private org.springframework.test.web.servlet.ResultActions enrollBatch(String courseCodesJson)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/enrollments/batch")
            .with(user("registrar").roles("REGISTRAR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"studentCode":"%s","courseCodes":%s}
                """.formatted(studentCode, courseCodesJson)));
  }

  @Test
  void enrollsEveryRequestedCourseInOneRequest() throws Exception {
    // TC-ENR-022
    enrollBatch("[\"BA%d\",\"BB%d\",\"BC%d\"]".formatted(seq, seq, seq))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentCode").value(studentCode))
        .andExpect(jsonPath("$.requested").value(3))
        .andExpect(jsonPath("$.enrolled").value(3))
        .andExpect(jsonPath("$.failed").value(0))
        .andExpect(jsonPath("$.results[0].status").value("ENROLLED"))
        .andExpect(jsonPath("$.results[0].enrolledAt").exists())
        .andExpect(jsonPath("$.results[0].message").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/enrollments")
                .param("studentCode", studentCode)
                .with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));
  }

  @Test
  void enrollsTheValidCoursesEvenWhenOneInTheMiddleIsRejected() throws Exception {
    // TC-ENR-023 — partial success, and the rows that succeeded stay committed.
    enrollBatch("[\"BA%d\",\"NOPE%d\",\"BC%d\"]".formatted(seq, seq, seq))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requested").value(3))
        .andExpect(jsonPath("$.enrolled").value(2))
        .andExpect(jsonPath("$.failed").value(1))
        .andExpect(jsonPath("$.results[0].status").value("ENROLLED"))
        .andExpect(jsonPath("$.results[1].status").value("UNKNOWN_COURSE"))
        .andExpect(jsonPath("$.results[1].message").exists())
        .andExpect(jsonPath("$.results[1].enrolledAt").doesNotExist())
        .andExpect(jsonPath("$.results[2].status").value("ENROLLED"));

    // The proof: read them back in a separate request. A shared transaction would have rolled
    // BA and BC back when NOPE was rejected.
    mockMvc
        .perform(
            get("/api/v1/enrollments")
                .param("studentCode", studentCode)
                .with(user("registrar").roles("REGISTRAR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  void reportsACourseTheStudentIsAlreadyEnrolledInWithoutBlockingTheRest() throws Exception {
    // TC-ENR-024
    enrollBatch("[\"BA%d\"]".formatted(seq)).andExpect(status().isOk());

    enrollBatch("[\"BA%d\",\"BB%d\"]".formatted(seq, seq))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrolled").value(1))
        .andExpect(jsonPath("$.failed").value(1))
        .andExpect(jsonPath("$.results[0].status").value("ALREADY_ENROLLED"))
        .andExpect(jsonPath("$.results[1].status").value("ENROLLED"));
  }

  @Test
  void collapsesACourseRepeatedWithinOneRequest() throws Exception {
    // TC-ENR-025 — one outcome per distinct course, not ENROLLED followed by ALREADY_ENROLLED.
    enrollBatch("[\"BA%d\",\"BA%d\"]".formatted(seq, seq))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requested").value(1))
        .andExpect(jsonPath("$.enrolled").value(1))
        .andExpect(jsonPath("$.results.length()").value(1));
  }

  @Test
  void answersAnUnknownStudentWithAWholeRequestBadRequest() throws Exception {
    // TC-ENR-026 — the student is the subject of the request, not one of its items.
    mockMvc
        .perform(
            post("/api/v1/enrollments/batch")
                .with(user("registrar").roles("REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentCode":"GHOST1","courseCodes":["BA%d"]}
                    """.formatted(seq)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.results").doesNotExist());
  }

  @Test
  void rejectsAnEmptyOrOversizedCourseList() throws Exception {
    // TC-ENR-027 — Bean Validation, in the standard ValidationError envelope.
    enrollBatch("[]")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'courseCodes')]").exists());

    StringBuilder tooMany = new StringBuilder("[");
    for (int i = 0; i < 51; i++) {
      tooMany.append(i == 0 ? "" : ",").append("\"C%d\"".formatted(i));
    }
    enrollBatch(tooMany.append("]").toString()).andExpect(status().isBadRequest());
  }

  @Test
  void isClosedToEveryRoleButTheRegistrar() throws Exception {
    // TC-ENR-028 — POST /api/v1/enrollments/** already covers /batch; this pins that it does.
    mockMvc
        .perform(
            post("/api/v1/enrollments/batch")
                .with(user("courseadmin").roles("COURSE_ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studentCode":"%s","courseCodes":["BA%d"]}
                    """.formatted(studentCode, seq)))
        .andExpect(status().isForbidden());
  }
}
