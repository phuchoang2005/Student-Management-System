package org.phuchoang.management.identity;

/**
 * Published interface (Open Host Service) {@code student} calls synchronously, in the same
 * transaction as the {@code Student} save, so a registered student is never left without a way to
 * log in (06-low-level-design.md §4.6, tactical-ddd-design.md §10).
 *
 * <p>Takes plain {@code Long}/{@code String} rather than {@code student}'s {@code StudentId}/
 * {@code Email} VOs (a deviation from 06-low-level-design.md §8.4's literal signature): {@code
 * identity} referencing {@code student}'s types while {@code student} also depends on {@code
 * identity} (to call this very method) is a genuine module cycle that {@code
 * ApplicationModules.verify()} rejects — Spring Modulith enforces module dependencies to be
 * acyclic, with no "intentional two-way" escape hatch. Passing primitives keeps the dependency
 * one-directional (student → identity only) while the data crossing the boundary is unchanged.
 */
public interface AccountProvisioning {

  ProvisionedAccount provisionForStudent(Long studentId, String email);

  /**
   * req.md §3 "Student ↔ User Account" — a student's account username always equals their
   * current email, so {@code student.StudentService.update} calls this whenever the email
   * actually changes, in the same transaction as the {@code Student} save (TC-STU-018).
   */
  void renameUsernameForStudent(Long studentId, String newEmail);

  /**
   * PM-018 — {@code student.StudentService.remove} calls this synchronously, in the same
   * transaction as the {@code Student} delete, rather than {@code identity} listening for {@code
   * StudentDeleted} the way {@code book}/{@code enrollment} do: {@code identity} already depends
   * on {@code student} in the other direction (this very interface), so an {@code
   * @ApplicationModuleListener} on {@code student.StudentDeleted} here would make the {@code
   * student}/{@code identity} package pair cyclic — {@code ApplicationModules.verify()} confirmed
   * this at build time (fails with "Cycle detected: Slice identity -> Slice student -> Slice
   * identity"), the same one-directional constraint this interface's class Javadoc already
   * documents for {@link #provisionForStudent}. `06-low-level-design.md` §13's literal listener
   * signature for `identity` doesn't hold; this method is the corrected mechanism.
   */
  void deprovisionForStudent(Long studentId);
}
