package org.phuchoang.management.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import tools.jackson.databind.ObjectMapper;

/**
 * What a revoked session's next request gets back.
 *
 * <p>{@code ConcurrentSessionFilter}'s default {@code ResponseBodySessionInformationExpiredStrategy}
 * prints one plain-text sentence and never calls {@code setStatus} — so a revoked session's next
 * request answers <strong>200 OK</strong> with prose in the body, which no client can distinguish
 * from success. This answers 401 in the same {@code Error} envelope as every other failure in the
 * API (api-specification.md §3), so {@code client.ts} handles it through the path it already has.
 *
 * <p>Written straight to the response rather than thrown: {@code ConcurrentSessionFilter} runs ahead
 * of {@code DispatcherServlet}, so {@code GlobalExceptionHandler} can never see an exception raised
 * here.
 *
 * <p>The body is assembled as a {@code Map} with a pre-formatted timestamp rather than as an {@code
 * ErrorResponse} record, matching {@code SecurityConfig.onLoginFailure} — the {@code ObjectMapper}
 * in this package is constructed directly rather than injected from Boot, and a bare Jackson mapper
 * renders {@code Instant} as a numeric epoch instead of ISO-8601.
 */
public class SessionRevokedExpiredStrategy implements SessionInformationExpiredStrategy {

  private final ObjectMapper objectMapper;

  public SessionRevokedExpiredStrategy(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
    HttpServletRequest request = event.getRequest();
    HttpServletResponse response = event.getResponse();

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getWriter(),
        Map.of(
            "timestamp", Instant.now().toString(),
            "status", HttpStatus.UNAUTHORIZED.value(),
            "error", HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            "message", "Your session was ended by an administrator. Please sign in again.",
            "path", request.getRequestURI()));
    response.flushBuffer();
  }
}
