package org.phuchoang.management.identity.application.command;

/** {@code role} is the raw request string, validated against {@code Role.STAFF_ROLES} in {@code IdentityService.provisionStaff} -- kept a primitive so the Web layer never touches {@code identity.domain.Role} directly (LayeringRulesTest). */
public record ProvisionStaffCommand(String username, String role) {}
