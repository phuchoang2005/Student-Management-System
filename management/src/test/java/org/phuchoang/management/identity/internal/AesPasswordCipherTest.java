package org.phuchoang.management.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.identity.domain.EncryptedInitialPassword;

/** Lives in {@code internal} because {@link AesPasswordCipher} is package-private, like every adapter. */
class AesPasswordCipherTest {

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void encryptThenDecryptRoundTripsTheInitialPassword() {
    AesPasswordCipher cipher = new AesPasswordCipher(KEY);

    EncryptedInitialPassword encrypted = cipher.encrypt("aB3xY9zQ");

    assertThat(encrypted.cipherText()).isNotEqualTo("aB3xY9zQ");
    assertThat(cipher.decrypt(encrypted)).isEqualTo("aB3xY9zQ");
  }

  @Test
  void theSamePlaintextEncryptsToADifferentCiphertextEachTime() {
    // The per-encryption random IV is what stops two students issued the same initial password
    // from being identifiable as such from the column alone.
    AesPasswordCipher cipher = new AesPasswordCipher(KEY);

    assertThat(cipher.encrypt("aB3xY9zQ").cipherText())
        .isNotEqualTo(cipher.encrypt("aB3xY9zQ").cipherText());
  }

  @Test
  void ciphertextFitsTheVarchar255Column() {
    // 05-database-schema.md §3.5 — initial_password_encrypted is VARCHAR(255).
    AesPasswordCipher cipher = new AesPasswordCipher(KEY);

    assertThat(cipher.encrypt("aB3xY9zQ").cipherText().length()).isLessThanOrEqualTo(255);
  }

  @Test
  void aTamperedCiphertextFailsToDecryptRatherThanReturningGarbage() {
    // The reason for GCM over plain AES.
    AesPasswordCipher cipher = new AesPasswordCipher(KEY);
    String cipherText = cipher.encrypt("aB3xY9zQ").cipherText();
    byte[] bytes = Base64.getDecoder().decode(cipherText);
    bytes[bytes.length - 1] ^= 0x01;
    EncryptedInitialPassword tampered =
        new EncryptedInitialPassword(Base64.getEncoder().encodeToString(bytes));

    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aKeyOfTheWrongLengthIsRejectedAtStartup() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[8]);

    assertThatThrownBy(() -> new AesPasswordCipher(shortKey))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("16, 24 or 32 bytes");
  }
}
