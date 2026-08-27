package org.phuchoang.management.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionLimit;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import tools.jackson.databind.ObjectMapper;

/**
 * The RBAC matrix, session-based auth, and login/change-password/view-initial-password endpoint
 * rules {@code 02-component-diagram.md} §4 and {@code 04-authentication-authorization.md} §1/§6
 * already fixed as decisions, wired up as an actual {@code SecurityFilterChain}
 * (06-low-level-design.md §11.1).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final ObjectMapper objectMapper = new ObjectMapper();

  // AuthenticationConfiguration is resolved here rather than exposed as its own AuthenticationManager
  // @Bean: declaring one would make getAuthenticationManager() resolve to this method itself,
  // mid-construction (StackOverflowError). Injected as-is, it builds the manager from the beans
  // already in the context -- identity's AppUserDetailsService plus the PasswordEncoder below.
  /**
   * The list of live sessions UC-27/UC-28 read and revoke.
   *
   * <p>Declared as a bean rather than left to {@code SessionManagementConfigurer} to create: {@code
   * SessionRegistryImpl} is an {@code ApplicationListener}, and only a registry that is a bean
   * receives {@code SessionDestroyedEvent}/{@code SessionIdChangedEvent} and prunes itself.
   *
   * <p>In-memory and per-JVM, which is the deployment this system describes
   * (01-system-overview.md). It empties on restart, and with more than one instance it would only
   * ever show the sessions belonging to whichever instance served the request.
   */
  @Bean
  public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }

  /**
   * Bridges the servlet container's {@code HttpSessionEvent}s into the application context. Without
   * it nothing tells {@link #sessionRegistry()} that a session was logged out or timed out, and the
   * registry accumulates dead session ids indefinitely.
   */
  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      AuthenticationConfiguration authenticationConfiguration,
      MustChangePasswordFilter mustChangePasswordFilter,
      SessionRegistry sessionRegistry)
      throws Exception {
    JsonUsernamePasswordAuthenticationFilter loginFilter =
        new JsonUsernamePasswordAuthenticationFilter(authenticationConfiguration.getAuthenticationManager());
    loginFilter.setFilterProcessesUrl("/api/v1/auth/login");
    loginFilter.setAuthenticationSuccessHandler(this::onLoginSuccess);
    loginFilter.setAuthenticationFailureHandler(this::onLoginFailure);
    // Without this the filter keeps its default RequestAttributeSecurityContextRepository, which
    // discards the authenticated context at the end of the login request -- no session, no
    // JSESSIONID, every following request unauthenticated. Session-based auth is the decision in
    // 04-authentication-authorization.md §1, so the context has to be saved to the HTTP session.
    loginFilter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
    // Set by hand, and it has to be. This filter is installed with addFilterAt below rather than
    // built by the DSL, so no AbstractAuthenticationFilterConfigurer runs for it -- and that
    // configurer is the only thing that consumes the SessionAuthenticationStrategy shared object
    // .sessionManagement() publishes. Left alone the filter keeps its inherited
    // NullAuthenticatedSessionStrategy, which does nothing, and there is no fallback:
    // SessionManagementFilter is not in the chain either. Two consequences, both fixed here:
    // the session was never registered (so the sessions endpoint would list nothing), and the
    // JSESSIONID was never rotated on login (so the app had no session-fixation protection).
    //
    // Order matters. Rotation must precede registration or the id recorded is the pre-rotation
    // one. ConcurrentSessionControlAuthenticationStrategy is deliberately absent: under an
    // unlimited session limit it only adds a getAllSessions() call per login.
    loginFilter.setSessionAuthenticationStrategy(
        new CompositeSessionAuthenticationStrategy(
            List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry))));

    http
        // No HTML form surface -- every write is a JSON body from a programmatic client, not a
        // browser <form> submission CSRF protection guards against (06-low-level-design.md §11.1).
        .csrf(AbstractHttpConfigurer::disable)
        // sessionConcurrency is what installs ConcurrentSessionFilter, and that filter is the
        // entire revocation mechanism: SessionInformation.expireNow() only sets a flag, and the
        // filter is what notices it, invalidates the session and answers. The limit itself is
        // UNLIMITED -- concurrent logins are not being capped, the machinery is being switched on.
        // FilterOrderRegistration places it after the login filter and before AuthorizationFilter,
        // so a revoked session is rejected ahead of both authorization and the must-change gate.
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .sessionConcurrency(concurrency -> concurrency
                .maximumSessions(SessionLimit.UNLIMITED)
                .sessionRegistry(sessionRegistry)
                .expiredSessionStrategy(new SessionRevokedExpiredStrategy(objectMapper))))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
            // PM-017 — public so it's callable before login; only reachable at all when
            // app.demo-accounts.enabled=true registers DemoAccountsController's bean (§11.4).
            .requestMatchers(HttpMethod.GET, "/api/v1/auth/demo-accounts").permitAll()
            // PM-029 — actuator is only ever exposed under the benchmark profile
            // (application-benchmark.properties); management.endpoints.web.exposure.include is
            // empty everywhere else, so these matchers are inert elsewhere. Health stays public so
            // liveness tooling doesn't need a session; everything else under /actuator/** is
            // metrics/introspection and is admin-only. Order matters between these two lines --
            // the broad matcher would otherwise shadow the health one.
            .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/actuator/**").hasRole("SYSTEM_ADMINISTRATOR")
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/password").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/v1/students/*/initial-password").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.POST, "/api/v1/students/**").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.PUT, "/api/v1/students/**").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/students/**").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/**").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/enrollments/**").hasRole("REGISTRAR")
            .requestMatchers(HttpMethod.POST, "/api/v1/courses/**").hasRole("COURSE_ADMINISTRATOR")
            .requestMatchers(HttpMethod.PUT, "/api/v1/courses/**").hasRole("COURSE_ADMINISTRATOR")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/courses/**").hasRole("COURSE_ADMINISTRATOR")
            .requestMatchers(HttpMethod.POST, "/api/v1/books/**").hasRole("LIBRARIAN")
            .requestMatchers(HttpMethod.PUT, "/api/v1/books/**").hasRole("LIBRARIAN")
            .requestMatchers(HttpMethod.PATCH, "/api/v1/books/**").hasRole("LIBRARIAN")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/books/**").hasRole("LIBRARIAN")
            .requestMatchers(HttpMethod.POST, "/api/v1/staff-accounts/**").hasRole("SYSTEM_ADMINISTRATOR")
            .requestMatchers(HttpMethod.PATCH, "/api/v1/staff-accounts/**").hasRole("SYSTEM_ADMINISTRATOR")
            // Required, not redundant: the domain-read allow-list below doesn't cover
            // /staff-accounts, so without this matcher a GET here would fall through to
            // .anyRequest().authenticated() and let every logged-in role enumerate staff.
            .requestMatchers(HttpMethod.GET, "/api/v1/staff-accounts/**").hasRole("SYSTEM_ADMINISTRATOR")
            // Same reasoning as the /staff-accounts GET above: without explicit matchers these
            // fall through to .anyRequest().authenticated() and every logged-in role could
            // enumerate -- and end -- everyone else's sessions.
            .requestMatchers(HttpMethod.GET, "/api/v1/sessions").hasRole("SYSTEM_ADMINISTRATOR")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/sessions/*").hasRole("SYSTEM_ADMINISTRATOR")
            .requestMatchers(HttpMethod.GET, "/api/v1/me/**").hasRole("STUDENT")
            // Read access is granted per resource, not as one undifferentiated "domain read": each
            // role reads only what its own work needs (02-component-diagram.md §4). These are
            // explicit allow-lists, not just absent grants -- without them a role would fall
            // through to .anyRequest().authenticated() below and read everything
            // (06-low-level-design.md §11.1, TC-XC-040).
            //
            // Every Student rule below is additionally scoped server-side to the caller's own
            // records inside the service (StudentService/BookService's callerStudentId), so the
            // grant is "your own row", not "every row".
            .requestMatchers(HttpMethod.GET, "/api/v1/students/**")
                .hasAnyRole("REGISTRAR", "LIBRARIAN", "COURSE_ADMINISTRATOR", "STUDENT")
            // Course Administrator is on that list without a Students tab in the UI: it reaches a
            // student record only by clicking through a course roster, which is a detail read, not
            // a browse.
            .requestMatchers(HttpMethod.GET, "/api/v1/books/**").hasAnyRole("LIBRARIAN", "STUDENT")
            .requestMatchers(HttpMethod.GET, "/api/v1/courses/**")
                .hasAnyRole("REGISTRAR", "COURSE_ADMINISTRATOR", "STUDENT")
            // No STUDENT: a Student's enrolled courses come from GET /api/v1/me/courses, which is
            // scoped by the session principal rather than by a caller-supplied student code, so
            // there is nothing here for one to read that /me doesn't already answer.
            .requestMatchers(HttpMethod.GET, "/api/v1/enrollments/**")
                .hasAnyRole("REGISTRAR", "COURSE_ADMINISTRATOR")
            .anyRequest().authenticated())
        .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(mustChangePasswordFilter, AuthorizationFilter.class);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  // The cast is safe: identity's AppUserDetailsService is the only UserDetailsService in the
  // context, so every principal that reaches a success handler is an AuthenticatedPrincipal.
  private void onLoginSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth)
      throws IOException {
    AuthenticatedPrincipal principal = (AuthenticatedPrincipal) auth.getPrincipal();
    res.setStatus(HttpServletResponse.SC_OK);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        res.getWriter(),
        Map.of("role", principal.role(), "mustChangePassword", principal.mustChangePassword()));
  }

  private void onLoginFailure(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
      throws IOException {
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        res.getWriter(),
        Map.of(
            "timestamp", Instant.now().toString(),
            "status", 401,
            "error", "Unauthorized",
            "message", "Invalid username or password",
            "path", req.getRequestURI()));
  }
}
