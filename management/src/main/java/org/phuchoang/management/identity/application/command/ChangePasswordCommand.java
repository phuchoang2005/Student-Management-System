package org.phuchoang.management.identity.application.command;

public record ChangePasswordCommand(
    String currentPassword, String newPassword, String retypeNewPassword) {}
