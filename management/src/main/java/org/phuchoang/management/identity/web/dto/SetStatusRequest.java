package org.phuchoang.management.identity.web.dto;

import jakarta.validation.constraints.NotNull;

public record SetStatusRequest(@NotNull Boolean enabled) {}
