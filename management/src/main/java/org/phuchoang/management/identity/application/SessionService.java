package org.phuchoang.management.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.phuchoang.management.shared.exception.DomainValidationException;
import org.phuchoang.management.shared.exception.FieldError;
import org.phuchoang.management.shared.exception.NotFoundException;
import org.phuchoang.management.shared.security.AuthenticatedPrincipal;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

/**
 * Who is signed in right now, and how a System Administrator ends one of those sessions (UC-27,
 * UC-28).
 *
 * <p>Reads Spring Security's {@link SessionRegistry} directly rather than any table of our own:
 * sessions live in the servlet container, so the registry is the only thing that knows about them.
 * That makes this view in-memory and per-JVM — it empties when the backend restarts, and under more
 * than one instance it would show only the sessions held by whichever instance served the request.
 * Both are consequences of the single-process deployment in 01-system-overview.md, not defects.
 *
 * <p><strong>Never look a principal up by constructing one.</strong> {@link AuthenticatedPrincipal}
 * is a record, so it has value-based {@code equals}/{@code hashCode}, and {@code SessionRegistryImpl}
 * keys its map on the principal object. {@code AuthController.changePassword} replaces the session's
 * principal with {@code withPasswordChanged()} without telling the registry, so a rebuilt principal
 * can differ from the stored key by a field and {@code getAllSessions(rebuilt, false)} silently
 * returns nothing. Every method here iterates {@code getAllPrincipals()} instead, which is
 * insensitive to that.
 *
 * <p>For the same reason the view deliberately carries no {@code mustChangePassword} or {@code
 * enabled}: the registry's snapshot of those can be stale. Account state belongs to the Staff
 * Accounts screen; this one is about sessions.
 */
@Service
public class SessionService {

  private final SessionRegistry sessionRegistry;

  public SessionService(SessionRegistry sessionRegistry) {
    this.sessionRegistry = sessionRegistry;
  }

  /** Every live session, newest first. {@code currentSessionId} marks the caller's own row. */
  public List<ActiveSessionView> listActiveSessions(String currentSessionId) {
    String currentHandle = currentSessionId == null ? null : handleOf(currentSessionId);
    return sessionRegistry.getAllPrincipals().stream()
        .filter(AuthenticatedPrincipal.class::isInstance)
        .map(AuthenticatedPrincipal.class::cast)
        .flatMap(
            principal ->
                // false: already-expired sessions are revoked-but-not-yet-collected, and listing
                // them would invite an administrator to end something already ended.
                sessionRegistry.getAllSessions(principal, false).stream()
                    .map(info -> toView(principal, info, currentHandle)))
        .sorted(Comparator.comparing(ActiveSessionView::lastRequest).reversed())
        .toList();
  }

  /**
   * Ends one session. The next request it makes is rejected with a 401 by {@code
   * ConcurrentSessionFilter} (see {@code SessionRevokedExpiredStrategy}).
   *
   * <p>Revocation is therefore <em>deferred</em>: {@code expireNow} sets a flag, it does not
   * invalidate the {@code HttpSession} there and then. The session object survives until its owner's
   * next request or until it times out. Nothing can be done with it in the meantime, which is the
   * property that matters — but "the session is gone now" would be too strong a claim.
   *
   * @param handle the opaque handle from {@link #listActiveSessions}, not a raw session id
   */
  public void revoke(String handle, String currentSessionId) {
    if (currentSessionId != null && handle.equals(handleOf(currentSessionId))) {
      // Guarded rather than allowed: ending your own session mid-task looks identical to the
      // feature being broken, and an administrator who wants out has Sign out.
      String message = "You cannot end your own session. Use sign out instead.";
      throw new DomainValidationException(message, List.of(new FieldError("handle", message)));
    }

    SessionInformation session =
        findByHandle(handle)
            .orElseThrow(() -> new NotFoundException("No active session matches that handle."));
    session.expireNow();
  }

  private Optional<SessionInformation> findByHandle(String handle) {
    // O(principals x sessions) with one digest each. The registry holds tens of entries on a
    // single-process deployment, so this is not worth an index -- and a cached handle -> id map
    // would be a second source of truth needing its own pruning on every SessionDestroyedEvent.
    return sessionRegistry.getAllPrincipals().stream()
        .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
        .filter(info -> handleOf(info.getSessionId()).equals(handle))
        .findFirst();
  }

  private ActiveSessionView toView(
      AuthenticatedPrincipal principal, SessionInformation info, String currentHandle) {
    String handle = handleOf(info.getSessionId());
    return new ActiveSessionView(
        handle,
        principal.username(),
        principal.role(),
        info.getLastRequest().toInstant(),
        handle.equals(currentHandle));
  }

  /**
   * A SHA-256 digest of the session id, never the id itself.
   *
   * <p>A session id <em>is</em> the credential — anything holding one can replay it as a {@code
   * JSESSIONID} cookie and become that user. Putting live ids on an admin screen would spread them
   * into browser history, screenshots and logs. The digest is stable, so it addresses a session for
   * revocation, and preimage-resistant, so it cannot be turned back into the cookie.
   *
   * <p>A fresh {@code MessageDigest} per call: the class is not thread-safe and this runs on request
   * threads.
   */
  private String handleOf(String sessionId) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(sessionId.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is mandated by the JDK; unreachable on any conformant runtime.
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  /**
   * One live session, as the API exposes it. Same VO-unwrapping rationale as the other application
   * layer view records — the web layer never touches a framework {@code SessionInformation}.
   */
  public record ActiveSessionView(
      String handle, String username, String role, Instant lastRequest, boolean current) {}
}
