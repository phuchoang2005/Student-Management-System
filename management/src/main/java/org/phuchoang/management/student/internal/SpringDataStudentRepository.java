package org.phuchoang.management.student.internal;

import org.springframework.data.repository.CrudRepository;

interface SpringDataStudentRepository extends CrudRepository<StudentRow, Long> {

  boolean existsByStudentCode(String studentCode);

  boolean existsByEmail(String email);
}
