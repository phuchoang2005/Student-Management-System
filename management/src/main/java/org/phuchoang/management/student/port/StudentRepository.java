package org.phuchoang.management.student.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.phuchoang.management.shared.paging.CursorPage;
import org.phuchoang.management.student.StudentId;
import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.StudentCode;

public interface StudentRepository {

  Optional<Student> findByCode(StudentCode code);

  /** Backs {@code StudentLookup.summaryOf}/{@code profileOf} (06-low-level-design.md §4.8). */
  Optional<Student> findById(StudentId id);

  /** Backs {@code StudentLookup.summariesOf}'s batch resolution (PM-046). */
  List<Student> findByIds(Collection<StudentId> ids);

  boolean existsByCode(StudentCode code);

  boolean existsByEmail(Email email);

  boolean existsByEmailExcludingCode(Email email, StudentCode excluding);

  /**
   * UC-13 — matches code/name/email via FULLTEXT search (PM-044), keyset-paginated (PM-045);
   * {@code query} may be {@code null} to return every student. {@code scopeToId} narrows the
   * result to a single student (own-records-only scoping for a STUDENT caller,
   * 02-component-diagram.md §4); {@code null} means unscoped, mirroring {@code
   * BookRepository.search}'s {@code ownerFilter} parameter. {@code afterKey} is the decoded cursor
   * (the previous page's last student_code), {@code null} for the first page.
   */
  CursorPage<Student> search(String query, StudentId scopeToId, String afterKey, int limit);

  Student save(Student student);

  void deleteByCode(StudentCode code);
}
