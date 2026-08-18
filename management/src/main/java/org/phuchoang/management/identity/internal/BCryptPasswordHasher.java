package org.phuchoang.management.identity.internal;

import org.phuchoang.management.identity.domain.PasswordHash;
import org.phuchoang.management.identity.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Wraps the {@code PasswordEncoder} bean {@code shared.security.SecurityConfig} already configures. */
@Component
class BCryptPasswordHasher implements PasswordHasher {

  private final PasswordEncoder passwordEncoder;

  BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public PasswordHash hash(String plaintext) {
    return new PasswordHash(passwordEncoder.encode(plaintext));
  }

  @Override
  public boolean matches(String plaintext, PasswordHash hash) {
    return passwordEncoder.matches(plaintext, hash.value());
  }
}
