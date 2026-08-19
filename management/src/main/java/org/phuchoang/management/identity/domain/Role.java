package org.phuchoang.management.identity.domain;

import java.util.Set;

public enum Role {
  REGISTRAR,
  LIBRARIAN,
  COURSE_ADMINISTRATOR,
  STUDENT,
  SYSTEM_ADMINISTRATOR;

  /** The 3 roles {@code User.provisionStaff} accepts (Identity.6, UC-24 flow 3a). */
  public static final Set<Role> STAFF_ROLES = Set.of(REGISTRAR, LIBRARIAN, COURSE_ADMINISTRATOR);
}
