package org.phuchoang.management.identity.web.dto;

import java.time.Instant;

/**
 * One signed-in session.
 *
 * <p>{@code handle} is a SHA-256 digest of the session id, not the id itself — a session id is a
 * replayable credential, so the API never emits one. It is the address used to end the session
 * (api-specification.md §5 decision #13).
 *
 * <p>No {@code mustChangePassword} or {@code enabled}: the session registry's copy of those can be
 * stale after a password change, and account state belongs to {@code /staff-accounts} anyway.
 */
public record ActiveSessionDto(
    String handle, String username, String role, Instant lastRequest, boolean current) {}
