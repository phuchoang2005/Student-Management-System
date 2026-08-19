package org.phuchoang.management.identity.port;

import org.phuchoang.management.identity.domain.EncryptedInitialPassword;

/**
 * Identity.5 — the reversible (AES) counterpart to {@link PasswordHasher}, used for the initial
 * password only, so a Registrar can read it back via US-6.3 until the student changes it
 * (04-authentication-authorization.md §2.2's security note).
 */
public interface PasswordCipher {

  EncryptedInitialPassword encrypt(String plaintext);

  String decrypt(EncryptedInitialPassword ciphertext);
}
