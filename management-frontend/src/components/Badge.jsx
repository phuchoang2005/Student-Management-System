import { ROLE_LABELS } from '../auth/permissions.js';

/** Role badge -- colour-keyed per role so "who am I logged in as" is readable at a glance. */
export function RoleBadge({ role }) {
  return <span className={`badge badge--${role}`}>{ROLE_LABELS[role] ?? role}</span>;
}

export default function Badge({ children, variant = 'neutral' }) {
  return <span className={`badge badge--${variant}`}>{children}</span>;
}
