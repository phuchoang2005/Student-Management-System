package org.phuchoang.management.identity.internal;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface SpringDataUserRepository extends CrudRepository<UserRow, Long> {

  Optional<UserRow> findByUsername(String username);

  Optional<UserRow> findByStudentId(Long studentId);
}
