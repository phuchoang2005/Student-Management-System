package org.phuchoang.management.shared.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.DuplicateCodeException;
import org.phuchoang.management.shared.exception.FieldError;
import org.phuchoang.management.shared.exception.InvalidCredentialsException;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One case per {@code ApiException} HTTP-status branch (400/401/403/404/409), per
 * 04-sprint-backlog.md PM-005, plus the {@code ValidationError} field-level variant.
 */
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        standaloneSetup(new ThrowingController()).setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @Test
  void domainValidationExceptionMapsTo400() throws Exception {
    mockMvc
        .perform(get("/throw").param("type", "domainValidation"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("invalid date of birth"))
        .andExpect(jsonPath("$.path").value("/throw"));
  }

  @Test
  void domainValidationExceptionWithFieldErrorsMapsToValidationError() throws Exception {
    mockMvc
        .perform(get("/throw").param("type", "fieldErrors"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].field").value("email"))
        .andExpect(jsonPath("$.errors[0].message").value("must be a well-formed email address"));
  }

  @Test
  void beanValidationFailureMapsToValidationError() throws Exception {
    mockMvc
        .perform(
            post("/throw/valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  void malformedRequestBodyMapsTo400() throws Exception {
    mockMvc
        .perform(
            post("/throw/date")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2023-02-30\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"));
  }

  @Test
  void invalidCredentialsExceptionMapsTo401() throws Exception {
    mockMvc
        .perform(get("/throw").param("type", "unauthorized"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("Unauthorized"));
  }

  @Test
  void accessDeniedExceptionMapsTo403() throws Exception {
    mockMvc
        .perform(get("/throw").param("type", "forbidden"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("Forbidden"));
  }

  @Test
  void notFoundExceptionMapsTo404() throws Exception {
    mockMvc
        .perform(get("/throw").param("type", "notFound"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"));
  }

  @Test
  void duplicateCodeExceptionMapsTo409() throws Exception {
    mockMvc
        .perform(get("/throw").param("type", "conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.error").value("Conflict"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/throw")
    void throwByType(@RequestParam String type) {
      switch (type) {
        case "domainValidation" -> throw new DomainValidationException("invalid date of birth");
        case "fieldErrors" ->
            throw new DomainValidationException(
                "validation failed",
                List.of(new FieldError("email", "must be a well-formed email address")));
        case "unauthorized" -> throw new InvalidCredentialsException("bad credentials");
        case "forbidden" -> throw new AccessDeniedException("access is denied");
        case "notFound" -> throw new NotFoundException("student not found");
        case "conflict" -> throw new DuplicateCodeException("student code already exists");
        default -> throw new IllegalArgumentException("unknown type: " + type);
      }
    }

    @PostMapping("/throw/valid")
    void throwOnInvalidBody(@Valid @RequestBody NameRequest request) {}

    @PostMapping("/throw/date")
    void throwOnMalformedDate(@RequestBody DateRequest request) {}
  }

  record NameRequest(@NotBlank String name) {}

  record DateRequest(LocalDate date) {}
}
