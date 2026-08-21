'use client';

import { Center, Spinner } from '@chakra-ui/react';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

import { useAuth } from '@/lib/auth/AuthContext';
import { landingRoute } from '@/lib/auth/permissions';

/** Sends each role to its own landing page, since no single route is visible to all five. */
export default function Home() {
  const { session, ready } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!ready) return;
    if (!session) router.replace('/login');
    else if (session.mustChangePassword) router.replace('/change-password');
    else router.replace(landingRoute(session.role));
  }, [ready, session, router]);

  return (
    <Center minH="100vh">
      <Spinner size="lg" />
    </Center>
  );
}
