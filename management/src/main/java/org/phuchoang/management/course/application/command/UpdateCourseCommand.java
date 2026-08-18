package org.phuchoang.management.course.application.command;

public record UpdateCourseCommand(String name, String description, int credits) {}
