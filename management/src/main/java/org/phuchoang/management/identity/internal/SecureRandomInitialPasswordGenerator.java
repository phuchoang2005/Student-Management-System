package org.phuchoang.management.identity.internal;

import java.security.SecureRandom;
import org.phuchoang.management.identity.port.InitialPasswordGenerator;
import org.springframework.stereotype.Component;

@Component
class SecureRandomInitialPasswordGenerator implements InitialPasswordGenerator {

  private static final String ALPHANUMERIC =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  private static final int LENGTH = 8;

  private final SecureRandom random = new SecureRandom();

  @Override
  public String generate() {
    StringBuilder password = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      password.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
    }
    return password.toString();
  }
}
