package org.phuchoang.management.student.domain;

import java.time.Instant;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.student.StudentId;

/**
 * {@code createdAt}/{@code updatedAt}/{@code version} are set by the application, not read back
 * from MySQL's column defaults — Spring Data JDBC doesn't re-fetch DB-computed values after {@code
 * save()} without an extra round trip (06-low-level-design.md §4.4).
 */
public class Student {

  private StudentId id;
  private final StudentCode code;
  private String firstName;
  private String lastName;
  private Email email;
  private DateOfBirth dateOfBirth;
  private final Instant createdAt;
  private Instant updatedAt;
  private final long version;

  private Student(
      StudentId id,
      StudentCode code,
      String firstName,
      String lastName,
      Email email,
      DateOfBirth dateOfBirth,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = id;
    this.code = code;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.dateOfBirth = dateOfBirth;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  public static Student register(
      StudentCode code, String firstName, String lastName, Email email, DateOfBirth dateOfBirth) {
    requireNonBlank(firstName, "First name");
    requireNonBlank(lastName, "Last name");
    Instant now = Instant.now();
    return new Student(null, code, firstName, lastName, email, dateOfBirth, now, now, 0L);
  }

  /**
   * Rehydrates a {@code Student} from data already validated at write time (a DB row) — bypasses
   * {@link #register} 's invariant checks, which only make sense for registrar-supplied input.
   */
  public static Student reconstitute(
      StudentId id,
      StudentCode code,
      String firstName,
      String lastName,
      Email email,
      DateOfBirth dateOfBirth,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new Student(id, code, firstName, lastName, email, dateOfBirth, createdAt, updatedAt, version);
  }

  /** Student.2-4 — email/dob format is already enforced by their VO constructors; only the blank-name check (Student.3) happens here. */
  public void applyChanges(String firstName, String lastName, Email email, DateOfBirth dateOfBirth) {
    requireNonBlank(firstName, "First name");
    requireNonBlank(lastName, "Last name");
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.dateOfBirth = dateOfBirth;
    this.updatedAt = Instant.now();
  }

  private static void requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new DomainValidationException(field + " must not be blank");
    }
  }

  public StudentId id() {
    return id;
  }

  public StudentCode code() {
    return code;
  }

  public String firstName() {
    return firstName;
  }

  public String lastName() {
    return lastName;
  }

  public Email email() {
    return email;
  }

  public DateOfBirth dateOfBirth() {
    return dateOfBirth;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public long version() {
    return version;
  }
}
