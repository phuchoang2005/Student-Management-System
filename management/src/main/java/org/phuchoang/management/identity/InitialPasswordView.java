package org.phuchoang.management.identity;

/** Result of {@link InitialPasswordLookup#viewInitialPassword}: the decrypted initial password. */
public record InitialPasswordView(String username, String initialPassword) {}
