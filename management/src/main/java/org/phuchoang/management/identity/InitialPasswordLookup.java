package org.phuchoang.management.identity;

import java.util.Optional;

/**
 * Published interface backing UC-23 / US-6.3. 06-low-level-design.md §8.6 hangs {@code
 * viewStudentInitialPassword} off {@code identity}'s own {@code AuthController} and has {@code
 * IdentityService} take a {@code StudentCode}; both are impossible without a module cycle —
 * resolving a student code means depending on {@code student}, which already depends on {@code
 * identity} through {@link AccountProvisioning} (see that interface's Javadoc). So {@code student}
 * keeps ownership of the code→id lookup, serves {@code GET /api/v1/students/{code}/initial-password}
 * from {@code StudentController}, and reads the password back through here. The endpoint's path,
 * roles, and responses are unchanged.
 *
 * <p>Returns {@link Optional} rather than throwing: api-specification.md §5.5 requires "already
 * changed" and "no such student" to be indistinguishable, and only {@code student} knows about the
 * second case — having it render the single 404 for both is what guarantees one identical body.
 */
public interface InitialPasswordLookup {

  /**
   * The account's still-unchanged initial password, or empty once it has been changed (Identity.4)
   * or if the student has no account at all.
   */
  Optional<InitialPasswordView> viewInitialPassword(Long studentId);
}
