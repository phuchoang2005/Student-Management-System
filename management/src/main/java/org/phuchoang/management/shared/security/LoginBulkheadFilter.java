package org.phuchoang.management.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.phuchoang.management.shared.web.ErrorResponseWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PM-048 / H5: BCrypt strength 10 puts a ~95ms/call floor under {@code POST /api/v1/auth/login}
 * (see {@code SecurityConfig}'s {@code passwordEncoder()}), and that work runs synchronously on
 * whatever Tomcat thread accepted the request — the same shared pool every other endpoint uses,
 * with no sizing of its own ({@code server.tomcat.*} is unset everywhere). {@code BM-IDN-001}'s
 * ramp shows login's own p95 growing from 170ms at 1 VU to 6069ms at 100 VUs; nothing before this
 * filter bounded how many Tomcat threads a login burst could occupy at once.
 *
 * <p>The accepted, stated bound ({@code app.security.login-bulkhead.permits}, default 20):
 * comfortably below Tomcat's default 200 max threads, so the rest of the application always keeps
 * the large majority of the pool free even under an arbitrarily large login burst, while still
 * large enough that ordinary concurrent logins (well below the {@code BM-IDN-001} knee) don't see
 * spurious rejections. A non-blocking {@link Semaphore#tryAcquire()} means a request past the
 * bound is rejected with {@code 429} immediately, before the authentication manager — and BCrypt —
 * are ever invoked, so the Tomcat thread handling it returns almost instantly rather than queuing
 * behind in-flight BCrypt work. This does not touch BCrypt's own work factor, which
 * {@code 01-benchmark-strategy.md}'s H5 entry is explicit is a security property, not a defect.
 */
@Component
public class LoginBulkheadFilter extends OncePerRequestFilter {

  private static final String LOGIN_PATH = "/api/v1/auth/login";

  private final Semaphore permits;
  private final ErrorResponseWriter errorResponseWriter;

  public LoginBulkheadFilter(
      @Value("${app.security.login-bulkhead.permits:20}") int permitCount,
      ErrorResponseWriter errorResponseWriter) {
    this.permits = new Semaphore(permitCount);
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!isLoginRequest(request)) {
      chain.doFilter(request, response);
      return;
    }

    if (!permits.tryAcquire()) {
      errorResponseWriter.write(
          request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many concurrent login attempts, try again shortly");
      return;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      permits.release();
    }
  }

  private boolean isLoginRequest(HttpServletRequest request) {
    return "POST".equals(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI());
  }
}
