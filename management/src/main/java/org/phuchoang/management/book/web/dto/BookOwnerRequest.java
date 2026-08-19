package org.phuchoang.management.book.web.dto;

import jakarta.validation.constraints.NotNull;

public record BookOwnerRequest(@NotNull Long studentId) {}
