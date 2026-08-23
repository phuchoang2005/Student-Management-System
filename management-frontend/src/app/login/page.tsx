'use client';

import { Alert, Box, Center, Heading, HStack, Stack, Text } from '@chakra-ui/react';
import { useRouter } from 'next/navigation';
import { useEffect, useState, type FormEvent } from 'react';

import ErrorBanner from '@/components/ErrorBanner';
import FormField from '@/components/FormField';
import FadeIn from '@/components/motion/FadeIn';
import StaggerItem from '@/components/motion/Stagger';
import Button from '@/components/ui/Button';
import RoleBadge from '@/components/ui/RoleBadge';
import SurfaceCard from '@/components/ui/SurfaceCard';
import { auth } from '@/lib/api/endpoints';
import type { DemoAccount } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import { landingRoute } from '@/lib/auth/permissions';

/**
 * The first screen anyone sees, and the one place the Zen brief has room to breathe: a single card,
 * one objective (§9), and nothing else competing for the eye.
 */
export default function LoginPage() {
  const { session, ready, login } = useAuth();
  const router = useRouter();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [demoAccounts, setDemoAccounts] = useState<DemoAccount[]>([]);

  const action = useAsyncAction(async (user: string, pass: string) => login(user, pass));

  // Already signed in (a refresh, or a back-button return): go where this role belongs.
  useEffect(() => {
    if (!ready || !session) return;
    router.replace(session.mustChangePassword ? '/change-password' : landingRoute(session.role));
  }, [ready, session, router]);

  // PM-017. The route is public but only registered when `app.demo-accounts.enabled` is true, so a
  // 404 here is the production configuration working, not a failure — the chips simply don't show.
  useEffect(() => {
    auth
      .demoAccounts()
      .then(setDemoAccounts)
      .catch(() => setDemoAccounts([]));
  }, []);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const next = await action.run(username, password);
    if (next) {
      router.replace(next.mustChangePassword ? '/change-password' : landingRoute(next.role));
    }
  };

  return (
    <Center minH="100vh" p="8">
      <FadeIn>
        <Box w="full" maxW="26rem">
          <Heading size="xl" mb="2">
            Student Management
          </Heading>
          <Text color="fg.muted" fontSize="sm" mb="8">
            Sign in to continue.
          </Text>

          <form onSubmit={onSubmit}>
            <SurfaceCard
              footer={
                <Button type="submit" w="full" loading={action.pending}>
                  Sign in
                </Button>
              }
            >
              <Stack gap="6">
                {/* A failed login is 401 with a body; every other 4xx here would be unexpected. */}
                <ErrorBanner error={action.error} />
                <FormField
                  label="Username"
                  name="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  required
                  helper="Staff sign in with their account name; students use their email address."
                />
                <FormField
                  label="Password"
                  name="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  required
                />
              </Stack>
            </SurfaceCard>
          </form>

          {demoAccounts.length > 0 ? (
            <Box mt="8">
              <Text fontSize="sm" fontWeight="medium" mb="4">
                Demo accounts
              </Text>
              <Stack gap="2">
                {demoAccounts.map((account, index) => (
                  <StaggerItem key={account.username} index={index}>
                    <HStack justify="space-between" gap="4">
                      <HStack gap="2" minW="0">
                        <RoleBadge role={account.role} />
                        <Text fontSize="sm" color="fg.muted" truncate>
                          {account.username}
                        </Text>
                      </HStack>
                      <Button
                        tone="neutral"
                        size="sm"
                        variant="outline"
                        onClick={() => {
                          setUsername(account.username);
                          setPassword(account.password);
                        }}
                      >
                        Use
                      </Button>
                    </HStack>
                  </StaggerItem>
                ))}
              </Stack>
              <Alert.Root status="info" mt="6" size="sm">
                <Alert.Indicator />
                <Alert.Content>
                  <Alert.Description>
                    A student account only exists once a Registrar has registered one — a student
                    login requires a real <code>students</code> row, so no student is seeded.
                  </Alert.Description>
                </Alert.Content>
              </Alert.Root>
            </Box>
          ) : null}
        </Box>
      </FadeIn>
    </Center>
  );
}
