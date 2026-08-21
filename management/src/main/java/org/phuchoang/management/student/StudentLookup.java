package org.phuchoang.management.student;

import java.util.Optional;

/**
 * Public read-only API other modules use to reference a {@code Student} without depending on
 * {@code student}'s internal layers (06-low-level-design.md §4.8). {@code StudentService}
 * implements this directly — no separate façade class, since the read path needs no logic beyond
 * delegating to {@code StudentRepository}.
 */
public interface StudentLookup {

  /**
   * The single {@link StudentCode} → {@link StudentId} translation point in the system. Every API
   * input names a student by its business key (api-specification.md §5 decision #9), while every
   * FK — {@code books.owner_id}, {@code enrollments.student_id} — is keyed on the surrogate id, so
   * {@code book} and {@code enrollment} resolve one to the other here rather than each reaching
   * into {@code student}'s repository. Empty means "no such student", which both callers turn into
   * {@code UnknownStudentException} (Book.4, Enrollment.3) — this replaces the {@code existsById}
   * check they used while the API was still keyed on ids, folding the existence check and the
   * resolution into one call.
   */
  Optional<StudentId> idOf(StudentCode code);

  /** Consumed by {@code BookService.getDetail} (06-low-level-design.md §4.8) to embed the current owner's summary. */
  StudentSummary summaryOf(StudentId id);

  /**
   * Consumed by {@code MeController} (US-5.4) for {@code GET /api/v1/me/profile} — the caller's own
   * record, including the {@code dateOfBirth} {@link StudentSummary} omits. Optional rather than
   * throwing, unlike {@link #summaryOf}: {@code me} resolves its id from the session principal,
   * which can outlive the student row a Registrar deleted mid-session.
   */
  Optional<StudentProfile> profileOf(StudentId id);
}
