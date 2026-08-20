package org.phuchoang.management.identity.web;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.phuchoang.management.identity.application.IdentityService.ProvisionedStaffAccount;
import org.phuchoang.management.identity.application.IdentityService.StaffAccountStatus;
import org.phuchoang.management.identity.application.command.ProvisionStaffCommand;
import org.phuchoang.management.identity.web.dto.CreateStaffAccountRequest;
import org.phuchoang.management.identity.web.dto.StaffAccountResponse;
import org.phuchoang.management.identity.web.dto.StaffAccountStatusResponse;

@Mapper(componentModel = "spring")
public interface StaffAccountMapper {

  ProvisionStaffCommand toCommand(CreateStaffAccountRequest request);

  @Mapping(target = "initialPassword", source = "plaintextPassword")
  StaffAccountResponse toResponse(ProvisionedStaffAccount provisioned);

  StaffAccountStatusResponse toResponse(StaffAccountStatus status);
}
