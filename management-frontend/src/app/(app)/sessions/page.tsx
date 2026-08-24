'use client';

import { Badge, Box, Code, Text } from '@chakra-ui/react';
import { RadioTower } from 'lucide-react';
import { useState } from 'react';

import ConfirmDialog from '@/components/ConfirmDialog';
import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import Button from '@/components/ui/Button';
import EmptyState from '@/components/ui/EmptyState';
import RoleBadge from '@/components/ui/RoleBadge';
import { sessions } from '@/lib/api/endpoints';
import type { ActiveSession } from '@/lib/api/types';
import RequireAuth from '@/lib/auth/RequireAuth';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import useResource from '@/lib/hooks/useResource';

/**
 * Who is signed in right now, and ending one of those sessions.
 *
 * Two things about this list are worth knowing before reading a number off it. It is held in the
 * backend's memory, not a table, so a restart empties it — nobody was signed out, the record of who
 * was simply did not survive. And revocation is deferred: ending a session flags it, and the person
 * is turned away on their *next* request rather than at the instant the button is pressed. Nothing
 * can be done with the session in between, which is the guarantee that matters.
 *
 * The rows carry no account state — whether the account is enabled, whether it owes a password
 * change. The registry's copy of those goes stale after a password change, and Staff Accounts
 * already owns them.
 */
export default function SessionsPage() {
  return (
    <RequireAuth capability="sessions:manage">
      <Sessions />
    </RequireAuth>
  );
}

function Sessions() {
  const resource = useResource<ActiveSession[]>(() => sessions.list());
  const [revoking, setRevoking] = useState<ActiveSession | null>(null);
  const revokeAction = useAsyncAction(sessions.revoke);

  const confirmRevoke = async () => {
    if (!revoking) return;
    // Checked through the return value: `revokeAction.error` holds the state this render closed
    // over, so on a first failure it is still null.
    const result = await revokeAction.run(revoking.handle);
    if (result !== undefined) {
      setRevoking(null);
      resource.refetch();
    }
  };

  return (
    <Box>
      <PageHeader
        title="Active sessions"
        description="Everyone signed in right now. Ending a session signs that person out on their next request."
        actions={
          <Button tone="neutral" variant="outline" onClick={() => resource.refetch()}>
            Refresh
          </Button>
        }
      />

      <ErrorBanner error={resource.error} />
      <ErrorBanner error={revokeAction.error} />

      <DataTable<ActiveSession>
        columns={[
          { key: 'username', header: 'User', cell: (row) => row.username },
          {
            key: 'role',
            header: 'Role',
            width: '10rem',
            cell: (row) => <RoleBadge role={row.role} />,
          },
          {
            key: 'lastRequest',
            header: 'Last seen',
            width: '13rem',
            cell: (row) => new Date(row.lastRequest).toLocaleString(),
          },
          {
            key: 'current',
            header: '',
            width: '8rem',
            cell: (row) =>
              row.current ? (
                <Badge colorPalette="accent" variant="subtle">
                  This session
                </Badge>
              ) : null,
          },
          {
            key: 'actions',
            header: '',
            width: '8rem',
            align: 'end' as const,
            cell: (row) => (
              <Button
                size="sm"
                tone="danger"
                variant="outline"
                // Your own session is not revocable here: ending it mid-task is indistinguishable
                // from the feature breaking, and signing out already does it deliberately.
                disabled={row.current}
                onClick={(event) => {
                  event.stopPropagation();
                  setRevoking(row);
                }}
              >
                End
              </Button>
            ),
          },
        ]}
        rows={resource.data ?? []}
        keyOf={(row) => row.handle}
        loading={resource.loading}
        empty={
          <EmptyState
            icon={RadioTower}
            title="Nobody is signed in"
            description="Sessions appear here as people sign in. The list lives in the server's memory, so a restart clears it."
          />
        }
      />

      <Text fontSize="sm" color="fg.muted" mt="4">
        Sessions are held in memory on this instance and are cleared by a restart.
      </Text>

      <ConfirmDialog
        open={!!revoking}
        title="End session"
        message={
          revoking
            ? `End ${revoking.username}'s session? They stay signed in until their next request, which will be refused and return them to the sign-in page. Their account is not changed — they can sign in again straight away.`
            : ''
        }
        confirmLabel="End session"
        pending={revokeAction.pending}
        onCancel={() => setRevoking(null)}
        onConfirm={confirmRevoke}
      />
    </Box>
  );
}
