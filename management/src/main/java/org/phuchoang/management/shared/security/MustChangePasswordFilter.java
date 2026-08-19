package org.phuchoang.management.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The gate 04-authentication-authorization.md §4.2 specifies: 403 while {@code
 * principal.mustChangePassword} is true, unless the request is the Change Password endpoint itself
 * (06-low-level-design.md §11.3). Sits after authorization in the chain, so a request has already
 * been authenticated before this runs.
 *
 * <p>PM-006 shipped this as a pass-through stub because the {@link AuthenticatedPrincipal} type it
 * needs didn't exist yet; US-6.1 ships that type, which is what unblocks the real body here —
 * ahead of PM-011 (Sprint 4), whose remaining scope is the cross-cutting gate tests in
 * {@code Testing/03-test-cases/cross-cutting.md} §2, not this rule. Without it US-6.1's third
 * acceptance criterion ("I can take no other action until I change it") would have no
 * implementation.
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

  private static final String CHANGE_PASSWORD_PATH = "/api/v1/auth/password";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.getPrincipal() instanceof AuthenticatedPrincipal principal
        && principal.mustChangePassword()
        && !CHANGE_PASSWORD_PATH.equals(request.getRequestURI())) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    chain.doFilter(request, response);
  }
}
