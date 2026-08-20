package org.phuchoang.management.identity.web;

import jakarta.validation.Valid;
import org.phuchoang.management.identity.application.IdentityService;
import org.phuchoang.management.identity.web.dto.CreateStaffAccountRequest;
import org.phuchoang.management.identity.web.dto.SetStatusRequest;
import org.phuchoang.management.identity.web.dto.StaffAccountResponse;
import org.phuchoang.management.identity.web.dto.StaffAccountStatusResponse;
import org.phuchoang.management.identity.web.dto.StaffAccountSummaryDto;
import org.phuchoang.management.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-24/UC-25 — a separate controller from {@code AuthController}, not just separate methods:
 * every endpoint here requires {@code hasRole("SYSTEM_ADMINISTRATOR")} (already wired in {@code
 * SecurityConfig}), a rule none of {@code AuthController}'s endpoints share
 * (06-low-level-design.md §8.5). Both methods delegate straight to {@code IdentityService} — no
 * additional orchestration at the web layer.
 */
@RestController
@RequestMapping("/api/v1/staff-accounts")
public class StaffAccountController {

  private final IdentityService identityService;
  private final StaffAccountMapper mapper;

  public StaffAccountController(IdentityService identityService, StaffAccountMapper mapper) {
    this.identityService = identityService;
    this.mapper = mapper;
  }

  /**
   * UC-25's read half. {@link #setStatus} is keyed by a numeric user id that {@link
   * #createStaffAccount} deliberately does not return, so this is the only way a client can find
   * the account it needs to enable or disable.
   */
  @GetMapping
  public PageResponse<StaffAccountSummaryDto> listStaffAccounts(Pageable pageable) {
    return PageResponse.from(identityService.listStaffAccounts(pageable).map(mapper::toSummaryDto));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public StaffAccountResponse createStaffAccount(@Valid @RequestBody CreateStaffAccountRequest request) {
    IdentityService.ProvisionedStaffAccount provisioned =
        identityService.provisionStaff(mapper.toCommand(request));
    return mapper.toResponse(provisioned);
  }

  @PatchMapping("/{id}/status")
  public StaffAccountStatusResponse setStatus(
      @PathVariable Long id, @Valid @RequestBody SetStatusRequest request) {
    IdentityService.StaffAccountStatus status =
        identityService.setAccountEnabled(id, request.enabled());
    return mapper.toResponse(status);
  }
}
