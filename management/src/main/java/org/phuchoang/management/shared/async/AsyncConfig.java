package org.phuchoang.management.shared.async;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@code @ApplicationModuleListener} (used by {@code BookService}/{@code
 * EnrollmentService.onStudentDeleted}/{@code onCourseDeleted}) is meta-annotated {@code @Async},
 * but Spring never proxies a bean for async dispatch without {@code @EnableAsync} registered
 * somewhere — without it those listeners ran synchronously, on the same thread as the publishing
 * transaction's commit, contradicting `02-component-diagram.md` §2.3 / `03-sequence-diagrams.md`'s
 * "HTTP response does not wait for listeners" design.
 *
 * <p>The executor bean is named {@code taskExecutor} deliberately: Spring's {@code
 * AsyncExecutionAspectSupport} resolves a bare {@code @Async} (no qualifier, as {@code
 * @ApplicationModuleListener} declares it) to the single {@code TaskExecutor} bean in the context
 * if there's exactly one, otherwise to the bean named {@code taskExecutor} — Spring Boot's
 * autoconfigured {@code applicationTaskExecutor} would otherwise make that resolution ambiguous.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("event-listener-");
    executor.initialize();
    return executor;
  }
}
