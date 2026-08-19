package org.phuchoang.management.student;

/**
 * Public read-only API other modules use to reference a {@code Student} without depending on
 * {@code student}'s internal layers (06-low-level-design.md §4.8). {@code StudentService}
 * implements this directly — no separate façade class, since the read path needs no logic beyond
 * delegating to {@code StudentRepository}.
 */
public interface StudentLookup {

  boolean existsById(StudentId id);

  /** Consumed by {@code BookService.getDetail} (06-low-level-design.md §4.8) to embed the current owner's summary. */
  StudentSummary summaryOf(StudentId id);
}
