package org.phuchoang.management.course.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.course.application.CourseService.CourseDetailView;
import org.phuchoang.management.course.application.CourseService.CourseSummaryView;
import org.phuchoang.management.course.application.CourseService.CreatedCourse;
import org.phuchoang.management.course.application.CourseService.UpdatedCourse;
import org.phuchoang.management.course.application.command.CreateCourseCommand;
import org.phuchoang.management.course.application.command.UpdateCourseCommand;
import org.phuchoang.management.course.web.dto.CourseCreateRequest;
import org.phuchoang.management.course.web.dto.CourseDetailDto;
import org.phuchoang.management.course.web.dto.CourseResponse;
import org.phuchoang.management.course.web.dto.CourseSummaryDto;
import org.phuchoang.management.course.web.dto.CourseUpdateRequest;

@Mapper(componentModel = "spring")
public interface CourseMapper {

  CreateCourseCommand toCommand(CourseCreateRequest request);

  UpdateCourseCommand toCommand(CourseUpdateRequest request);

  CourseResponse toResponse(CreatedCourse created);

  CourseResponse toResponse(UpdatedCourse updated);

  CourseSummaryDto toSummaryDto(CourseSummaryView view);

  CourseDetailDto toDetailDto(CourseDetailView view);
}
