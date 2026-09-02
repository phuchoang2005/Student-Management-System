package org.phuchoang.management.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.phuchoang.management.shared.web.ErrorResponseWriter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * PM-048 / H5: unit-level coverage for the bulkhead mechanism itself ({@link
 * LoginBulkheadFilter}), independent of the full Spring Security filter chain -- proves a
 * saturated permit count rejects with 429 before the wrapped chain (and therefore the
 * authentication manager / BCrypt) ever runs, and that a released permit lets the next request
 * through.
 */
class LoginBulkheadFilterTest {

  private static final String LOGIN_PATH = "/api/v1/auth/login";
  private static final ErrorResponseWriter ERROR_RESPONSE_WRITER =
      new ErrorResponseWriter(new ObjectMapper());

  @Test
  void rejectsWithTooManyRequestsWhenNoPermitIsFreeThenRecoversOnceReleased() throws Exception {
    LoginBulkheadFilter filter = new LoginBulkheadFilter(1, ERROR_RESPONSE_WRITER);
    CountDownLatch firstRequestEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstRequest = new CountDownLatch(1);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      // The single permit is held by this in-flight "request" until releaseFirstRequest counts
      // down, simulating BCrypt work occupying the thread for the duration of the test.
      var firstResponse = new MockHttpServletResponse();
      var firstRequestDone =
          executor.submit(
              () -> {
                filter.doFilter(
                    loginRequest(),
                    firstResponse,
                    (req, res) -> {
                      firstRequestEntered.countDown();
                      try {
                        releaseFirstRequest.await(5, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                    });
                return null;
              });
      assertThat(firstRequestEntered.await(5, TimeUnit.SECONDS)).isTrue();

      // A second request arrives while the only permit is held -- rejected immediately, its
      // wrapped chain is never invoked.
      AtomicBoolean secondChainInvoked = new AtomicBoolean(false);
      MockHttpServletResponse secondResponse = new MockHttpServletResponse();
      filter.doFilter(loginRequest(), secondResponse, (req, res) -> secondChainInvoked.set(true));

      assertThat(secondResponse.getStatus()).isEqualTo(429);
      assertThat(secondChainInvoked).isFalse();

      releaseFirstRequest.countDown();
      firstRequestDone.get(5, TimeUnit.SECONDS);

      // The permit was released when the first request's chain returned -- a third request
      // should now be let through.
      AtomicBoolean thirdChainInvoked = new AtomicBoolean(false);
      MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
      filter.doFilter(loginRequest(), thirdResponse, (req, res) -> thirdChainInvoked.set(true));

      assertThat(thirdChainInvoked).isTrue();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void nonLoginRequestsAlwaysPassThroughRegardlessOfPermits() throws Exception {
    LoginBulkheadFilter filter = new LoginBulkheadFilter(0, ERROR_RESPONSE_WRITER);
    AtomicBoolean chainInvoked = new AtomicBoolean(false);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/students");

    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> chainInvoked.set(true));

    assertThat(chainInvoked).isTrue();
  }

  private static MockHttpServletRequest loginRequest() {
    return new MockHttpServletRequest("POST", LOGIN_PATH);
  }
}
