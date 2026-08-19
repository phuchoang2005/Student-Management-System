package org.phuchoang.management.identity.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.identity.application.command.ChangePasswordCommand;
import org.phuchoang.management.identity.web.dto.ChangePasswordRequest;

@Mapper(componentModel = "spring")
public interface AuthMapper {

  ChangePasswordCommand toCommand(ChangePasswordRequest request);
}
