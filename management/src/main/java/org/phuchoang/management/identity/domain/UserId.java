package org.phuchoang.management.identity.domain;

/** Mirrors {@code StudentId}'s nullable-before-save pattern (06-low-level-design.md §8.1). */
public record UserId(Long value) {}
