package org.phuchoang.management.identity.web.dto;

/**
 * UC-25 — one row of {@code GET /api/v1/staff-accounts}. Unlike {@link StaffAccountResponse} this
 * carries {@code id}: it is the only place the API surfaces the numeric user id that {@code PATCH
 * /api/v1/staff-accounts/{id}/status} is keyed by.
 *
 * <p>No password field of any kind — the one-time {@code initialPassword} is returned exactly once,
 * at creation (Identity.3, Identity.6), and must never be re-readable from a list.
 */
public record StaffAccountSummaryDto(Long id, String username, String role, boolean enabled) {}
