package org.phuchoang.management.identity.web;

import org.phuchoang.management.identity.application.IdentityService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * 04-authentication-authorization.md §4.1 — the {@code UserDetailsService} Spring Security's
 * {@code DaoAuthenticationProvider} calls during {@code POST /api/v1/auth/login}, and the reason
 * {@code shared.security.SecurityConfig} can build a real {@code AuthenticationManager} at all.
 * Password comparison itself stays in the framework (against the {@code PasswordEncoder} bean);
 * this only resolves the account.
 *
 * <p>{@code @Component}, not {@code @Service}: it hosts no business rule, and {@code
 * NamingConventionsTest} requires every {@code @Service} to live in {@code application/}.
 */
@Component
public class AppUserDetailsService implements UserDetailsService {

  private final IdentityService identityService;

  public AppUserDetailsService(IdentityService identityService) {
    this.identityService = identityService;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    return identityService
        .loadPrincipal(username)
        .orElseThrow(() -> new UsernameNotFoundException(username));
  }
}
