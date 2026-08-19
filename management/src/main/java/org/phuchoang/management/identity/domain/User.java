package org.phuchoang.management.identity.domain;

/**
 * Hashing and encryption happen in {@code IdentityService} (application layer), not inside this
 * aggregate — unlike 06-low-level-design.md §8.2's literal {@code PasswordHasher}/{@code
 * PasswordCipher}-parameter signatures, {@code User} here only ever receives an already-hashed
 * {@link PasswordHash} and an already-encrypted {@link EncryptedInitialPassword}. Passing the
 * {@code port/}-typed collaborators themselves into {@code domain/} would cross the Domain→Port
 * layering rule (LayeringRulesTest); doing both one layer up keeps the same "no live Spring
 * dependency on the aggregate" intent without that violation.
 *
 * <p>{@code studentId} is a plain {@code Long}, not {@code student}'s {@code StudentId} VO —
 * {@code identity} referencing {@code student}'s types would form a module cycle together with
 * {@code student}'s own dependency on {@link org.phuchoang.management.identity.AccountProvisioning}
 * (see that interface's Javadoc).
 */
public class User {

  private UserId id;
  private Username username;
  private PasswordHash passwordHash;
  private EncryptedInitialPassword initialPasswordEncrypted;
  private final Role role;
  private final Long studentId;
  private boolean mustChangePassword;
  private boolean enabled;
  private final long version;

  private User(
      UserId id,
      Username username,
      PasswordHash passwordHash,
      EncryptedInitialPassword initialPasswordEncrypted,
      Role role,
      Long studentId,
      boolean mustChangePassword,
      boolean enabled,
      long version) {
    this.id = id;
    this.username = username;
    this.passwordHash = passwordHash;
    this.initialPasswordEncrypted = initialPasswordEncrypted;
    this.role = role;
    this.studentId = studentId;
    this.mustChangePassword = mustChangePassword;
    this.enabled = enabled;
    this.version = version;
  }

  /** Identity.1/2/3/5 — role is always STUDENT, mustChangePassword always starts true. */
  public static User provisionForStudent(
      Username username,
      Long studentId,
      PasswordHash passwordHash,
      EncryptedInitialPassword initialPasswordEncrypted) {
    return new User(
        null,
        username,
        passwordHash,
        initialPasswordEncrypted,
        Role.STUDENT,
        studentId,
        true,
        true,
        0L);
  }

  /**
   * Identity.3/6, UC-24 — {@code role} must already be one of {@link Role#STAFF_ROLES}; the
   * caller ({@code IdentityService.provisionStaff}) validates that before calling. {@code
   * studentId} stays {@code null} (05-database-schema.md §3.5's role co-invariant), and hashing/
   * encryption/must-change-password/enabled mirror {@link #provisionForStudent} exactly.
   */
  public static User provisionStaff(
      Username username,
      Role role,
      PasswordHash passwordHash,
      EncryptedInitialPassword initialPasswordEncrypted) {
    return new User(null, username, passwordHash, initialPasswordEncrypted, role, null, true, true, 0L);
  }

  /** Rehydrates a {@code User} from data already validated at write time (a DB row). */
  public static User reconstitute(
      UserId id,
      Username username,
      PasswordHash passwordHash,
      EncryptedInitialPassword initialPasswordEncrypted,
      Role role,
      Long studentId,
      boolean mustChangePassword,
      boolean enabled,
      long version) {
    return new User(
        id,
        username,
        passwordHash,
        initialPasswordEncrypted,
        role,
        studentId,
        mustChangePassword,
        enabled,
        version);
  }

  /** Identity.2/req.md §3 — keeps username equal to the owning student's email after it changes. */
  public void renameUsername(Username newUsername) {
    this.username = newUsername;
  }

  /**
   * Identity.3/4/5 — clearing {@code initialPasswordEncrypted} in the same step is what makes the
   * new password permanently unrecoverable, including to the Registrar
   * (04-authentication-authorization.md §5.1). The plaintext policy (§5.2) is checked before
   * hashing, in {@code IdentityService.changePassword}.
   */
  public void changePassword(PasswordHash newPasswordHash) {
    this.passwordHash = newPasswordHash;
    this.initialPasswordEncrypted = null;
    this.mustChangePassword = false;
  }

  /**
   * Identity.7, UC-25 — toggles login access in either direction; no other field changes, so
   * deactivation is not a soft-delete of any state the account holder previously created.
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
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

  /** {@code null} once the account holder has changed their password (Identity.4). */
  public EncryptedInitialPassword initialPasswordEncrypted() {
    return initialPasswordEncrypted;
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

  public boolean enabled() {
    return enabled;
  }

  public long version() {
    return version;
  }
}
