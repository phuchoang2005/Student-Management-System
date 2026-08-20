package org.phuchoang.management.identity.web;

import java.util.List;
import org.mapstruct.Mapper;
import org.phuchoang.management.identity.application.IdentityService.DemoAccount;
import org.phuchoang.management.identity.application.command.ChangePasswordCommand;
import org.phuchoang.management.identity.web.dto.ChangePasswordRequest;
import org.phuchoang.management.identity.web.dto.DemoAccountResponse;

@Mapper(componentModel = "spring")
public interface AuthMapper {

  ChangePasswordCommand toCommand(ChangePasswordRequest request);

  List<DemoAccountResponse> toDemoAccountResponses(List<DemoAccount> accounts);
}
