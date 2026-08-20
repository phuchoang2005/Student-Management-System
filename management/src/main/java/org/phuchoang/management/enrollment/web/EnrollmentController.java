package org.phuchoang.management.enrollment.web;

import jakarta.validation.Valid;
import org.phuchoang.management.enrollment.application.EnrollmentService;
import org.phuchoang.management.enrollment.web.dto.EnrollmentCreateRequest;
import org.phuchoang.management.enrollment.web.dto.EnrollmentDetailDto;
import org.phuchoang.management.enrollment.web.dto.EnrollmentResponse;
import org.phuchoang.management.shared.security.AuthenticatedPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

  private final EnrollmentService enrollmentService;
  private final EnrollmentMapper mapper;

  public EnrollmentController(EnrollmentService enrollmentService, EnrollmentMapper mapper) {
    this.enrollmentService = enrollmentService;
    this.mapper = mapper;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EnrollmentResponse createEnrollment(@Valid @RequestBody EnrollmentCreateRequest request) {
    EnrollmentService.CreatedEnrollment created = enrollmentService.enroll(mapper.toCommand(request));
    return mapper.toResponse(created);
  }

  @GetMapping("/{studentId}/{courseCode}")
  public EnrollmentDetailDto getEnrollment(
      @PathVariable Long studentId, @PathVariable String courseCode, Authentication authentication) {
    Long callerStudentId = AuthenticatedPrincipal.studentIdOf(authentication);
    return mapper.toDetailDto(enrollmentService.getDetail(studentId, courseCode, callerStudentId));
  }

  @DeleteMapping("/{studentId}/{courseCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void endEnrollment(@PathVariable Long studentId, @PathVariable String courseCode) {
    enrollmentService.end(studentId, courseCode);
  }
}
