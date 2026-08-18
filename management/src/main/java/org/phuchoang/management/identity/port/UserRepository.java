package org.phuchoang.management.identity.port;

import java.util.Optional;
import org.phuchoang.management.identity.domain.User;

/**
 * Scoped to what account provisioning + US-1.2's username sync need. {@code findByUsername}/
 * {@code existsByUsername}/{@code deleteByStudentId} (06-low-level-design.md §8.3) are added
 * alongside login/change-password/view-initial-password (US-6.1–6.3) and student removal
 * (US-1.3).
 */
public interface UserRepository {

  Optional<User> findByStudentId(Long studentId);

  User save(User user);
}
