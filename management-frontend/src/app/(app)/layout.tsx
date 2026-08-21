'use client';

import AppShell from '@/components/AppShell';
import RequireAuth from '@/lib/auth/RequireAuth';

/**
 * Everything inside the `(app)` route group requires a session and renders inside the shell.
 * `/login` sits outside the group precisely so it gets neither.
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireAuth>
      <AppShell>{children}</AppShell>
    </RequireAuth>
  );
}
