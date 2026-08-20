package org.phuchoang.management.student.web.dto;

/** {@code InitialPasswordResponse} (api-specification.md) — UC-23's Registrar-only read. */
public record InitialPasswordResponse(String username, String initialPassword) {}
