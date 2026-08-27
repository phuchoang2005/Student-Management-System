package org.phuchoang.management.benchmark;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BM-JMH-001 (03-benchmark-scenarios.md §9): the BCrypt work-factor cost curve behind H5.
 * Strength 10 — the default {@code SecurityConfig.passwordEncoder()} inherits from the no-arg
 * constructor — was never chosen against a measurement; this curve makes the security/latency
 * trade-off explicit and sets the floor under the Login SLO and BM-IDN-001's knee.
 *
 * <p>{@code matches} is benchmarked against a precomputed hash so it measures verification only,
 * not verification plus a fresh {@code encode}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class BcryptBenchmark {

  private static final String PLAINTEXT = "aB3xY9zQ";

  @Param({"4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14"})
  public int strength;

  private BCryptPasswordEncoder encoder;
  private String precomputedHash;

  @Setup(Level.Trial)
  public void setUp() {
    encoder = new BCryptPasswordEncoder(strength);
    precomputedHash = encoder.encode(PLAINTEXT);
  }

  @Benchmark
  public String encode() {
    return encoder.encode(PLAINTEXT);
  }

  @Benchmark
  public boolean matches() {
    return encoder.matches(PLAINTEXT, precomputedHash);
  }
}
