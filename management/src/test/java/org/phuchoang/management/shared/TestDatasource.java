package org.phuchoang.management.shared;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;

/**
 * The one place a Testcontainers MySQL instance is bound to {@code spring.datasource.*}.
 *
 * <p>It exists for the {@code serverTimezone=UTC} parameter. {@code MySQLContainer.getJdbcUrl()}
 * carries no time-zone parameter, so Connector/J falls back to {@code connectionTimeZone=LOCAL} —
 * the JVM's zone — while {@code application.properties} pins production to UTC. Tests that bound the
 * bare URL were therefore writing and reading in the same non-UTC zone and round-tripping
 * <em>accidentally</em> correctly, which is exactly why the drift {@code JdbcConversionsConfig}
 * fixes went unnoticed: production was wrong and the suite was green.
 *
 * <p>Binding through one helper rather than repeating three lines per test class is the point — a
 * test class added later inherits the parameter instead of silently reintroducing the blind spot.
 */
public final class TestDatasource {

  private TestDatasource() {}

  public static void bind(DynamicPropertyRegistry registry, MySQLContainer<?> mysql) {
    registry.add("spring.datasource.url", () -> withUtcTimeZone(mysql.getJdbcUrl()));
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
  }

  /** Testcontainers appends its own parameters when a test sets any, so pick the separator. */
  private static String withUtcTimeZone(String jdbcUrl) {
    return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "serverTimezone=UTC";
  }
}
