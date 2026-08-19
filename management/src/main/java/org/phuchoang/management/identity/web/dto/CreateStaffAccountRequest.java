package org.phuchoang.management.identity.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code role} stays a raw {@code String} here too (UC-24) -- {@code IdentityService.provisionStaff}
 * is where {@code Role.STAFF_ROLES} membership, including the SYSTEM_ADMINISTRATOR rejection, is
 * enforced, so an unrecognized value fails the same validated way as an out-of-range one.
 */
public record CreateStaffAccountRequest(@NotBlank String username, @NotBlank String role) {}
