/**
 * SecurityConfig.filterChain's authorization rules, mirrored as a capability map.
 *
 * The point is to hide nav items and action buttons *before* any request is made, so a 403 becomes
 * an edge case rather than the normal path. The server remains the real guarantee -- this is a
 * usability mirror of it, never a substitute.
 */

export const ROLES = {
  REGISTRAR: 'REGISTRAR',
  LIBRARIAN: 'LIBRARIAN',
  COURSE_ADMINISTRATOR: 'COURSE_ADMINISTRATOR',
  STUDENT: 'STUDENT',
  SYSTEM_ADMINISTRATOR: 'SYSTEM_ADMINISTRATOR',
};

/** The four roles granted domain reads. SYSTEM_ADMINISTRATOR is deliberately excluded. */
const DOMAIN_ROLES = [
  ROLES.REGISTRAR,
  ROLES.LIBRARIAN,
  ROLES.COURSE_ADMINISTRATOR,
  ROLES.STUDENT,
];

/** Capability -> roles that hold it. Keys are referenced by route guards and by button gating. */
const CAPABILITIES = {
  'domain:read': DOMAIN_ROLES,
  'students:write': [ROLES.REGISTRAR],
  'students:initial-password': [ROLES.REGISTRAR],
  'enrollments:write': [ROLES.REGISTRAR],
  'books:write': [ROLES.LIBRARIAN],
  'courses:write': [ROLES.COURSE_ADMINISTRATOR],
  'me:read': [ROLES.STUDENT],
  'staff:manage': [ROLES.SYSTEM_ADMINISTRATOR],
  // Every authenticated role can change its own password.
  'password:change': Object.values(ROLES),
};

export function can(role, capability) {
  if (!role) return false;
  return (CAPABILITIES[capability] ?? []).includes(role);
}

export const ROLE_LABELS = {
  REGISTRAR: 'Registrar',
  LIBRARIAN: 'Librarian',
  COURSE_ADMINISTRATOR: 'Course Admin',
  STUDENT: 'Student',
  SYSTEM_ADMINISTRATOR: 'System Admin',
};

/**
 * The sidebar, in order. `capability` gates visibility, so SYSTEM_ADMINISTRATOR sees exactly two
 * items -- Staff Accounts and Change Password -- which is the RBAC allow-list made visible.
 */
export const NAV_ITEMS = [
  { to: '/students', label: 'Students', capability: 'domain:read' },
  { to: '/books', label: 'Books', capability: 'domain:read' },
  { to: '/courses', label: 'Courses', capability: 'domain:read' },
  { to: '/enrollments', label: 'Enrollments', capability: 'domain:read' },
  { to: '/me', label: 'My Books & Courses', capability: 'me:read' },
  { to: '/staff-accounts', label: 'Staff Accounts', capability: 'staff:manage' },
  { to: '/change-password', label: 'Change Password', capability: 'password:change' },
];

/** Where each role lands after login. */
export function landingRoute(role) {
  if (role === ROLES.SYSTEM_ADMINISTRATOR) return '/staff-accounts';
  if (role === ROLES.STUDENT) return '/me';
  return '/students';
}
