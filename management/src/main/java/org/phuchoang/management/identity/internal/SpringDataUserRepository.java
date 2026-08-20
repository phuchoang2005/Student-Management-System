package org.phuchoang.management.identity.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

interface SpringDataUserRepository extends CrudRepository<UserRow, Long> {

  Optional<UserRow> findByUsername(String username);

  Optional<UserRow> findByStudentId(Long studentId);

  boolean existsByUsername(String username);

  // Same two constraints as SpringDataStudentRepository.search: a string-based @Query can't return
  // Page and doesn't auto-apply a Pageable's LIMIT/OFFSET, so JdbcUserRepository pairs this with
  // countStaffAccounts and binds the bounds itself. The role set is a bound parameter rather than
  // an inlined literal so Role.STAFF_ROLES stays the single source of truth for "is staff".
  @Query("""
      SELECT * FROM users
      WHERE role IN (:roles)
      ORDER BY username
      LIMIT :limit OFFSET :offset
      """)
  List<UserRow> findStaffAccounts(Collection<String> roles, int limit, long offset);

  @Query("SELECT COUNT(*) FROM users WHERE role IN (:roles)")
  long countStaffAccounts(Collection<String> roles);

  void deleteByStudentId(Long studentId);
}
