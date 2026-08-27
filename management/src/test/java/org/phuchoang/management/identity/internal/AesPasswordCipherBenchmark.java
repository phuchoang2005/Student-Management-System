package org.phuchoang.management.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.phuchoang.management.identity.domain.EncryptedInitialPassword;

/**
 * BM-JMH-002 (03-benchmark-scenarios.md §9): what {@link AesPasswordCipher}'s per-call {@code
 * Cipher.getInstance(TRANSFORMATION)} costs versus a reused {@code Cipher} instance.
 *
 * <p>Lives in {@code identity.internal}, not {@code ..benchmark..}, because {@link
 * AesPasswordCipher} and its constructor are package-private (same reason {@code
 * AesPasswordCipherTest} lives here). The reused-{@code Cipher} variant is hand-rolled locally,
 * not a change to {@code AesPasswordCipher} itself — a JMH result may never justify a code change
 * on its own (01-benchmark-strategy.md §5.1); it may only support a change also visible in a
 * {@code BM-*} k6 scenario.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class AesPasswordCipherBenchmark {

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
  private static final String PLAINTEXT = "aB3xY9zQ";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private AesPasswordCipher asWritten;
  private EncryptedInitialPassword sampleCiphertext;

  private Cipher reusableCipher;
  private SecretKey key;
  private final SecureRandom random = new SecureRandom();

  @Setup(Level.Trial)
  public void setUp() throws Exception {
    asWritten = new AesPasswordCipher(KEY);
    sampleCiphertext = asWritten.encrypt(PLAINTEXT);

    byte[] keyBytes = Base64.getDecoder().decode(KEY);
    key = new SecretKeySpec(keyBytes, "AES");
    reusableCipher = Cipher.getInstance(TRANSFORMATION);
  }

  @Benchmark
  public EncryptedInitialPassword encryptAsWritten() {
    return asWritten.encrypt(PLAINTEXT);
  }

  @Benchmark
  public String decryptAsWritten() {
    return asWritten.decrypt(sampleCiphertext);
  }

  @Benchmark
  public byte[] encryptReusedCipher() throws Exception {
    byte[] iv = new byte[IV_LENGTH_BYTES];
    random.nextBytes(iv);
    reusableCipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
    return reusableCipher.doFinal(PLAINTEXT.getBytes(StandardCharsets.UTF_8));
  }
}
