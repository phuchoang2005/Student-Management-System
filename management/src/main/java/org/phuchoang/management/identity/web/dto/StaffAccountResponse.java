package org.phuchoang.management.identity.web.dto;

/** UC-24 — {@code initialPassword} is returned exactly once, in this response (Identity.3, Identity.6). */
public record StaffAccountResponse(String username, String role, String initialPassword) {}
