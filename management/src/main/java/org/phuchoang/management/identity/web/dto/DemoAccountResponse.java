package org.phuchoang.management.identity.web.dto;

/** PM-017 — one entry of {@code GET /api/v1/auth/demo-accounts} (04-authentication-authorization.md §8). */
public record DemoAccountResponse(String role, String username, String password) {}
