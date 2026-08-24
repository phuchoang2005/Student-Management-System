/**
 * `SecurityConfig.filterChain`'s authorization rules, mirrored as a capability map.
 *
 * The point is to hide nav items and action buttons *before* any request is made, so a 403 becomes
 * an edge case rather than the normal path. The server remains the real guarantee — this is a
 * usability mirror of it, never a substitute.
 *
 * Reads are granted **per resource**, not as one undifferentiated "domain read": each role sees the
 * data its own work needs and nothing else (02-component-diagram.md §4).
 */
import type { LucideIcon } from 'lucide-react';
import {
  BookOpen,
  ClipboardList,
  GraduationCap,
  KeyRound,
  RadioTower,
  ShieldCheck,
  Users,
} from 'lucide-react';

import type { Role } from '@/lib/api/types';

export const ROLES: Record<Role, Role> = {
  REGISTRAR: 'REGISTRAR',
  LIBRARIAN: 'LIBRARIAN',
  COURSE_ADMINISTRATOR: 'COURSE_ADMINISTRATOR',
  STUDENT: 'STUDENT',
  SYSTEM_ADMINISTRATOR: 'SYSTEM_ADMINISTRATOR',
};

export type Capability =
  | 'students:read'
  | 'students:write'
  | 'students:initial-password'
  | 'books:read'
  | 'books:write'
  | 'courses:read'
  | 'courses:write'
  | 'enrollments:read'
  | 'enrollments:write'
  | 'me:read'
  | 'staff:manage'
  | 'sessions:manage'
  | 'password:change';

/** Capability → roles that hold it. Keys are referenced by route guards and by button gating. */
const CAPABILITIES: Record<Capability, Role[]> = {
  // STUDENT is here because the server scopes their read to their own row; the Student UI reads
  // /me/profile instead, so this grant only backs the fallback path.
  'students:read': ['REGISTRAR', 'LIBRARIAN', 'COURSE_ADMINISTRATOR', 'STUDENT'],
  'students:write': ['REGISTRAR'],
  'students:initial-password': ['REGISTRAR'],
  'books:read': ['LIBRARIAN', 'STUDENT'],
  'books:write': ['LIBRARIAN'],
  'courses:read': ['REGISTRAR', 'COURSE_ADMINISTRATOR', 'STUDENT'],
  'courses:write': ['COURSE_ADMINISTRATOR'],
  'enrollments:read': ['REGISTRAR', 'COURSE_ADMINISTRATOR'],
  'enrollments:write': ['REGISTRAR'],
  'me:read': ['STUDENT'],
  'staff:manage': ['SYSTEM_ADMINISTRATOR'],
  'sessions:manage': ['SYSTEM_ADMINISTRATOR'],
  // Every authenticated role can change its own password.
  'password:change': [
    'REGISTRAR',
    'LIBRARIAN',
    'COURSE_ADMINISTRATOR',
    'STUDENT',
    'SYSTEM_ADMINISTRATOR',
  ],
};

export function can(role: Role | undefined | null, capability: Capability): boolean {
  if (!role) return false;
  return CAPABILITIES[capability].includes(role);
}

export const ROLE_LABELS: Record<Role, string> = {
  REGISTRAR: 'Registrar',
  LIBRARIAN: 'Librarian',
  COURSE_ADMINISTRATOR: 'Course Admin',
  STUDENT: 'Student',
  SYSTEM_ADMINISTRATOR: 'System Admin',
};

export interface NavItem {
  href: string;
  label: string;
  /** Which roles see the item. Not derived from a capability: see `roles` on `/students` below. */
  roles: Role[];
  /**
   * One outline glyph from Lucide (§7 — a single icon library, used consistently). The icon is
   * decorative: the label is always rendered beside it, so it never carries meaning on its own.
   */
  icon: LucideIcon;
}

/**
 * The sidebar, in order.
 *
 * Visibility is an explicit role list rather than a capability lookup, because the two differ in
 * one deliberate place: COURSE_ADMINISTRATOR holds `students:read` — it needs it to open a student
 * profile from a course roster — but gets no Students tab, because browsing students is not part of
 * its job. A capability-driven nav could not express "reachable, but not a destination".
 */
export const NAV_ITEMS: NavItem[] = [
  {
    href: '/students',
    label: 'Students',
    icon: Users,
    // STUDENT sees this item, but it renders their own record directly rather than a list.
    roles: ['REGISTRAR', 'LIBRARIAN', 'STUDENT'],
  },
  { href: '/books', label: 'Books', icon: BookOpen, roles: ['LIBRARIAN', 'STUDENT'] },
  {
    href: '/courses',
    label: 'Courses',
    icon: GraduationCap,
    roles: ['REGISTRAR', 'COURSE_ADMINISTRATOR', 'STUDENT'],
  },
  {
    href: '/enrollments',
    label: 'Enrollments',
    icon: ClipboardList,
    roles: ['REGISTRAR', 'COURSE_ADMINISTRATOR'],
  },
  { href: '/staff-accounts', label: 'Staff Accounts', icon: ShieldCheck, roles: ['SYSTEM_ADMINISTRATOR'] },
  {
    href: '/sessions',
    label: 'Active Sessions',
    icon: RadioTower,
    roles: ['SYSTEM_ADMINISTRATOR'],
  },
  {
    href: '/change-password',
    label: 'Change Password',
    icon: KeyRound,
    roles: ['REGISTRAR', 'LIBRARIAN', 'COURSE_ADMINISTRATOR', 'STUDENT', 'SYSTEM_ADMINISTRATOR'],
  },
];

export function navItemsFor(role: Role | undefined | null): NavItem[] {
  if (!role) return [];
  return NAV_ITEMS.filter((item) => item.roles.includes(role));
}

/** Where each role lands after login — no single route is visible to all five. */
export function landingRoute(role: Role): string {
  switch (role) {
    case 'SYSTEM_ADMINISTRATOR':
      return '/staff-accounts';
    case 'COURSE_ADMINISTRATOR':
      return '/courses';
    case 'LIBRARIAN':
      return '/books';
    default:
      // REGISTRAR lands on the student roll; STUDENT lands on their own record.
      return '/students';
  }
}
