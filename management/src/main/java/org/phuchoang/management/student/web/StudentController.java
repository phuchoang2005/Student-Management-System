package org.phuchoang.management.student.web;

import jakarta.validation.Valid;
import org.phuchoang.management.student.application.StudentService;
import org.phuchoang.management.student.web.dto.RegisterStudentRequest;
import org.phuchoang.management.student.web.dto.StudentRegistrationResponse;
import org.phuchoang.management.student.web.dto.StudentResponse;
import org.phuchoang.management.student.web.dto.UpdateStudentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
