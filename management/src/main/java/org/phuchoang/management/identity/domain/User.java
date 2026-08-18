package org.phuchoang.management.identity.domain;

/**
 * Hashing happens in {@code IdentityService} (application layer), not inside this factory — unlike
 * 06-low-level-design.md §8.2's literal {@code PasswordHasher}-parameter signature, {@code User}
 * here only ever receives an already-hashed {@link PasswordHash}. Passing the {@code port/}-typed
 * {@code PasswordHasher} itself into {@code domain/} would cross the Domain→Port layering rule
 * (LayeringRulesTest); hashing one layer up keeps the same "no live Spring dependency on the
 * aggregate" intent without that violation.
 *
 * <p>{@code studentId} is a plain {@code Long}, not {@code student}'s {@code StudentId} VO —
 * {@code identity} referencing {@code student}'s types would form a module cycle together with
 * {@code student}'s own dependency on {@link org.phuchoang.management.identity.AccountProvisioning}
 * (see that interface's Javadoc).
 *
 * <p>{@code initial_password_encrypted} has no field here yet — the AES {@code PasswordCipher}
 * step (Identity.5, US-6.3) is deferred; until then the column simply stays {@code NULL}.
 */
public class User {

  private UserId id;
  private Username username;
  private PasswordHash passwordHash;
  private final Role role;
  private final Long studentId;
  private boolean mustChangePassword;
  private final long version;

  private User(
      UserId id,
      Username username,
      PasswordHash passwordHash,
      Role role,
      Long studentId,
      boolean mustChangePassword,
      long version) {
    this.id = id;
    this.username = username;
    this.passwordHash = passwordHash;
    this.role = role;
    this.studentId = studentId;
    this.mustChangePassword = mustChangePassword;
    this.version = version;
  }

  /** Identity.1/2/3 — role is always STUDENT, mustChangePassword always starts true. */
  public static User provisionForStudent(Username username, Long studentId, PasswordHash passwordHash) {
    return new User(null, username, passwordHash, Role.STUDENT, studentId, true, 0L);
  }

  /** Rehydrates a {@code User} from data already validated at write time (a DB row). */
  public static User reconstitute(
      UserId id,
      Username username,
      PasswordHash passwordHash,
      Role role,
      Long studentId,
      boolean mustChangePassword,
      long version) {
    return new User(id, username, passwordHash, role, studentId, mustChangePassword, version);
  }

  public UserId id() {
    return id;
  }

  public Username username() {
    return username;
  }

  public PasswordHash passwordHash() {
    return passwordHash;
  }

  public Role role() {
    return role;
  }

  public Long studentId() {
    return studentId;
  }

  public boolean mustChangePassword() {
    return mustChangePassword;
  }

  public long version() {
    return version;
  }
}
