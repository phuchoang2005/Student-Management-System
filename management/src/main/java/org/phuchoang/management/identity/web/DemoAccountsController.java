package org.phuchoang.management.identity.web;

import java.util.List;
import org.phuchoang.management.identity.application.IdentityService;
import org.phuchoang.management.identity.web.dto.DemoAccountResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PM-017, 04-authentication-authorization.md §8 — a developer/QA convenience only, with no
 * business use case (not part of {@code use-cases.md}). Registered only when {@code
 * app.demo-accounts.enabled=true} (defaulted {@code true} outside {@code prod}, hard-{@code false}
 * in {@code application-prod.properties}): making the bean itself conditional means a disabled
 * route 404s as if it never existed, rather than 403ing behind a security rule that could be
 * misconfigured or bypassed later (06-low-level-design.md §11.4).
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "app.demo-accounts.enabled", havingValue = "true")
public class DemoAccountsController {

  private final IdentityService identityService;
  private final AuthMapper mapper;

  public DemoAccountsController(IdentityService identityService, AuthMapper mapper) {
    this.identityService = identityService;
    this.mapper = mapper;
  }

  @GetMapping("/demo-accounts")
  public List<DemoAccountResponse> listDemoAccounts() {
    return mapper.toDemoAccountResponses(identityService.listDemoAccounts());
  }
}
