package org.phuchoang.management.benchmark;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.phuchoang.management.book.domain.Isbn;
import org.phuchoang.management.course.domain.Credits;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.InvalidEmailException;
import org.phuchoang.management.student.StudentCode;
import org.phuchoang.management.student.domain.Email;

/**
 * BM-JMH-003 (03-benchmark-scenarios.md §9): is domain validation measurable at all?
 *
 * <p><b>Expected answer: no.</b> {@link Email}'s {@code Pattern} is already {@code static final},
 * so nothing recompiles per instance, and every other value object here does no more than a
 * length/blank/sign check. This benchmark exists to <em>prove</em> that, so nobody spends effort
 * optimizing a path that costs nanoseconds — a flat, sub-microsecond result across all four types
 * is the successful outcome, not a failed measurement.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class ValueObjectBenchmark {

  @Benchmark
  public Email validEmail() {
    return new Email("valid@example.test");
  }

  @Benchmark
  public void invalidEmail(Blackhole bh) {
    try {
      new Email("not-an-email");
    } catch (InvalidEmailException e) {
      bh.consume(e);
    }
  }

  @Benchmark
  public StudentCode validStudentCode() {
    return new StudentCode("STU-0001");
  }

  @Benchmark
  public void invalidStudentCode(Blackhole bh) {
    try {
      new StudentCode("");
    } catch (DomainValidationException e) {
      bh.consume(e);
    }
  }

  @Benchmark
  public Isbn validIsbn() {
    return new Isbn("978-3-16-148410-0");
  }

  @Benchmark
  public void invalidIsbn(Blackhole bh) {
    try {
      new Isbn("");
    } catch (DomainValidationException e) {
      bh.consume(e);
    }
  }

  @Benchmark
  public Credits validCredits() {
    return new Credits(3);
  }

  @Benchmark
  public void invalidCredits(Blackhole bh) {
    try {
      new Credits(-1);
    } catch (DomainValidationException e) {
      bh.consume(e);
    }
  }
}
