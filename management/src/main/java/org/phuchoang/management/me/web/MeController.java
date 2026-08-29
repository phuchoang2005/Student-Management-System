package org.phuchoang.management.me.web;

import org.phuchoang.management.book.BookLookup;
import org.phuchoang.management.enrollment.EnrollmentLookup;
import org.phuchoang.management.me.web.dto.MeBookSummaryDto;
import org.phuchoang.management.me.web.dto.MeCourseSummaryDto;
import org.phuchoang.management.me.web.dto.MeProfileDto;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.shared.security.AuthenticatedPrincipal;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.StudentLookup;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * US-5.4 — a Student's own record, owned books, and active course enrollments, each scoped to
 * {@code principal.studentId}. {@code SecurityConfig} restricts {@code GET /api/v1/me/**} to
 * {@code hasRole("STUDENT")} (06-low-level-design.md §11.1), so every caller that reaches this
 * controller is guaranteed to be a Student with a non-null {@code studentId} (the {@code
 * users.student_id} role co-invariant, 05-database-schema.md §3.5) — no further role/ownership
 * check is needed here.
 *
 * <p>Three endpoints, not one composed response. The single {@code GET /me/books-and-courses} this
 * replaces had to hand-roll {@code booksPage}/{@code coursesPage} prefixed paging, because only one
 * {@code cursor}/{@code size} pair can be resolved per request — splitting the collections apart
 * lets each take its own cursor (PM-045), the same as every other list endpoint in the API, and
 * lets a client paging its book list stop refetching its course list to do it.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

  private final StudentLookup studentLookup;
  private final BookLookup bookLookup;
  private final EnrollmentLookup enrollmentLookup;
  private final MeMapper mapper;

  public MeController(
      StudentLookup studentLookup,
      BookLookup bookLookup,
      EnrollmentLookup enrollmentLookup,
      MeMapper mapper) {
    this.studentLookup = studentLookup;
    this.bookLookup = bookLookup;
    this.enrollmentLookup = enrollmentLookup;
    this.mapper = mapper;
  }

  /**
   * The caller's own record — the only way a Student learns their own {@code studentCode}, since
   * the login response carries just {@code {role, mustChangePassword}} and this API has no
   * session-probe endpoint.
   *
   * <p>404 when the row is gone: the session principal outlives a student a Registrar removed
   * mid-session, and the honest answer is that the record no longer exists.
   */
  @GetMapping("/profile")
  public MeProfileDto getMyProfile(Authentication authentication) {
    StudentId studentId = studentIdOf(authentication);
    return studentLookup
        .profileOf(studentId)
        .map(mapper::toDto)
        .orElseThrow(() -> new NotFoundException("Your student record no longer exists."));
  }

  @GetMapping("/courses")
  public CursorPage<MeCourseSummaryDto> getMyCourses(
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    int clampedSize = Math.min(Math.max(size, 1), 100);
    return enrollmentLookup
        .findByStudent(studentIdOf(authentication), cursor, clampedSize)
        .map(mapper::toDto);
  }

  @GetMapping("/books")
  public CursorPage<MeBookSummaryDto> getMyBooks(
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    int clampedSize = Math.min(Math.max(size, 1), 100);
    return bookLookup.findByOwner(studentIdOf(authentication), cursor, clampedSize).map(mapper::toDto);
  }

  // Cast is safe: identity's AppUserDetailsService is the only UserDetailsService in the
  // context (AuthenticatedPrincipal's own Javadoc), and the STUDENT-only filter-chain rule
  // guarantees studentId is non-null for every caller that reaches this controller.
  private StudentId studentIdOf(Authentication authentication) {
    AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
    return new StudentId(principal.studentId());
  }
}
