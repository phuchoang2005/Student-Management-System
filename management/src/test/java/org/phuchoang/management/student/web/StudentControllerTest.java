package org.phuchoang.management.student.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.web.GlobalExceptionHandler;
import org.phuchoang.management.student.application.StudentService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class StudentControllerTest {

  @Mock private StudentService studentService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StudentController controller = new StudentController(studentService, new StudentMapperImpl());
    mockMvc = standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  private static StudentService.ProvisionedStudent aProvisionedStudent() {
    Instant now = Instant.now();
    return new StudentService.ProvisionedStudent(
        1L,
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
}
