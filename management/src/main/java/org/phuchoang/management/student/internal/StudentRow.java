package org.phuchoang.management.student.internal;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Table("students")
record StudentRow(
    @Id Long id,
    String studentCode,
    String firstName,
    String lastName,
    String email,
    LocalDate dateOfBirth,
    // Must be a primitive long, not boxed Long: Spring Data JDBC's default IsNewStrategy treats a
    // boxed @Version wrapper as "already persisted" the moment it's non-null, even at 0 -- which
    // made every first-ever save() attempt an UPDATE (with no id yet) instead of an INSERT.
    @Version long version,
    Instant createdAt,
    Instant updatedAt) {}
