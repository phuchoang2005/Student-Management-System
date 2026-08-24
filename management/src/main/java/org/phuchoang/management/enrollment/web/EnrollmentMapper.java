package org.phuchoang.management.enrollment.web;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.enrollment.application.EnrollmentBatchService.BatchEnrollmentOutcome;
import org.phuchoang.management.enrollment.application.EnrollmentBatchService.BatchEnrollmentResult;
import org.phuchoang.management.enrollment.application.EnrollmentService.CreatedEnrollment;
import org.phuchoang.management.enrollment.application.EnrollmentService.EnrollmentDetailView;
import org.phuchoang.management.enrollment.application.command.BatchEnrollStudentCommand;
import org.phuchoang.management.enrollment.application.command.EnrollStudentCommand;
import org.phuchoang.management.enrollment.web.dto.BatchEnrollmentRequest;
import org.phuchoang.management.enrollment.web.dto.BatchEnrollmentResponse;
import org.phuchoang.management.enrollment.web.dto.BatchEnrollmentResultDto;
import org.phuchoang.management.enrollment.web.dto.EnrollmentCourseDto;
import org.phuchoang.management.enrollment.web.dto.EnrollmentCreateRequest;
import org.phuchoang.management.enrollment.web.dto.EnrollmentDetailDto;
import org.phuchoang.management.enrollment.web.dto.EnrollmentResponse;
import org.phuchoang.management.enrollment.web.dto.EnrollmentStudentDto;
import org.phuchoang.management.student.StudentSummary;

@Mapper(componentModel = "spring")
public interface EnrollmentMapper {

  EnrollStudentCommand toCommand(EnrollmentCreateRequest request);

  BatchEnrollStudentCommand toCommand(BatchEnrollmentRequest request);

  /** {@code outcome} is an enum; MapStruct renders it as its name, which is the wire contract. */
  @Mapping(source = "outcome", target = "status")
  BatchEnrollmentResultDto toDto(BatchEnrollmentOutcome outcome);

  /**
   * The three counts come through {@code expression} rather than {@code source}: they are derived
   * accessors on {@code BatchEnrollmentResult}, and MapStruct only treats a record's components as
   * properties, so naming them as sources does not resolve.
   */
  @Mapping(source = "outcomes", target = "results")
  @Mapping(expression = "java(result.outcomes().size())", target = "requested")
  @Mapping(expression = "java(result.enrolledCount())", target = "enrolled")
  @Mapping(expression = "java(result.failedCount())", target = "failed")
  BatchEnrollmentResponse toResponse(BatchEnrollmentResult result);

  EnrollmentResponse toResponse(CreatedEnrollment created);

  EnrollmentDetailDto toDetailDto(EnrollmentDetailView view);

  EnrollmentStudentDto toDto(StudentSummary summary);

  EnrollmentCourseDto toDto(CourseSummary summary);
}
