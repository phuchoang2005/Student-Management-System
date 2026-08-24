package org.phuchoang.management.enrollment.web;

import jakarta.validation.Valid;
import org.phuchoang.management.enrollment.application.EnrollmentBatchService;
import org.phuchoang.management.enrollment.application.EnrollmentService;
import org.phuchoang.management.enrollment.web.dto.BatchEnrollmentRequest;
import org.phuchoang.management.enrollment.web.dto.BatchEnrollmentResponse;
import org.phuchoang.management.enrollment.web.dto.EnrollmentCreateRequest;
import org.phuchoang.management.enrollment.web.dto.EnrollmentDetailDto;
import org.phuchoang.management.enrollment.web.dto.EnrollmentResponse;
import org.phuchoang.management.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Keyed on {@code studentCode}, never {@code studentId} — the surrogate id is a database concern
 * and does not appear in a path, a query parameter, or a body anywhere in this controller
 * (api-specification.md §5 decision #9).
 */
@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

  private final EnrollmentService enrollmentService;
  private final EnrollmentBatchService enrollmentBatchService;
  private final EnrollmentMapper mapper;

  public EnrollmentController(
      EnrollmentService enrollmentService,
      EnrollmentBatchService enrollmentBatchService,
      EnrollmentMapper mapper) {
    this.enrollmentService = enrollmentService;
    this.enrollmentBatchService = enrollmentBatchService;
    this.mapper = mapper;
  }

  /**
   * UC-11/UC-20 list view. Exactly one of {@code studentCode} (→ that student's enrolled courses)
   * or {@code courseCode} (→ that course's roster) is required; neither or both is a {@code 400}
   * (see {@code EnrollmentService.search}). Both directions return the same row shape, so one
   * client renders a course list and another a student roster off one schema.
   */
  @GetMapping
  public PageResponse<EnrollmentDetailDto> searchEnrollments(
      @RequestParam(required = false) String studentCode,
      @RequestParam(required = false) String courseCode,
      Pageable pageable) {
    return PageResponse.from(
        enrollmentService.search(studentCode, courseCode, pageable).map(mapper::toDetailDto));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EnrollmentResponse createEnrollment(@Valid @RequestBody EnrollmentCreateRequest request) {
    EnrollmentService.CreatedEnrollment created = enrollmentService.enroll(mapper.toCommand(request));
    return mapper.toResponse(created);
  }

  /**
   * UC-26 — one student, several courses, one request.
   *
   * <p>200 rather than 207: the request itself succeeded, and the per-course outcomes are payload,
   * not transport status. {@code 207 Multi-Status} is a WebDAV code defined against a {@code
   * DAV:multistatus} body, so returning it with a bespoke JSON shape would be a pun. Nor 201 — there
   * is no single {@code Location} and creation is partial by design.
   *
   * <p>An unknown student is still a whole-request 400: it is the subject of the request, not one of
   * its items. An unknown or already-enrolled course is a per-course outcome. Enrolled courses are
   * committed independently, so a rejection later in the list does not undo them.
   */
  @PostMapping("/batch")
  public BatchEnrollmentResponse enrollBatch(@Valid @RequestBody BatchEnrollmentRequest request) {
    return mapper.toResponse(enrollmentBatchService.enrollAll(mapper.toCommand(request)));
  }

  @GetMapping("/{studentCode}/{courseCode}")
  public EnrollmentDetailDto getEnrollment(
      @PathVariable String studentCode, @PathVariable String courseCode) {
    return mapper.toDetailDto(enrollmentService.getDetail(studentCode, courseCode));
  }

  @DeleteMapping("/{studentCode}/{courseCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void endEnrollment(@PathVariable String studentCode, @PathVariable String courseCode) {
    enrollmentService.end(studentCode, courseCode);
  }
}
