package org.phuchoang.management.student.port;

import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.domain.StudentCode;

/**
 * Scoped to what US-1.1 (registration) needs. {@code findByCode}/{@code
 * existsByEmailExcludingCode}/{@code search}/{@code deleteByCode} (06-low-level-design.md §4.5)
 * are added when the update/remove/search use cases (US-1.2/US-1.3/US-5.1) are implemented.
 */
public interface StudentRepository {

  boolean existsByCode(StudentCode code);

  boolean existsByEmail(Email email);

  Student save(Student student);
}
