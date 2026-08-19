package org.phuchoang.management.identity.internal;

import org.phuchoang.management.identity.domain.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
record UserRow(
    @Id Long id,
    String username,
    String passwordHash,
    // Nullable: cleared the instant the account holder changes their password (Identity.4).
    String initialPasswordEncrypted,
    Role role,
    Long studentId,
    boolean mustChangePassword,
    // Primitive long, not boxed -- see StudentRow's @Version comment.
    @Version long version) {}
