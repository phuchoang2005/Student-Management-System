package org.phuchoang.management.enrollment.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.enrollment.application.EnrollmentService.CreatedEnrollment;
import org.phuchoang.management.enrollment.application.EnrollmentService.EnrollmentDetailView;
import org.phuchoang.management.enrollment.application.command.EnrollStudentCommand;
import org.phuchoang.management.enrollment.web.dto.EnrollmentCourseDto;
import org.phuchoang.management.enrollment.web.dto.EnrollmentCreateRequest;
import org.phuchoang.management.enrollment.web.dto.EnrollmentDetailDto;
import org.phuchoang.management.enrollment.web.dto.EnrollmentResponse;
import org.phuchoang.management.enrollment.web.dto.EnrollmentStudentDto;
import org.phuchoang.management.student.StudentSummary;

@Mapper(componentModel = "spring")
public interface EnrollmentMapper {

  EnrollStudentCommand toCommand(EnrollmentCreateRequest request);

  EnrollmentResponse toResponse(CreatedEnrollment created);

  EnrollmentDetailDto toDetailDto(EnrollmentDetailView view);

  EnrollmentStudentDto toDto(StudentSummary summary);

  EnrollmentCourseDto toDto(CourseSummary summary);
}
