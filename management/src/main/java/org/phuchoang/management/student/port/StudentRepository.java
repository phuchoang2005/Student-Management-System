package org.phuchoang.management.student.port;

import java.util.Optional;
import org.phuchoang.management.student.domain.Email;
import org.phuchoang.management.student.domain.Student;
import org.phuchoang.management.student.domain.StudentCode;

/**
 * Scoped to what US-1.1 (registration), US-1.2 (update), and US-1.3 (remove) need. {@code search}
 * (06-low-level-design.md §4.5) is added when the search use case (US-5.1) is implemented.
 */
public interface StudentRepository {

  Optional<Student> findByCode(StudentCode code);

  boolean existsByCode(StudentCode code);

  boolean existsByEmail(Email email);

  boolean existsByEmailExcludingCode(Email email, StudentCode excluding);

  Student save(Student student);

  void deleteByCode(StudentCode code);
}
