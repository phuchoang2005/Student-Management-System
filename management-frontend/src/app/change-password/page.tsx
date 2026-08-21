'use client';

import { Alert, Box, Button, Card, Center, Stack } from '@chakra-ui/react';
import NextLink from 'next/link';
import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';

import ErrorBanner from '@/components/ErrorBanner';
import FormField from '@/components/FormField';
import PageHeader from '@/components/PageHeader';
import { auth } from '@/lib/api/endpoints';
import { useAuth } from '@/lib/auth/AuthContext';
import { landingRoute } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import useAsyncAction from '@/lib/hooks/useAsyncAction';

/**
 * The one route a must-change-password session is allowed to reach — `allowDuringPasswordChange`
 * is what stops `RequireAuth` bouncing the user back here forever.
 *
 * It sits *outside* the `(app)` route group on purpose, next to `/login`. That group's layout
 * guards its children with a plain `<RequireAuth>`, which would send a forced-change session back
 * to the route it is already on and render a spinner instead of this form — an account provisioned
 * by `User.provisionStaff` (`mustChangePassword = true`) could never get past it. Outside the
 * group, this page's own guard is the only one that applies, and a forced user sees just the form:
 * no sidebar full of links the backend's `MustChangePasswordFilter` would 403 anyway.
 */
export default function ChangePasswordPage() {
  return (
    <RequireAuth allowDuringPasswordChange>
      <ChangePasswordForm />
    </RequireAuth>
  );
}

function ChangePasswordForm() {
  const { session, clearMustChange } = useAuth();
  const router = useRouter();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [retypeNewPassword, setRetypeNewPassword] = useState('');
  const [done, setDone] = useState(false);

  const forced = session?.mustChangePassword ?? false;
  const action = useAsyncAction(auth.changePassword);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const result = await action.run(currentPassword, newPassword, retypeNewPassword);
    if (result === undefined && action.error) return;
    // The backend rewrites the live session's principal at the same moment, so neither side needs a
    // re-login — the local flag just has to catch up.
    clearMustChange();
    setDone(true);
    setCurrentPassword('');
    setNewPassword('');
    setRetypeNewPassword('');
    if (forced && session) router.replace(landingRoute(session.role));
  };

  return (
    <Center minH="100vh" p="6">
      <Box w="full" maxW="30rem">
        <PageHeader
          title="Change password"
          description={
            forced
              ? 'Your account is still on its system-generated password. Choose a new one to continue.'
              : 'Set a new password for your account.'
          }
        />

        {done && !forced ? (
          <Alert.Root status="success" mb="4">
            <Alert.Indicator />
            <Alert.Content>
              <Alert.Description>Your password has been changed.</Alert.Description>
            </Alert.Content>
          </Alert.Root>
        ) : null}

        <form onSubmit={onSubmit}>
          <Card.Root>
            <Card.Body>
              <Stack gap="4">
                {/* A wrong current password comes back 401 here, not 400 -- the one place outside login
                    that does, so it is rendered inline rather than treated as an expired session. */}
                <ErrorBanner error={action.error} />
                <FormField
                  label="Current password"
                  name="currentPassword"
                  type="password"
                  autoComplete="current-password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  error={action.error?.fieldError('currentPassword')}
                  required
                />
                <FormField
                  label="New password"
                  name="newPassword"
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  error={action.error?.fieldError('newPassword')}
                  required
                />
                <FormField
                  label="Retype new password"
                  name="retypeNewPassword"
                  type="password"
                  autoComplete="new-password"
                  value={retypeNewPassword}
                  onChange={(e) => setRetypeNewPassword(e.target.value)}
                  error={action.error?.fieldError('retypeNewPassword')}
                  required
                />
              </Stack>
            </Card.Body>
            <Card.Footer justifyContent="space-between">
              <Button type="submit" loading={action.pending}>
                Change password
              </Button>
              {/* Reached from the sidebar rather than forced into: without the shell around it,
                  this link is the only way back. */}
              {!forced && session ? (
                <Button asChild size="sm" variant="ghost">
                  <NextLink href={landingRoute(session.role)}>Back</NextLink>
                </Button>
              ) : null}
            </Card.Footer>
          </Card.Root>
        </form>
      </Box>
    </Center>
  );
}
