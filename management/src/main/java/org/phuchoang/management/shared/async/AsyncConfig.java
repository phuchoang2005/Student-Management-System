package org.phuchoang.management.shared.async;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
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
 *
 * <p>{@code @EnableScheduling} lives here rather than on its own configuration class because its
 * only consumer today is {@link EventPublicationRecoveryJob}, the backstop for the same PM-047 /
 * H6 fix this executor's sizing addresses — see that class's Javadoc for the stated recovery
 * window.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

  /**
   * PM-047 / H6: {@code BM-XC-001} at N=200 found 568 of 801 {@code EVENT_PUBLICATION} rows
   * permanently incomplete because the previous sizing (core=2, max=4, queue=50 — capacity for 54
   * in-flight/queued tasks) silently dropped everything past that under the default {@code
   * AbortPolicy}, and nothing retried a rejected task.
   *
   * <p>The accepted, stated bound: core=8, max=20, queue=1000 comfortably clears a 200-student
   * burst (~801 publications, two listeners per {@code StudentDeleted}/{@code CourseDeleted}) with
   * headroom. {@link ThreadPoolExecutor.CallerRunsPolicy} is the second layer — even beyond this
   * sizing, a rejected task runs on the publishing thread instead of being dropped, so publication
   * loss is no longer possible at any burst size; the worst case is added latency on the publisher,
   * never a missing {@code EVENT_PUBLICATION} row.
   */
  @Bean
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(8);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("event-listener-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
