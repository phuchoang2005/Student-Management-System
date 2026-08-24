package org.phuchoang.management.shared.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.repository.config.JdbcConfiguration;

/**
 * Pins both halves of every date/time round trip to UTC, because by default they disagree about
 * which zone a zoneless MySQL {@code DATETIME} is written in.
 *
 * <p>On write, Spring Data JDBC resolves an {@code Instant} property to a {@code java.sql.Timestamp}
 * column ({@code JdbcColumnTypes}: {@code Temporal -> Timestamp}) and the store's {@code
 * InstantToTimestampConverter} hands over the correct epoch; Connector/J then renders it into the
 * connection time zone, which {@code application.properties} pins to UTC. That half is already
 * right. On read, {@code rs.getObject} on a {@code DATETIME} returns a {@code
 * java.time.LocalDateTime} ({@code MysqlType.DATETIME}'s default Java class) and Spring's stock
 * {@code Jsr310Converters.LocalDateTimeToInstantConverter} interprets it at {@code
 * ZoneId.systemDefault()} — so every read was off by the JVM's offset from UTC. Worse, {@code
 * JdbcStudentRepository.toRow}/{@code JdbcCourseRepository.toRow} write the value they last read
 * back into every UPDATE, so the error compounded once per {@code version}.
 *
 * <p>Only the reading side is registered for {@code Instant}. An {@code @WritingConverter Instant ->
 * LocalDateTime} would be dead code: {@code MappingRelationalConverter.determineCustomWriteTarget}
 * asks {@code CustomConversions} for the exact pair {@code (Instant, Timestamp)} first, and the
 * store converter already claims it.
 *
 * <p>{@code LocalDate} needs the write half instead. Its store converter is {@code
 * Timestamp.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())}, which at a positive UTC
 * offset sends the previous day's evening; MySQL then truncates that {@code DATETIME} into the
 * {@code DATE} column and {@code students.date_of_birth} lands a whole day early. Reads need no
 * converter — {@code rs.getObject} on a {@code DATE} returns a {@code LocalDate} with no zone
 * applied.
 *
 * <p>Registered as a plain {@code JdbcCustomConversions} bean rather than by extending {@code
 * AbstractJdbcConfiguration}: Boot's {@code
 * DataJdbcRepositoriesAutoConfiguration$SpringBootJdbcConfiguration.jdbcCustomConversions()} is
 * {@code @ConditionalOnMissingBean}, so this replaces that one bean and leaves the mapping context,
 * converter, dialect, aggregate template and repository registration auto-configured. Built through
 * {@code JdbcConfiguration.createCustomConversions} — the same call {@code
 * AbstractJdbcConfiguration} makes — so the dialect's own converters and simple types are not lost,
 * which {@code new JdbcCustomConversions(List.of(...))} would silently drop.
 *
 * <p>User converters win over the store's: {@code CustomConversions} reverses the {@code [user,
 * store, default]} list before registering it, and {@code GenericConversionService} registers each
 * pair with {@code addFirst}.
 */
@Configuration
public class JdbcConversionsConfig {

  @Bean
  public JdbcCustomConversions jdbcCustomConversions(JdbcDialect dialect) {
    return JdbcConfiguration.createCustomConversions(
        dialect, List.of(UtcLocalDateTimeToInstant.INSTANCE, UtcLocalDateToTimestamp.INSTANCE));
  }

  /** Singleton enums rather than lambdas or records, matching Spring's own {@code Jsr310Converters}. */
  @ReadingConverter
  enum UtcLocalDateTimeToInstant implements Converter<LocalDateTime, Instant> {
    INSTANCE;

    @Override
    public Instant convert(LocalDateTime source) {
      return source.toInstant(ZoneOffset.UTC);
    }
  }

  @WritingConverter
  enum UtcLocalDateToTimestamp implements Converter<LocalDate, Timestamp> {
    INSTANCE;

    @Override
    public Timestamp convert(LocalDate source) {
      return Timestamp.from(source.atStartOfDay(ZoneOffset.UTC).toInstant());
    }
  }
}
