package org.phuchoang.management.course.application.command;

public record CreateCourseCommand(String courseCode, String name, String description, int credits) {}
