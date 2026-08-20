package org.phuchoang.management.student.port;

import java.util.Optional;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.domain.StudentCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StudentRepository {

  Optional<Student> findByCode(StudentCode code);

  /** Backs {@code StudentLookup.summaryOf} (06-low-level-design.md §4.8). */
  Optional<Student> findById(StudentId id);

  boolean existsByCode(StudentCode code);

  /** Backs {@code StudentLookup.existsById} (Book.4, Enrollment.3). */
  boolean existsById(StudentId id);

  boolean existsByEmail(Email email);

  boolean existsByEmailExcludingCode(Email email, StudentCode excluding);

  /**
   * UC-13 — matches code/name/email; {@code query} may be {@code null} to return every student.
   * {@code scopeToId} narrows the result to a single student (own-records-only scoping for a
   * STUDENT caller, 02-component-diagram.md §4); {@code null} means unscoped, mirroring {@code
   * BookRepository.search}'s {@code ownerFilter} parameter.
   */
  Page<Student> search(String query, StudentId scopeToId, Pageable pageable);

  Student save(Student student);

  void deleteByCode(StudentCode code);
}
