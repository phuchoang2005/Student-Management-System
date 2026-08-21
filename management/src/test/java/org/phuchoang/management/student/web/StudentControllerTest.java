package org.phuchoang.management.student.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.DuplicateEmailException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.exception.PasswordNoLongerAvailableException;
import org.phuchoang.management.shared.web.GlobalExceptionHandler;
import org.phuchoang.management.student.application.StudentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class StudentControllerTest {

  @Mock private StudentService studentService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StudentController controller = new StudentController(studentService, new StudentMapperImpl());
    mockMvc =
        standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(
                new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
            .build();
  }

  private static StudentService.ProvisionedStudent aProvisionedStudent() {
    Instant now = Instant.now();
    return new StudentService.ProvisionedStudent(
        "S00123",
        "Jane",
        "Doe",
        "jane.doe@example.edu",
        LocalDate.of(2000, 1, 1),
        now,
        now,
        "jane.doe@example.edu",
        "aB3xY9zQ");
  }

  private static final String VALID_BODY =
      """
      {"studentCode":"S00123","firstName":"Jane","lastName":"Doe","email":"jane.doe@example.edu","dateOfBirth":"2000-01-01"}
      """;

  @Test
  void registerStudentReturns201WithUsernameAndInitialPasswordExactlyOnce() throws Exception {
    when(studentService.register(any())).thenReturn(aProvisionedStudent());

    mockMvc
        .perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.studentCode").value("S00123"))
        .andExpect(jsonPath("$.username").value("jane.doe@example.edu"))
        .andExpect(jsonPath("$.initialPassword").value("aB3xY9zQ"));
  }

  @Test
  void registerStudentPropagatesDuplicateCodeAs409() throws Exception {
    when(studentService.register(any()))
        .thenThrow(new DuplicateCodeException("Student code 'S00123' is already in use."));

    mockMvc
        .perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isConflict());
  }

  @Test
  void registerStudentRejectsBlankFirstNameBeforeReachingService() throws Exception {
    String body =
        """
        {"studentCode":"S00123","firstName":"","lastName":"Doe","email":"jane.doe@example.edu","dateOfBirth":"2000-01-01"}
        """;

    mockMvc
        .perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerStudentRejectsMalformedDateOfBirth() throws Exception {
    String body =
        """
        {"studentCode":"S00123","firstName":"Jane","lastName":"Doe","email":"jane.doe@example.edu","dateOfBirth":"2023-02-30"}
        """;

    mockMvc
        .perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  private static final String VALID_UPDATE_BODY =
      """
      {"firstName":"Janet","lastName":"Roe","email":"jane.new@example.edu","dateOfBirth":"1999-05-05"}
      """;

  private static StudentService.UpdatedStudent anUpdatedStudent() {
    Instant now = Instant.now();
    return new StudentService.UpdatedStudent(
        "S00123", "Janet", "Roe", "jane.new@example.edu", LocalDate.of(1999, 5, 5), now, now);
  }

  @Test
  void updateStudentReturns200WithUpdatedFields() throws Exception {
    when(studentService.update(any(), any())).thenReturn(anUpdatedStudent());

    mockMvc
        .perform(put("/api/v1/students/S00123").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentCode").value("S00123"))
        .andExpect(jsonPath("$.firstName").value("Janet"))
        .andExpect(jsonPath("$.email").value("jane.new@example.edu"));
  }

  @Test
  void updateStudentPropagatesNotFoundAs404() throws Exception {
    when(studentService.update(any(), any()))
        .thenThrow(new NotFoundException("Student 'S00123' does not exist."));

    mockMvc
        .perform(put("/api/v1/students/S00123").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE_BODY))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateStudentPropagatesDuplicateEmailAs409() throws Exception {
    when(studentService.update(any(), any()))
        .thenThrow(new DuplicateEmailException("Email 'jane.new@example.edu' is already used by another student."));

    mockMvc
        .perform(put("/api/v1/students/S00123").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE_BODY))
        .andExpect(status().isConflict());
  }

  @Test
  void updateStudentRejectsBlankLastNameBeforeReachingService() throws Exception {
    String body =
        """
        {"firstName":"Janet","lastName":"","email":"jane.new@example.edu","dateOfBirth":"1999-05-05"}
        """;

    mockMvc
        .perform(put("/api/v1/students/S00123").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateStudentRejectsMalformedDateOfBirth() throws Exception {
    String body =
        """
        {"firstName":"Janet","lastName":"Roe","email":"jane.new@example.edu","dateOfBirth":"2023-02-30"}
        """;

    mockMvc
        .perform(put("/api/v1/students/S00123").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void removeStudentReturns204() throws Exception {
    doNothing().when(studentService).remove("S00123");

    mockMvc.perform(delete("/api/v1/students/S00123")).andExpect(status().isNoContent());

    verify(studentService).remove("S00123");
  }

  @Test
  void removeStudentPropagatesNotFoundAs404() throws Exception {
    doThrow(new NotFoundException("Student 'S00123' does not exist."))
        .when(studentService)
        .remove("S00123");

    mockMvc.perform(delete("/api/v1/students/S00123")).andExpect(status().isNotFound());
  }

  private static final StudentService.StudentSummaryView A_SUMMARY =
      new StudentService.StudentSummaryView("S00123", "Jane", "Doe", "jane.doe@example.edu");

  @Test
  void searchStudentsReturnsPagedSummaries() throws Exception {
    Page<StudentService.StudentSummaryView> page =
        new PageImpl<>(List.of(A_SUMMARY), PageRequest.of(0, 20), 1);
    when(studentService.search(any(), any(), any())).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/students").param("query", "jane"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].studentCode").value("S00123"));
  }

  @Test
  void searchStudentsReturnsEmptyContentWhenNoMatch() throws Exception {
    when(studentService.search(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(get("/api/v1/students").param("query", "nobody"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  private static StudentService.StudentDetailView aStudentDetailView() {
    Instant now = Instant.now();
    return new StudentService.StudentDetailView(
        "S00123", "Jane", "Doe", "jane.doe@example.edu", LocalDate.of(2000, 1, 1), now, now);
  }

  @Test
  void getStudentReturnsDetail() throws Exception {
    when(studentService.getDetail(eq("S00123"), any())).thenReturn(aStudentDetailView());

    mockMvc
        .perform(get("/api/v1/students/S00123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentCode").value("S00123"))
        .andExpect(jsonPath("$.email").value("jane.doe@example.edu"))
        .andExpect(jsonPath("$.dateOfBirth").value("2000-01-01"))
        // Owned books and enrolled courses are their own endpoints now, not fields here.
        .andExpect(jsonPath("$.books").doesNotExist())
        .andExpect(jsonPath("$.courses").doesNotExist())
        .andExpect(jsonPath("$.id").doesNotExist());
  }

  @Test
  void getStudentPropagatesNotFoundAs404() throws Exception {
    when(studentService.getDetail(eq("S00123"), any()))
        .thenThrow(new NotFoundException("Student 'S00123' does not exist."));

    mockMvc.perform(get("/api/v1/students/S00123")).andExpect(status().isNotFound());
  }

  @Test
  void getInitialPasswordReturnsTheUsernameAndStillUnchangedPassword() throws Exception {
    // US-6.3 / TC-IDN-016
    when(studentService.viewInitialPassword("S00123"))
        .thenReturn(new StudentService.InitialPassword("jane.doe@example.edu", "aB3xY9zQ"));

    mockMvc
        .perform(get("/api/v1/students/S00123/initial-password"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("jane.doe@example.edu"))
        .andExpect(jsonPath("$.initialPassword").value("aB3xY9zQ"));
  }

  @Test
  void getInitialPasswordPropagatesUnavailabilityAs404() throws Exception {
    // US-6.3 / TC-IDN-017, TC-IDN-018 -- no password field in the body either way
    when(studentService.viewInitialPassword("S00123"))
        .thenThrow(
            new PasswordNoLongerAvailableException(
                "No unchanged initial password found for student 'S00123'."));

    mockMvc
        .perform(get("/api/v1/students/S00123/initial-password"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.initialPassword").doesNotExist());
  }
}
