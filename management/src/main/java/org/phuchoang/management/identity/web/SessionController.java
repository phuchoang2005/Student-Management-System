package org.phuchoang.management.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.phuchoang.management.identity.application.SessionService;
import org.phuchoang.management.identity.web.dto.ActiveSessionDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-27/UC-28 — who is signed in, and ending one of those sessions. SYSTEM_ADMINISTRATOR only
 * ({@code SecurityConfig}).
 *
 * <p>Not paged, unlike every other list in this API: the session registry is an in-memory snapshot
 * with no stable ordering across calls and no way to offset into it, so paging it would hand out
 * pages that overlap and skip as sessions come and go. It is bounded by the number of people signed
 * in at once, which on this deployment is small.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

  private final SessionService sessionService;
  private final SessionMapper mapper;

  public SessionController(SessionService sessionService, SessionMapper mapper) {
    this.sessionService = sessionService;
    this.mapper = mapper;
  }

  @GetMapping
  public List<ActiveSessionDto> listActiveSessions(HttpServletRequest request) {
    return sessionService.listActiveSessions(currentSessionId(request)).stream()
        .map(mapper::toDto)
        .toList();
  }

  /**
   * 204 like every other delete here. The revoked session is not destroyed at this instant — its
   * owner's next request is what invalidates it and answers 401; see {@code SessionService.revoke}.
   */
  @DeleteMapping("/{handle}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeSession(@PathVariable String handle, HttpServletRequest request) {
    sessionService.revoke(handle, currentSessionId(request));
  }

  /** {@code false} — asking for the caller's session must never create one. */
  private String currentSessionId(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    return session == null ? null : session.getId();
  }
}
