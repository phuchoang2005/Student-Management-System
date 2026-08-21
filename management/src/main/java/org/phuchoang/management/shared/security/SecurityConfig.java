package org.phuchoang.management.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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
  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      AuthenticationConfiguration authenticationConfiguration,
      MustChangePasswordFilter mustChangePasswordFilter)
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

    http
        // No HTML form surface -- every write is a JSON body from a programmatic client, not a
        // browser <form> submission CSRF protection guards against (06-low-level-design.md §11.1).
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
            // PM-017 — public so it's callable before login; only reachable at all when
            // app.demo-accounts.enabled=true registers DemoAccountsController's bean (§11.4).
            .requestMatchers(HttpMethod.GET, "/api/v1/auth/demo-accounts").permitAll()
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
