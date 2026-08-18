package org.phuchoang.management.shared;

import org.junit.jupiter.api.Test;
import org.phuchoang.management.ManagementApplication;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Mechanical backstop for the "public API, not a port" rule (06-low-level-design.md §2.1):
 * nothing outside a module may import from that module's {@code internal/} package.
 */
class ModuleBoundaryTest {

  @Test
  void moduleStructureIsRespected() {
    ApplicationModules.of(ManagementApplication.class).verify();
  }
}
