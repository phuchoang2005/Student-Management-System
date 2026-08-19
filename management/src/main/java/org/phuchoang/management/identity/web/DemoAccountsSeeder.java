package org.phuchoang.management.identity.web;

import org.phuchoang.management.identity.application.IdentityService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PM-017 — seeds the 4 non-{@code STUDENT} demo accounts on startup so {@code
 * DemoAccountsController}'s credentials actually work against {@code POST /api/v1/auth/login}.
 * Gated by the same {@code app.demo-accounts.enabled} property as the controller bean itself, so a
 * {@code prod} deployment never runs this either. See {@link IdentityService#seedDemoAccounts()}
 * for why {@code demo.student} is excluded.
 */
@Component
@ConditionalOnProperty(name = "app.demo-accounts.enabled", havingValue = "true")
class DemoAccountsSeeder implements ApplicationRunner {

  private final IdentityService identityService;

  DemoAccountsSeeder(IdentityService identityService) {
    this.identityService = identityService;
  }

  @Override
  public void run(ApplicationArguments args) {
    identityService.seedDemoAccounts();
  }
}
