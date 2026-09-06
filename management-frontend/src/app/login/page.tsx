'use client';

import { Box, Grid, HStack, Heading, Icon, Separator, Stack, Text } from '@chakra-ui/react';
import { useRouter } from 'next/navigation';
import { useEffect, useState, type FormEvent } from 'react';

import ErrorBanner from '@/components/ErrorBanner';
import FormField from '@/components/FormField';
import FadeIn from '@/components/motion/FadeIn';
import StaggerItem from '@/components/motion/Stagger';
import Button from '@/components/ui/Button';
import Key from '@/components/ui/Key';
import SurfaceCard from '@/components/ui/SurfaceCard';
import { auth } from '@/lib/api/endpoints';
import type { DemoAccount, Role } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import { ROLE_LABELS, landingRoute, navItemsFor } from '@/lib/auth/permissions';

/**
 * What each role is for, in one line, from `docs-v00/BA-docs/use-cases.md`'s actor table.
 *
 * Written as the job rather than the permission — "keeps the catalogue and tracks who is holding
 * which book" is what a Librarian would say they do; "holds books:read and books:write" is what the
 * security config says about them, and nobody chooses a vantage point from a capability list.
 */
const ROLE_WORK: Record<Role, string> = {
  REGISTRAR: 'Registers students, and decides which courses they take.',
  LIBRARIAN: 'Keeps the catalogue, and tracks who is holding which book.',
  COURSE_ADMINISTRATOR: 'Owns the course catalogue and reads its rosters.',
  STUDENT: 'Sees their own record, their own courses, their own books.',
  SYSTEM_ADMINISTRATOR: 'Provisions staff accounts and ends sessions. No student records at all.',
};

/**
 * The first screen anyone sees.
 *
 * The characteristic fact about this system is not that it has a login — every system has a login.
 * It is that five people sign in to the same URL and get five different applications: a Librarian
 * and a Course Administrator can open the same student and see different halves of them, and a
 * System Administrator, who can do the most, can read no student record at all. The boundary is the
 * product.
 *
 * So the board is the page and the form is a panel beside it. Each role states the work it does and
 * then shows **its actual sidebar** — the same `navItemsFor()` call the shell makes, with the same
 * icons and labels — so choosing a role is choosing a vantage point and you can see what you are
 * choosing before you commit. It replaces five near-identical rows of `[badge] username [Use]`,
 * which were the busiest thing on a page where they carried the least.
 *
 * This is also the app's one orchestrated motion moment: the board settles in on first paint and
 * nothing else in the product performs an entrance like it.
 *
 * `GET /auth/demo-accounts` is only registered when `app.demo-accounts.enabled` is true, so in
 * production the request 404s and the board is simply absent — the form then stands alone and
 * centred, which is why it is a self-contained panel rather than a column of this layout.
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

  // PM-017. A 404 here is the production configuration working, not a failure.
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

  const form = (
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
  );

  const hasBoard = demoAccounts.length > 0;

  return (
    <Box minH="100vh" px={{ base: '5', md: '10' }} py={{ base: '10', md: '16' }}>
      <Box maxW={hasBoard ? '64rem' : '26rem'} mx="auto">
        <FadeIn>
          <Box mb={{ base: '10', md: '14' }} maxW="34rem">
            <Heading size="lg">Student Management</Heading>
            <Text textStyle="prose" color="fg.muted" mt="3">
              {hasBoard
                ? 'Five roles sign in here, and each one gets a different application. Pick a vantage point below, or sign in with your own account.'
                : 'Sign in to continue.'}
            </Text>
          </Box>
        </FadeIn>

        {hasBoard ? (
          <Grid
            gap={{ base: '12', lg: '16' }}
            alignItems="start"
            templateColumns={{ base: '1fr', lg: '20rem minmax(0, 1fr)' }}
          >
            <Stack gap="6">
              {form}
              {/* Kept from the old screen because it answers the question the board provokes:
                  there are five roles here and only four of them can be tried. */}
              <Text textStyle="meta" color="fg.muted" maxW="30ch">
                A student account only exists once a Registrar has registered one, so no student is
                seeded — register one to sign in as them.
              </Text>
            </Stack>
            <Stack gap="0" as="ul" listStyleType="none">
              {demoAccounts.map((account, index) => (
                <StaggerItem key={account.username} index={index}>
                  <RoleEntry
                    account={account}
                    first={index === 0}
                    onUse={() => {
                      setUsername(account.username);
                      setPassword(account.password);
                    }}
                  />
                </StaggerItem>
              ))}
            </Stack>
          </Grid>
        ) : (
          <FadeIn>{form}</FadeIn>
        )}
      </Box>
    </Box>
  );
}

/**
 * One role: what it does, the sidebar it will get, and the account that opens it.
 *
 * The sections come from `navItemsFor()` — the same function `AppShell` calls — so this preview
 * cannot drift from the nav it is previewing. A hairline separates entries instead of a card
 * apiece: five bordered boxes would make the list read as five products rather than five views of
 * one.
 */
function RoleEntry({
  account,
  first,
  onUse,
}: {
  account: DemoAccount;
  first: boolean;
  onUse: () => void;
}) {
  const sections = navItemsFor(account.role);

  return (
    <Box as="li" pt={first ? '0' : '6'} pb="6">
      {first ? null : <Separator mb="6" />}
      <HStack justify="space-between" align="flex-start" gap="6" mb="2">
        <Heading size="md">{ROLE_LABELS[account.role]}</Heading>
        <Button tone="neutral" variant="outline" size="sm" onClick={onUse} flexShrink={0}>
          Use this role
        </Button>
      </HStack>

      <Text textStyle="body" color="fg.muted" maxW="46ch">
        {ROLE_WORK[account.role]}
      </Text>

      <Key muted mt="3" display="block">
        {account.username}
      </Key>

      <HStack gap="5" mt="4" wrap="wrap">
        {sections.map((section) => (
          <HStack key={section.href} gap="2" color="fg.subtle">
            <Icon as={section.icon} boxSize="4" strokeWidth={1.5} aria-hidden />
            <Text textStyle="meta">{section.label}</Text>
          </HStack>
        ))}
      </HStack>
    </Box>
  );
}
