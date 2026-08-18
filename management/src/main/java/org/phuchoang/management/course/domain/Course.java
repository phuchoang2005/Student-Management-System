package org.phuchoang.management.course.domain;

import java.time.Instant;
import org.phuchoang.management.course.CourseId;
import org.phuchoang.management.shared.exception.DomainValidationException;

/**
 * {@code createdAt}/{@code updatedAt}/{@code version} are set by the application, not read back
 * from MySQL's column defaults, mirroring {@code Student} (06-low-level-design.md §4.4, §5).
 */
public class Course {

  private CourseId id;
  private final CourseCode code;
  private String name;
  private String description;
  private Credits credits;
  private final Instant createdAt;
  private Instant updatedAt;
  private final long version;

  private Course(
      CourseId id,
      CourseCode code,
      String name,
      String description,
      Credits credits,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.description = description;
    this.credits = credits;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  public static Course create(CourseCode code, String name, String description, Credits credits) {
    requireNonBlank(name, "Name");
    Instant now = Instant.now();
    return new Course(null, code, name, description, credits, now, now, 0L);
  }

  /**
   * Rehydrates a {@code Course} from data already validated at write time (a DB row) — bypasses
   * {@link #create}'s invariant checks, mirroring {@code Student.reconstitute}.
   */
  public static Course reconstitute(
      CourseId id,
      CourseCode code,
      String name,
      String description,
      Credits credits,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new Course(id, code, name, description, credits, createdAt, updatedAt, version);
  }

  private static void requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new DomainValidationException(field + " must not be blank");
    }
  }

  public CourseId id() {
    return id;
  }

  public CourseCode code() {
    return code;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public Credits credits() {
    return credits;
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
