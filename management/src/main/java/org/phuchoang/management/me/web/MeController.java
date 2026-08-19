package org.phuchoang.management.me.web;

import java.util.List;
import org.phuchoang.management.book.BookLookup;
import org.phuchoang.management.enrollment.EnrollmentLookup;
import org.phuchoang.management.me.web.dto.MeBooksAndCoursesResponse;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.FieldError;
import org.phuchoang.management.shared.security.AuthenticatedPrincipal;
import org.phuchoang.management.shared.web.PageResponse;
import org.phuchoang.management.student.StudentId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * US-5.4 — a Student's own owned books and active course enrollments, composed in one call and
 * scoped to {@code principal.studentId}. {@code SecurityConfig} restricts {@code GET
 * /api/v1/me/**} to {@code hasRole("STUDENT")} (06-low-level-design.md §11.1), so every caller
 * that reaches this controller is guaranteed to be a Student with a non-null {@code studentId}
 * (the {@code users.student_id} role co-invariant, 05-database-schema.md §3.5) — no further
 * role/ownership check is needed here.
 *
 * <p>{@code books}/{@code courses} page independently ({@code booksPage}/{@code booksSize} vs.
 * {@code coursesPage}/{@code coursesSize}), unlike every other list endpoint's single {@code
 * Pageable} — Spring Data's {@code PageableHandlerMethodArgumentResolver} only supports one
 * {@code page}/{@code size} pair per request, so both are parsed and validated by hand instead.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

  private final BookLookup bookLookup;
  private final EnrollmentLookup enrollmentLookup;
  private final MeMapper mapper;

  public MeController(BookLookup bookLookup, EnrollmentLookup enrollmentLookup, MeMapper mapper) {
    this.bookLookup = bookLookup;
    this.enrollmentLookup = enrollmentLookup;
    this.mapper = mapper;
  }

  @GetMapping("/books-and-courses")
  public MeBooksAndCoursesResponse getMyBooksAndCourses(
      @RequestParam(defaultValue = "0") int booksPage,
      @RequestParam(defaultValue = "20") int booksSize,
      @RequestParam(defaultValue = "0") int coursesPage,
      @RequestParam(defaultValue = "20") int coursesSize,
      Authentication authentication) {
    StudentId studentId = studentIdOf(authentication);
    Pageable booksPageable = pageRequest("books", booksPage, booksSize);
    Pageable coursesPageable = pageRequest("courses", coursesPage, coursesSize);

    var books = PageResponse.from(bookLookup.findByOwner(studentId, booksPageable).map(mapper::toDto));
    var courses = PageResponse.from(enrollmentLookup.findByStudent(studentId, coursesPageable).map(mapper::toDto));

    return new MeBooksAndCoursesResponse(books, courses);
  }

  // Cast is safe: identity's AppUserDetailsService is the only UserDetailsService in the
  // context (AuthenticatedPrincipal's own Javadoc), and the STUDENT-only filter-chain rule
  // guarantees studentId is non-null for every caller that reaches this method.
  private StudentId studentIdOf(Authentication authentication) {
    AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
    return new StudentId(principal.studentId());
  }

  private Pageable pageRequest(String collection, int page, int size) {
    if (page < 0) {
      throw new DomainValidationException(
          "Invalid page request.", List.of(new FieldError(collection + "Page", "must be >= 0")));
    }
    if (size < 1 || size > 100) {
      throw new DomainValidationException(
          "Invalid page request.", List.of(new FieldError(collection + "Size", "must be between 1 and 100")));
    }
    return PageRequest.of(page, size);
  }
}
