package org.phuchoang.management.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the {@link ErrorResponse} envelope straight to a servlet response, for the filter-chain
 * failure paths ({@code LoginBulkheadFilter}, {@code SecurityConfig.onLoginFailure},
 * {@code SessionRevokedExpiredStrategy}) that run ahead of {@code DispatcherServlet} and so can
 * never reach {@link GlobalExceptionHandler}.
 *
 * <p>Injects Boot's auto-configured {@link ObjectMapper} bean rather than a bare {@code new
 * ObjectMapper()} — the same instance MVC uses to serialize {@link ErrorResponse} correctly (an
 * {@code Instant} as ISO-8601) for every {@link GlobalExceptionHandler} response. A hand-built
 * mapper has no such configuration and renders {@code Instant} as a numeric epoch instead.
 */
@Component
public class ErrorResponseWriter {

  private final ObjectMapper objectMapper;

  public ErrorResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getWriter(),
        new ErrorResponse(
            Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
  }
}
