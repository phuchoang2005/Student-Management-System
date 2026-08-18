package org.phuchoang.management.student.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.student.application.StudentService.ProvisionedStudent;
import org.phuchoang.management.student.application.command.RegisterStudentCommand;
import org.phuchoang.management.student.web.dto.RegisterStudentRequest;
import org.phuchoang.management.student.web.dto.StudentRegistrationResponse;

@Mapper(componentModel = "spring")
public interface StudentMapper {

  RegisterStudentCommand toCommand(RegisterStudentRequest request);

  StudentRegistrationResponse toRegistrationResponse(ProvisionedStudent provisioned);
}
