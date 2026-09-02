package org.phuchoang.management.enrollment.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.enrollment.application.EnrollmentBatchService;
import org.phuchoang.management.enrollment.application.EnrollmentService;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.FieldError;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.shared.web.GlobalExceptionHandler;
import org.phuchoang.management.student.StudentSummary;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class EnrollmentControllerTest {

  @Mock private EnrollmentService enrollmentService;
  @Mock private EnrollmentBatchService enrollmentBatchService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    EnrollmentController controller =
        new EnrollmentController(enrollmentService, enrollmentBatchService, new EnrollmentMapperImpl());
    mockMvc =
        standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  private static EnrollmentService.EnrollmentDetailView aDetailView() {
    StudentSummary student = new StudentSummary("S00123", "Jane", "Doe", "jane.doe@example.edu");
    CourseSummary course = new CourseSummary("CS101", "Intro to CS", 3);
    return new EnrollmentService.EnrollmentDetailView(student, course, Instant.parse("2024-01-01T00:00:00Z"));
  }

  @Test
  void getEnrollmentReturns200WithStudentAndCourseSummaries() throws Exception {
    when(enrollmentService.getDetail("S00123", "CS101")).thenReturn(aDetailView());

    mockMvc
        .perform(get("/api/v1/enrollments/S00123/CS101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.student.studentCode").value("S00123"))
        .andExpect(jsonPath("$.course.courseCode").value("CS101"))
        .andExpect(jsonPath("$.enrolledAt").exists())
        // Neither side carries a surrogate id (api-specification.md §5 decision #9).
        .andExpect(jsonPath("$.student.id").doesNotExist())
        .andExpect(jsonPath("$.course.id").doesNotExist());
  }

  @Test
  void getEnrollmentPropagatesNotFoundAs404() throws Exception {
    when(enrollmentService.getDetail("S00123", "CS101"))
        .thenThrow(new NotFoundException("No active enrollment for student 'S00123' in course 'CS101'."));

    mockMvc.perform(get("/api/v1/enrollments/S00123/CS101")).andExpect(status().isNotFound());
  }

  @Test
  void searchByStudentCodeReturnsPagedEnrollments() throws Exception {
    when(enrollmentService.search(eq("S00123"), eq(null), any(), anyInt()))
        .thenReturn(new CursorPage<>(List.of(aDetailView()), null));

    mockMvc
        .perform(get("/api/v1/enrollments").param("studentCode", "S00123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].course.courseCode").value("CS101"));
  }

  @Test
  void searchByCourseCodeReturnsPagedEnrollments() throws Exception {
    when(enrollmentService.search(eq(null), eq("CS101"), any(), anyInt()))
        .thenReturn(new CursorPage<>(List.of(aDetailView()), null));

    mockMvc
        .perform(get("/api/v1/enrollments").param("courseCode", "CS101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].student.studentCode").value("S00123"));
  }

  @Test
  void searchWithoutAFilterPropagatesValidationErrorAs400() throws Exception {
    String message = "Supply exactly one of 'studentCode' or 'courseCode'.";
    when(enrollmentService.search(eq(null), eq(null), any(), anyInt()))
        .thenThrow(new DomainValidationException(message, List.of(new FieldError("studentCode", message))));

    mockMvc
        .perform(get("/api/v1/enrollments"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("studentCode"));
  }
}
