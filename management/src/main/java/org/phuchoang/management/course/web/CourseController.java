package org.phuchoang.management.course.web;

import jakarta.validation.Valid;
import org.phuchoang.management.course.application.CourseService;
import org.phuchoang.management.course.web.dto.CourseCreateRequest;
import org.phuchoang.management.course.web.dto.CourseDetailDto;
import org.phuchoang.management.course.web.dto.CourseResponse;
import org.phuchoang.management.course.web.dto.CourseSummaryDto;
import org.phuchoang.management.course.web.dto.CourseUpdateRequest;
import org.phuchoang.management.shared.paging.CursorPage;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/courses")
public class CourseController {

  private final CourseService courseService;
  private final CourseMapper mapper;

  public CourseController(CourseService courseService, CourseMapper mapper) {
    this.courseService = courseService;
    this.mapper = mapper;
  }

  @GetMapping
  public CursorPage<CourseSummaryDto> searchCourses(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    int clampedSize = Math.min(Math.max(size, 1), 100);
    return courseService.search(query, cursor, clampedSize).map(mapper::toSummaryDto);
  }

  @GetMapping("/{code}")
  public CourseDetailDto getCourse(@PathVariable String code) {
    return mapper.toDetailDto(courseService.getDetail(code));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CourseResponse createCourse(@Valid @RequestBody CourseCreateRequest request) {
    CourseService.CreatedCourse created = courseService.create(mapper.toCommand(request));
    return mapper.toResponse(created);
  }

  @PutMapping("/{code}")
  public CourseResponse updateCourse(
      @PathVariable String code, @Valid @RequestBody CourseUpdateRequest request) {
    CourseService.UpdatedCourse updated = courseService.update(code, mapper.toCommand(request));
    return mapper.toResponse(updated);
  }

  @DeleteMapping("/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeCourse(@PathVariable String code) {
    courseService.remove(code);
  }
}
