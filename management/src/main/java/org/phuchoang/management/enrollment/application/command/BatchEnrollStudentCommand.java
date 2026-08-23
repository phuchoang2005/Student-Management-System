package org.phuchoang.management.enrollment.application.command;

import java.util.List;

/**
 * One student, many courses. Business keys throughout, same as {@link EnrollStudentCommand} — the
 * batch changes how many courses are named per request, not how a student or a course is named.
 */
public record BatchEnrollStudentCommand(String studentCode, List<String> courseCodes) {}
