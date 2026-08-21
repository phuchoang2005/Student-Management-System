'use client';

import { useRouter } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';
import { Center, Spinner } from '@chakra-ui/react';

import { useAuth } from './AuthContext';
import { can, type Capability } from './permissions';
import Forbidden from '@/components/Forbidden';

/**
 * Three rules, applied in order:
 *   1. no session                        → /login
 *   2. mustChangePassword, off that page → /change-password
 *   3. capability not held               → Forbidden
 *
 * Rule 2 mirrors `MustChangePasswordFilter`, which 403s every URI except `/api/v1/auth/password`.
 * It exists so the forced-change flow feels like a flow rather than a wall of failed requests; the
 * server enforcement is still the real guarantee.
 */
export default function RequireAuth({
  capability,
  allowDuringPasswordChange = false,
  children,
}: {
  capability?: Capability;
  /** Set on /change-password itself — the one route rule 2 must not bounce. */
  allowDuringPasswordChange?: boolean;
  children: ReactNode;
}) {
  const { session, ready } = useAuth();
  const router = useRouter();

  const redirectTo = !session
    ? '/login'
    : session.mustChangePassword && !allowDuringPasswordChange
      ? '/change-password'
      : null;

  useEffect(() => {
    // Navigating during render is not allowed in the App Router; `ready` also keeps the server
    // render (which never has a session) from bouncing an authenticated user to /login.
    if (ready && redirectTo) router.replace(redirectTo);
  }, [ready, redirectTo, router]);

  if (!ready || redirectTo) {
    return (
      <Center py="20">
        <Spinner size="lg" />
      </Center>
    );
  }

  if (capability && !can(session!.role, capability)) {
    return <Forbidden />;
  }

  return <>{children}</>;
}
