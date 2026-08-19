package org.phuchoang.management.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.phuchoang.management.identity.application.IdentityService;
import org.phuchoang.management.identity.web.dto.ChangePasswordRequest;
import org.phuchoang.management.shared.security.AuthenticatedPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hosts {@code changePassword} only — {@code POST /api/v1/auth/login} never reaches a controller,
 * it is handled entirely inside the authentication filter chain (06-low-level-design.md §8.5).
 * {@code viewStudentInitialPassword} lives on {@code StudentController} instead; see {@link
 * org.phuchoang.management.identity.InitialPasswordLookup} for why.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final IdentityService identityService;
  private final AuthMapper mapper;

  /**
   * The default repository Spring Security's own {@code SecurityContextHolderFilter} uses, so
   * writing through it lands the refreshed principal in exactly the session attribute the next
   * request reads back.
   */
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  public AuthController(IdentityService identityService, AuthMapper mapper) {
    this.identityService = identityService;
    this.mapper = mapper;
  }

  @PostMapping("/password")
  public void changePassword(
      @Valid @RequestBody ChangePasswordRequest request,
      Authentication authentication,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    identityService.changePassword(authentication.getName(), mapper.toCommand(request));
    clearMustChangePassword(authentication, httpRequest, httpResponse);
  }

  /**
   * 04-authentication-authorization.md §5.1's "refresh cached principal in session" step: the
   * principal held in the session still says {@code mustChangePassword = true}, so without this
   * the caller would stay locked behind {@code MustChangePasswordFilter} until they re-logged in
   * (TC-IDN-015).
   */
  private void clearMustChangePassword(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    if (!(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
      return;
    }
    AuthenticatedPrincipal refreshed = principal.withPasswordChanged();
    Authentication refreshedAuth =
        UsernamePasswordAuthenticationToken.authenticated(
            refreshed, authentication.getCredentials(), refreshed.getAuthorities());

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(refreshedAuth);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
  }
}
