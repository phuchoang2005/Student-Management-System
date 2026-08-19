package org.phuchoang.management.identity.port;

import java.util.Optional;
import org.phuchoang.management.identity.domain.User;
import org.phuchoang.management.identity.domain.Username;

/**
 * Scoped to what account provisioning, US-1.2's username sync, and US-6.1–6.3 need. {@code
 * existsByUsername}/{@code findById}/{@code deleteByStudentId} (06-low-level-design.md §8.3) are
 * added alongside staff-account provisioning (US-7.1/7.2) and student removal (US-1.3).
 *
 * <p>06-low-level-design.md §8.3's {@code findByStudentCode} is deliberately absent: resolving a
 * student code would mean either {@code identity} depending on {@code student}'s types (a module
 * cycle — see {@code AccountProvisioning}'s Javadoc) or its repository querying the {@code
 * students} table it doesn't own. {@code student} resolves the code to an id and calls {@link
 * org.phuchoang.management.identity.InitialPasswordLookup} instead, so {@link #findByStudentId}
 * covers UC-23 too.
 */
public interface UserRepository {

  /** {@code AppUserDetailsService.loadUserByUsername}, {@code IdentityService.changePassword}. */
  Optional<User> findByUsername(Username username);

  Optional<User> findByStudentId(Long studentId);

  User save(User user);
}
