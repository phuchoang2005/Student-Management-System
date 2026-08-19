package org.phuchoang.management.book.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record BookCreateRequest(
    @NotBlank @Size(max = 20) String isbn,
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 255) String author,
    LocalDate publishedDate,
    Long ownerId) {}
