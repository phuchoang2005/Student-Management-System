package org.phuchoang.management.student.internal;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface SpringDataStudentRepository extends CrudRepository<StudentRow, Long> {

  Optional<StudentRow> findByStudentCode(String studentCode);

  boolean existsByStudentCode(String studentCode);

  boolean existsByEmail(String email);

  boolean existsByEmailAndStudentCodeNot(String email, String studentCode);
}
