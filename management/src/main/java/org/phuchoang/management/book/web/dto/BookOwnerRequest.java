package org.phuchoang.management.book.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookOwnerRequest(@NotBlank @Size(max = 20) String studentCode) {}
