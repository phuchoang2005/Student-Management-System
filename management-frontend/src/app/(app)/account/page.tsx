'use client';

import { Box, Stack, Text } from '@chakra-ui/react';
import { KeyRound, LogOut } from 'lucide-react';
import NextLink from 'next/link';
import { useRouter } from 'next/navigation';

import PageHeader from '@/components/PageHeader';
import RecordCard from '@/components/RecordCard';
import Button from '@/components/ui/Button';
import RoleBadge from '@/components/ui/RoleBadge';
import { useAuth } from '@/lib/auth/AuthContext';
import { CHANGE_PASSWORD_HREF, ROLE_LABELS } from '@/lib/auth/permissions';

/**
 * The signed-in person, and the two things they can do about it.
 *
 * A small page, and a deliberate one: "who am I signed in as" was previously answerable only by
 * reading the topbar, and the two account actions were loose in the sidebar between domain
 * sections. Giving them a destination means the shell's account block can stay a short list of
 * links rather than growing into a menu.
 *
 * `/change-password` is **linked**, not absorbed. It lives outside the `(app)` route group on
 * purpose: `MustChangePasswordFilter` forces a user there before anything else works, and
 * `RequireAuth` — which wraps this group — would redirect them straight back out again, so a user
 * holding a generated password would bounce forever. That placement is load-bearing; do not move it.
 */
export default function AccountPage() {
  const { session, logout } = useAuth();
  const router = useRouter();

  if (!session) return null;

  const signOut = async () => {
    await logout();
    router.replace('/login');
  };

  return (
    <Box maxW="42rem">
      <PageHeader title="Account" description="The account you are signed in with." />

      <RecordCard
        fields={[
          { label: 'Signed in as', value: session.username },
          { label: 'Role', value: <RoleBadge role={session.role} /> },
          { label: 'What this role reaches', value: ROLE_LABELS[session.role] },
        ]}
      />

      <Stack direction={{ base: 'column', sm: 'row' }} gap="3" mt="8" align="stretch">
        <Button asChild tone="neutral" variant="outline">
          <NextLink href={CHANGE_PASSWORD_HREF}>
            <KeyRound strokeWidth={1.5} />
            Change password
          </NextLink>
        </Button>
        <Button tone="neutral" variant="outline" onClick={signOut}>
          <LogOut strokeWidth={1.5} />
          Sign out
        </Button>
      </Stack>

      <Text textStyle="meta" color="fg.muted" mt="6" maxW="60ch">
        Signing out ends this session everywhere it is in use. A System Administrator can also end it
        for you, which is not the same as disabling the account.
      </Text>
    </Box>
  );
}
