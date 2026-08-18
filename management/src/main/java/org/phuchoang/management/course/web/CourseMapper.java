package org.phuchoang.management.course.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.course.application.CourseService.CreatedCourse;
import org.phuchoang.management.course.application.command.CreateCourseCommand;
import org.phuchoang.management.course.web.dto.CourseCreateRequest;
import org.phuchoang.management.course.web.dto.CourseResponse;

@Mapper(componentModel = "spring")
public interface CourseMapper {

  CreateCourseCommand toCommand(CourseCreateRequest request);

  CourseResponse toResponse(CreatedCourse created);
}
