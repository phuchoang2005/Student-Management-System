package org.phuchoang.management.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code POST /api/v1/auth/login} takes a JSON body ({@code {username, password}}, UC-21) rather
 * than the form-urlencoded parameters the framework default reads, so this parses the body itself
 * and otherwise behaves identically (06-low-level-design.md §11.2).
 */
public class JsonUsernamePasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public JsonUsernamePasswordAuthenticationFilter(AuthenticationManager authenticationManager) {
    super(authenticationManager);
  }

  @Override
  public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
    try {
      LoginRequest body = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
      var authRequest = UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password());
      setDetails(request, authRequest);
      return getAuthenticationManager().authenticate(authRequest);
    } catch (IOException | JacksonException e) {
      throw new AuthenticationServiceException("Malformed login request body", e);
    }
  }

  private record LoginRequest(String username, String password) {}
}
