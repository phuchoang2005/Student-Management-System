package org.phuchoang.management.student.web;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.phuchoang.management.student.application.StudentService.ProvisionedStudent;
import org.phuchoang.management.student.application.StudentService.StudentDetailView;
import org.phuchoang.management.student.application.StudentService.StudentSummaryView;
import org.phuchoang.management.student.application.StudentService.UpdatedStudent;
import org.phuchoang.management.student.application.command.RegisterStudentCommand;
import org.phuchoang.management.student.application.command.UpdateStudentCommand;
import org.phuchoang.management.student.web.dto.RegisterStudentRequest;
import org.phuchoang.management.student.web.dto.StudentDetailDto;
import org.phuchoang.management.student.web.dto.StudentRegistrationResponse;
import org.phuchoang.management.student.web.dto.StudentResponse;
import org.phuchoang.management.student.web.dto.StudentSummaryDto;
import org.phuchoang.management.student.web.dto.UpdateStudentRequest;

@Mapper(componentModel = "spring")
public interface StudentMapper {

  RegisterStudentCommand toCommand(RegisterStudentRequest request);

  UpdateStudentCommand toCommand(UpdateStudentRequest request);

  StudentRegistrationResponse toRegistrationResponse(ProvisionedStudent provisioned);

  StudentResponse toResponse(UpdatedStudent updated);

  StudentSummaryDto toSummaryDto(StudentSummaryView view);

  @Mapping(target = "books", source = "ownedBooks")
  @Mapping(target = "courses", source = "activeCourses")
  StudentDetailDto toDetailDto(StudentDetailView view);
}
