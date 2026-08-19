package org.phuchoang.management.enrollment.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.enrollment.application.EnrollmentService.CreatedEnrollment;
import org.phuchoang.management.enrollment.application.command.EnrollStudentCommand;
import org.phuchoang.management.enrollment.web.dto.EnrollmentCreateRequest;
import org.phuchoang.management.enrollment.web.dto.EnrollmentResponse;

@Mapper(componentModel = "spring")
public interface EnrollmentMapper {

  EnrollStudentCommand toCommand(EnrollmentCreateRequest request);

  EnrollmentResponse toResponse(CreatedEnrollment created);
}
