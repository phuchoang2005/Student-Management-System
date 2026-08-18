package org.phuchoang.management.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chain-position stub for PM-006 (04-sprint-backlog.md): reserves the {@code addFilterAfter}
 * slot right after authorization so the real gate can drop in without reshuffling the chain. The
 * actual rule — 403 unless {@code principal.mustChangePassword} is false or the path is {@code
 * /api/v1/auth/password} (04-authentication-authorization.md §4.2) — needs the {@code
 * AuthenticatedPrincipal} type the {@code identity} module doesn't ship until PM-011 (Sprint 4),
 * which replaces this class body in place.
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(request, response);
  }
}
