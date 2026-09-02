package org.phuchoang.management.student.web;

import jakarta.validation.Valid;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.shared.security.AuthenticatedPrincipal;
import org.phuchoang.management.student.application.StudentService;
import org.phuchoang.management.student.web.dto.InitialPasswordResponse;
import org.phuchoang.management.student.web.dto.RegisterStudentRequest;
import org.phuchoang.management.student.web.dto.StudentDetailDto;
import org.phuchoang.management.student.web.dto.StudentRegistrationResponse;
import org.phuchoang.management.student.web.dto.StudentResponse;
import org.phuchoang.management.student.web.dto.StudentSummaryDto;
import org.phuchoang.management.student.web.dto.UpdateStudentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

  private final StudentService studentService;
  private final StudentMapper mapper;

  public StudentController(StudentService studentService, StudentMapper mapper) {
    this.studentService = studentService;
    this.mapper = mapper;
  }

  @GetMapping
  public CursorPage<StudentSummaryDto> searchStudents(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    Long callerStudentId = AuthenticatedPrincipal.studentIdOf(authentication);
    int clampedSize = Math.min(Math.max(size, 1), 100);
    return studentService.search(query, cursor, clampedSize, callerStudentId).map(mapper::toSummaryDto);
  }

  @GetMapping("/{code}")
  public StudentDetailDto getStudent(@PathVariable String code, Authentication authentication) {
    Long callerStudentId = AuthenticatedPrincipal.studentIdOf(authentication);
    return mapper.toDetailDto(studentService.getDetail(code, callerStudentId));
  }

  /** US-6.3 — Registrar only, enforced by the filter chain (06-low-level-design.md §11.1). */
  @GetMapping("/{code}/initial-password")
  public InitialPasswordResponse getInitialPassword(@PathVariable String code) {
    return mapper.toInitialPasswordResponse(studentService.viewInitialPassword(code));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public StudentRegistrationResponse registerStudent(@Valid @RequestBody RegisterStudentRequest request) {
    StudentService.ProvisionedStudent provisioned = studentService.register(mapper.toCommand(request));
    return mapper.toRegistrationResponse(provisioned);
  }

  @PutMapping("/{code}")
  public StudentResponse updateStudent(
      @PathVariable String code, @Valid @RequestBody UpdateStudentRequest request) {
    StudentService.UpdatedStudent updated = studentService.update(code, mapper.toCommand(request));
    return mapper.toResponse(updated);
  }

  @DeleteMapping("/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeStudent(@PathVariable String code) {
    studentService.remove(code);
  }
}
