package org.phuchoang.management.enrollment.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.enrollment.application.EnrollmentService;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.web.GlobalExceptionHandler;
import org.phuchoang.management.student.StudentSummary;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class EnrollmentControllerTest {

  @Mock private EnrollmentService enrollmentService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    EnrollmentController controller = new EnrollmentController(enrollmentService, new EnrollmentMapperImpl());
    mockMvc =
        standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  private static EnrollmentService.EnrollmentDetailView aDetailView() {
    StudentSummary student = new StudentSummary(1L, "S00123", "Jane", "Doe", "jane.doe@example.edu");
    CourseSummary course = new CourseSummary(1L, "CS101", "Intro to CS", 3);
    return new EnrollmentService.EnrollmentDetailView(student, course, Instant.parse("2024-01-01T00:00:00Z"));
  }

  @Test
  void getEnrollmentReturns200WithStudentAndCourseSummaries() throws Exception {
    when(enrollmentService.getDetail(1L, "CS101")).thenReturn(aDetailView());

    mockMvc
        .perform(get("/api/v1/enrollments/1/CS101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.student.studentCode").value("S00123"))
        .andExpect(jsonPath("$.course.courseCode").value("CS101"))
        .andExpect(jsonPath("$.enrolledAt").exists());
  }

  @Test
  void getEnrollmentPropagatesNotFoundAs404() throws Exception {
    when(enrollmentService.getDetail(1L, "CS101"))
        .thenThrow(new NotFoundException("No active enrollment for student 1 in course 'CS101'."));

    mockMvc.perform(get("/api/v1/enrollments/1/CS101")).andExpect(status().isNotFound());
  }
}
