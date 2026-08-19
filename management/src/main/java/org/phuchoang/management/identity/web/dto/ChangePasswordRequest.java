package org.phuchoang.management.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The length bounds duplicate {@code IdentityService}'s policy check on purpose: bean validation
 * gives the per-field {@code ValidationError} envelope, and the service-level check keeps the
 * §5.2 policy enforced for any caller that doesn't go through this DTO.
 */
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 8, max = 72) String newPassword,
    @NotBlank @Size(min = 8, max = 72) String retypeNewPassword) {}
