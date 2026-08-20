package org.phuchoang.management.shared.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The {@code UserDetails} every authenticated request carries (06-low-level-design.md §11.3) —
 * built by {@code identity}'s {@code AppUserDetailsService} at login, read back by {@link
 * SecurityConfig}'s success handler and {@link MustChangePasswordFilter}.
 *
 * <p>Lives in {@code shared.security}, not {@code identity}, and holds plain {@code String}/{@code
 * Long} fields rather than wrapping {@code identity}'s {@code User} aggregate — the literal shape
 * in 06-low-level-design.md §11.3. Two rules make that shape unbuildable here: {@code shared}
 * referencing {@code identity}'s types would close a module cycle ({@code identity} already
 * depends on {@code shared.exception}), which {@code ApplicationModules.verify()} rejects; and a
 * {@code User} field would put a Domain-layer type on a class the Web layer constructs, which
 * {@code LayeringRulesTest} rejects. Passing primitives is the same escape used for {@code
 * AccountProvisioning} — see that interface's Javadoc — and the data crossing the boundary is
 * unchanged.
 *
 * <p>{@code studentId} is {@code null} for the staff roles, mirroring {@code users.student_id}'s
 * role co-invariant (05-database-schema.md §3.5).
 */
public record AuthenticatedPrincipal(
    String username,
    String passwordHash,
    String role,
    Long studentId,
    boolean mustChangePassword,
    boolean enabled)
    implements UserDetails {

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }

  /** Identity.7 — a disabled account's {@code UserDetails} reports itself disabled to Spring Security. */
  @Override
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * The refreshed principal a successful Change Password installs into the live session
   * (04-authentication-authorization.md §5.1), so the {@link MustChangePasswordFilter} gate clears
   * without forcing a re-login (TC-IDN-015).
   */
  public AuthenticatedPrincipal withPasswordChanged() {
    return new AuthenticatedPrincipal(username, passwordHash, role, studentId, false, enabled);
  }

  /**
   * {@code student}/{@code book}/{@code enrollment}'s "own records only" scoping
   * (02-component-diagram.md §4) needs the caller's {@code studentId} wherever the general-purpose
   * read endpoints are reachable by all four domain roles, unlike {@code /me/**} which the filter
   * chain already restricts to STUDENT alone — so a blind cast isn't safe here the way it is in
   * {@code MeController}. Returns {@code null} for staff-role callers and for any principal that
   * isn't this type (test principals of other shapes), both treated as "unscoped" by callers. Also
   * null-safe for {@code authentication} itself — a standalone (filter-chain-free) MockMvc test
   * resolves an unset {@code Authentication} controller parameter to {@code null}, not a wrongly
   * typed principal.
   */
  public static Long studentIdOf(Authentication authentication) {
    return authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal
        ? principal.studentId()
        : null;
  }
}
