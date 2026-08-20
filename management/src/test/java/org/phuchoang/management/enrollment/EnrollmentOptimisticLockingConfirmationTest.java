package org.phuchoang.management.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.enrollment.application.EnrollmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TC-XC-019 (P2, "design confirmation") — {@code cross-cutting.md} §3: unlike {@code Student},
 * {@code Course}, {@code Book}, and {@code User}, {@code Enrollment} carries no {@code @Version}
 * and has no update use case (06-low-level-design.md §7), so no {@code StaleWriteException}
 * concurrency test exists for it. This documents *why* that gap exists rather than leaving it look
 * like an oversight, mirroring {@code EnrollmentRow}'s own Javadoc.
 */
@SpringBootTest
@Testcontainers
class EnrollmentOptimisticLockingConfirmationTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void enrollmentsTableHasNoVersionColumn() {
    List<String> columns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'enrollments'",
            String.class);

    assertThat(columns).noneMatch("version"::equalsIgnoreCase);
  }

  @Test
  void enrollmentServiceHasNoUpdateMethod() {
    List<String> methodNames =
        Arrays.stream(EnrollmentService.class.getDeclaredMethods()).map(Method::getName).toList();

    assertThat(methodNames).noneMatch(name -> name.toLowerCase().contains("update"));
  }
}
