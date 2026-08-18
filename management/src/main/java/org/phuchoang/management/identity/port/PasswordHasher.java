package org.phuchoang.management.identity.port;

import org.phuchoang.management.identity.domain.PasswordHash;

public interface PasswordHasher {

  PasswordHash hash(String plaintext);

  boolean matches(String plaintext, PasswordHash hash);
}
