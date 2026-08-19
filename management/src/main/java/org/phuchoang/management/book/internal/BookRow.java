package org.phuchoang.management.book.internal;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Table("books")
record BookRow(
    @Id Long id,
    String isbn,
    String title,
    String author,
    LocalDate publishedDate,
    Long ownerId,
    // Must be a primitive long, not boxed Long: Spring Data JDBC's default IsNewStrategy treats a
    // boxed @Version wrapper as "already persisted" the moment it's non-null, even at 0 -- which
    // made every first-ever save() attempt an UPDATE (with no id yet) instead of an INSERT.
    @Version long version,
    Instant createdAt,
    Instant updatedAt) {}
