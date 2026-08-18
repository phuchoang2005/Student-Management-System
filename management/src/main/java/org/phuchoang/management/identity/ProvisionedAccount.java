package org.phuchoang.management.identity;

/** Result of {@link AccountProvisioning#provisionForStudent}: the one-time plaintext password. */
public record ProvisionedAccount(String username, String plaintextPassword) {}
