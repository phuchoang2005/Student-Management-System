package org.phuchoang.management.identity.port;

/** Identity.3 — generates the system-issued 8-character alphanumeric initial password. */
public interface InitialPasswordGenerator {

  String generate();
}
