package org.phuchoang.management.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.phuchoang.management.identity.domain.EncryptedInitialPassword;
import org.phuchoang.management.identity.port.PasswordCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-GCM rather than the plain "AES" 04-authentication-authorization.md §2.2 names: GCM is
 * authenticated, so a tampered-with {@code initial_password_encrypted} column fails to decrypt
 * instead of yielding a silently wrong plaintext. A fresh random IV is generated per encryption
 * and prefixed to the ciphertext, which is what keeps two students issued the same initial
 * password from producing the same column value.
 *
 * <p>Key management and rotation are explicitly a build/ops concern (§9), so the key is read as
 * base64 application configuration; {@code application.properties} carries a development default
 * that {@code INITIAL_PASSWORD_KEY} overrides per environment.
 */
@Component
class AesPasswordCipher implements PasswordCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  AesPasswordCipher(@Value("${app.security.initial-password-key}") String base64Key) {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
      throw new IllegalStateException(
          "app.security.initial-password-key must decode to 16, 24 or 32 bytes, got " + keyBytes.length);
    }
    this.key = new SecretKeySpec(keyBytes, "AES");
  }

  @Override
  public EncryptedInitialPassword encrypt(String plaintext) {
    byte[] iv = new byte[IV_LENGTH_BYTES];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return new EncryptedInitialPassword(Base64.getEncoder().encodeToString(combined));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt the initial password", e);
    }
  }

  @Override
  public String decrypt(EncryptedInitialPassword ciphertext) {
    byte[] combined = Base64.getDecoder().decode(ciphertext.cipherText());
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(TAG_LENGTH_BITS, combined, 0, IV_LENGTH_BYTES));
      byte[] plaintext =
          cipher.doFinal(combined, IV_LENGTH_BYTES, combined.length - IV_LENGTH_BYTES);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to decrypt the stored initial password", e);
    }
  }
}
