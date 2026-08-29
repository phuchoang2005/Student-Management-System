package org.phuchoang.management.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.phuchoang.management.shared.web.ErrorResponseWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

/**
 * What a revoked session's next request gets back.
 *
 * <p>{@code ConcurrentSessionFilter}'s default {@code ResponseBodySessionInformationExpiredStrategy}
 * prints one plain-text sentence and never calls {@code setStatus} — so a revoked session's next
 * request answers <strong>200 OK</strong> with prose in the body, which no client can distinguish
 * from success. This answers 401 in the same {@code Error} envelope as every other failure in the
 * API (api-specification.md §3), via the shared {@link ErrorResponseWriter} so {@code client.ts}
 * handles it through the path it already has.
 *
 * <p>Written straight to the response rather than thrown: {@code ConcurrentSessionFilter} runs ahead
 * of {@code DispatcherServlet}, so {@code GlobalExceptionHandler} can never see an exception raised
 * here.
 */
public class SessionRevokedExpiredStrategy implements SessionInformationExpiredStrategy {

  private final ErrorResponseWriter errorResponseWriter;

  public SessionRevokedExpiredStrategy(ErrorResponseWriter errorResponseWriter) {
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
    HttpServletRequest request = event.getRequest();
    HttpServletResponse response = event.getResponse();

    errorResponseWriter.write(
        request,
        response,
        HttpStatus.UNAUTHORIZED,
        "Your session was ended by an administrator. Please sign in again.");
    response.flushBuffer();
  }
}
