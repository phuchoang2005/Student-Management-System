package org.phuchoang.management.identity.port;

import java.util.Optional;
import org.phuchoang.management.identity.domain.User;
import org.phuchoang.management.identity.domain.UserId;
import org.phuchoang.management.identity.domain.Username;

/**
 * Scoped to what account provisioning, US-1.2's username sync, and US-6.1–6.3 need. {@code
 * deleteByStudentId} (06-low-level-design.md §8.3) is added alongside student removal (US-1.3).
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

  /** {@code IdentityService.setAccountEnabled} (UC-25). */
  Optional<User> findById(UserId userId);

  /** {@code IdentityService.provisionStaff} (Identity.2, UC-24 flow 3a) — never called for Student accounts, since {@code Email} is already Student.2-unique. */
  boolean existsByUsername(Username username);

  User save(User user);
}
