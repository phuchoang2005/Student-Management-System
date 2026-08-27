package org.phuchoang.management.benchmark;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.openjdk.jmh.infra.Blackhole;
import org.phuchoang.management.course.CourseSummary;
import org.phuchoang.management.enrollment.application.EnrollmentService.EnrollmentDetailView;
import org.phuchoang.management.enrollment.web.EnrollmentMapper;
import org.phuchoang.management.enrollment.web.EnrollmentMapperImpl;
import org.phuchoang.management.student.StudentSummary;
import org.phuchoang.management.student.application.StudentService.StudentSummaryView;
import org.phuchoang.management.student.web.StudentMapper;
import org.phuchoang.management.student.web.StudentMapperImpl;

/**
 * BM-JMH-004 (03-benchmark-scenarios.md §9): bounds the non-I/O share of a list response — the
 * alternative hypothesis whenever H1/H2 is suspected of being "the mapping, not the queries."
 *
 * <p>Neither {@link StudentMapper} nor {@link EnrollmentMapper} declares {@code uses = {...}}, so
 * their generated {@code *Impl} classes have plain no-arg constructors and need no Spring
 * container — {@code componentModel = "spring"} only adds {@code @Component} to the generated
 * class.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class MapperBenchmark {

  @Param({"20", "100"})
  public int rows;

  private StudentMapper studentMapper;
  private EnrollmentMapper enrollmentMapper;
  private List<StudentSummaryView> studentRows;
  private List<EnrollmentDetailView> enrollmentRows;

  @Setup(Level.Trial)
  public void setUp() {
    studentMapper = new StudentMapperImpl();
    enrollmentMapper = new EnrollmentMapperImpl();

    studentRows = new ArrayList<>(rows);
    enrollmentRows = new ArrayList<>(rows);
    for (int i = 0; i < rows; i++) {
      studentRows.add(
          new StudentSummaryView(
              "STU-" + i, "First" + i, "Last" + i, "student" + i + "@example.test"));
      enrollmentRows.add(
          new EnrollmentDetailView(
              new StudentSummary(
                  "STU-" + i, "First" + i, "Last" + i, "student" + i + "@example.test"),
              new CourseSummary("CRS-" + i, "Course " + i, 3),
              Instant.now()));
    }
  }

  @Benchmark
  public void mapStudentPage(Blackhole bh) {
    for (StudentSummaryView row : studentRows) {
      bh.consume(studentMapper.toSummaryDto(row));
    }
  }

  @Benchmark
  public void mapEnrollmentPage(Blackhole bh) {
    for (EnrollmentDetailView row : enrollmentRows) {
      bh.consume(enrollmentMapper.toDetailDto(row));
    }
  }
}
