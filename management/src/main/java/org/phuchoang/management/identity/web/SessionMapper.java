package org.phuchoang.management.identity.web;

import org.mapstruct.Mapper;
import org.phuchoang.management.identity.application.SessionService.ActiveSessionView;
import org.phuchoang.management.identity.web.dto.ActiveSessionDto;

@Mapper(componentModel = "spring")
public interface SessionMapper {

  ActiveSessionDto toDto(ActiveSessionView view);
}
