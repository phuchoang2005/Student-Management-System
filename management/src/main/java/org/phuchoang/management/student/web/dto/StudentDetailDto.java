package org.phuchoang.management.student.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@code books}/{@code courses} are always {@code []} until US-5.5 wires the real composition in (StudentService.getDetail's Javadoc). */
public record StudentDetailDto(
    Long id,
    String studentCode,
    String firstName,
    String lastName,
    String email,
    LocalDate dateOfBirth,
    Instant createdAt,
    Instant updatedAt,
    List<Object> books,
    List<Object> courses) {}
