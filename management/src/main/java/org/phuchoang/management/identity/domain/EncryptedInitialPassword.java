package org.phuchoang.management.identity.domain;

import org.phuchoang.management.shared.exception.DomainValidationException;

/**
 * Identity.5 — the base64 AES ciphertext of the *system-issued* initial password, the only
 * reversible form of any password in this design (04-authentication-authorization.md §2.2). Held
 * on {@link User} as a nullable field, matching {@code users.initial_password_encrypted}'s
 * nullable column (05-database-schema.md §3.5): {@code null} once the account holder changes their
 * password, from which point no password is recoverable by anyone (Identity.4).
 */
public record EncryptedInitialPassword(String cipherText) {

  public EncryptedInitialPassword {
    if (cipherText == null || cipherText.isBlank()) {
      throw new DomainValidationException("Encrypted initial password must not be blank");
    }
  }
}
