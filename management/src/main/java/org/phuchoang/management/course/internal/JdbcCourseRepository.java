package org.phuchoang.management.course.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.phuchoang.management.course.CourseId;
import org.phuchoang.management.course.domain.Course;
import org.phuchoang.management.course.CourseCode;
import org.phuchoang.management.course.domain.Credits;
import org.phuchoang.management.course.port.CourseRepository;
import org.phuchoang.management.shared.exception.StaleWriteException;
import org.phuchoang.management.shared.paging.CursorCodec;
import org.phuchoang.management.shared.paging.CursorPage;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCourseRepository implements CourseRepository {

  private final SpringDataCourseRepository springRepo;

  JdbcCourseRepository(SpringDataCourseRepository springRepo) {
    this.springRepo = springRepo;
  }

  @Override
  public Optional<Course> findByCode(CourseCode code) {
    return springRepo.findByCourseCode(code.value()).map(this::toDomain);
  }

  @Override
  public boolean existsByCode(CourseCode code) {
    return springRepo.existsByCourseCode(code.value());
  }

  @Override
  public CursorPage<Course> search(String query, String afterKey, int limit) {
    String booleanQuery = toBooleanModeQuery(query);
    List<CourseRow> matched =
        booleanQuery == null || booleanQuery.isBlank()
            ? springRepo.browse(afterKey, limit + 1)
            : springRepo.search(booleanQuery, afterKey, limit + 1);
    List<Course> rows = matched.stream().map(this::toDomain).toList();

    boolean hasMore = rows.size() > limit;
    List<Course> content = hasMore ? rows.subList(0, limit) : rows;
    String nextCursor = hasMore ? CursorCodec.encode(content.get(content.size() - 1).code().value()) : null;
    return new CursorPage<>(content, nextCursor);
  }

  @Override
  public List<Course> findByCodes(Collection<CourseCode> codes) {
    // A guard, not an optimisation: `IN ()` is a syntax error in MySQL, and an empty batch lookup
    // is a perfectly ordinary call (e.g. an enrollment page naming no courses).
    if (codes.isEmpty()) {
      return List.of();
    }
    List<String> rawCodes = codes.stream().map(CourseCode::value).toList();
    return springRepo.findByCourseCodeIn(rawCodes).stream().map(this::toDomain).toList();
  }

  // Builds a MySQL boolean-mode FULLTEXT expression requiring every token in the raw query as a
  // prefix match, mirroring how the built-in FULLTEXT parser tokenizes indexed text on
  // non-alphanumeric boundaries -- see JdbcStudentRepository.toBooleanModeQuery for the full
  // rationale (a single glued trailing '*' breaks on any query with its own word separators, and a
  // plain multi-word AGAINST with no '+' defaults to OR). Tokens under innodb_ft_min_token_size's
  // default of 3 characters are dropped, not required -- MySQL never indexes them, so requiring one
  // would make the whole query unsatisfiable. null/blank pass through untouched.
  private static String toBooleanModeQuery(String raw) {
    if (raw == null || raw.isBlank()) {
      return raw;
    }
    StringBuilder booleanQuery = new StringBuilder();
    for (String token : raw.split("[^\\p{Alnum}]+")) {
      if (token.length() < 3) {
        continue;
      }
      if (booleanQuery.length() > 0) {
        booleanQuery.append(' ');
      }
      booleanQuery.append('+').append(token).append('*');
    }
    return booleanQuery.isEmpty() ? null : booleanQuery.toString();
  }

  @Override
  public Map<String, Long> enrollmentCountsFor(Collection<String> courseCodes) {
    // A guard, not an optimisation: `IN ()` is a syntax error in MySQL, and an empty page is a
    // perfectly ordinary search result.
    if (courseCodes.isEmpty()) {
      return Map.of();
    }
    return springRepo.enrollmentCountsFor(courseCodes).stream()
        .collect(
            Collectors.toMap(
                CourseEnrollmentCountRow::courseCode, CourseEnrollmentCountRow::enrolledCount));
  }

  @Override
  public long enrollmentCountOf(CourseCode code) {
    return springRepo.enrollmentCountOf(code.value());
  }

  @Override
  public Course save(Course course) {
    try {
      return toDomain(springRepo.save(toRow(course)));
    } catch (OptimisticLockingFailureException e) {
      throw new StaleWriteException("Course " + course.code().value() + " was modified concurrently");
    }
  }

  @Override
  public void deleteByCode(CourseCode code) {
    springRepo.deleteByCourseCode(code.value());
  }

  private CourseRow toRow(Course course) {
    CourseId id = course.id();
    return new CourseRow(
        id == null ? null : id.value(),
        course.code().value(),
        course.name(),
        course.description(),
        course.credits().value(),
        course.version(),
        course.createdAt(),
        course.updatedAt());
  }

  private Course toDomain(CourseRow row) {
    return Course.reconstitute(
        new CourseId(row.id()),
        new CourseCode(row.courseCode()),
        row.name(),
        row.description(),
        new Credits(row.credits()),
        row.createdAt(),
        row.updatedAt(),
        row.version());
  }
}
