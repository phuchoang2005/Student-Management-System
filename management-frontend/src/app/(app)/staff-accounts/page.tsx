'use client';

import {
  Alert,
  Badge,
  Box,
  Button,
  Card,
  Code,
  HStack,
  Input,
  NativeSelect,
  Stack,
  Text,
} from '@chakra-ui/react';
import { useState, type FormEvent } from 'react';

import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import Pagination from '@/components/Pagination';
import { staffAccounts } from '@/lib/api/endpoints';
import type { Role, StaffAccountCreated, StaffAccountSummary } from '@/lib/api/types';
import { ROLE_COLORS, ROLE_LABELS } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import usePagedResource from '@/lib/hooks/usePagedResource';

const STAFF_ROLES: Role[] = ['REGISTRAR', 'LIBRARIAN', 'COURSE_ADMINISTRATOR'];

/**
 * The System Administrator's entire surface: create a staff account, and enable or disable one.
 *
 * The `id` here is the one surrogate the API still exposes, deliberately: `PATCH
 * /staff-accounts/{id}/status` addresses an `identity` record that has no business key of its own
 * — a username can be renamed, an id cannot — so it stays. It is never typed by hand; it comes from
 * the listing.
 */
export default function StaffAccountsPage() {
  return (
    <RequireAuth capability="staff:manage">
      <StaffAccounts />
    </RequireAuth>
  );
}

function StaffAccounts() {
  const resource = usePagedResource<StaffAccountSummary>((_query, page) =>
    staffAccounts.list(page),
  );

  const [username, setUsername] = useState('');
  const [role, setRole] = useState<Role>('REGISTRAR');
  const [created, setCreated] = useState<StaffAccountCreated | null>(null);

  const createAction = useAsyncAction(staffAccounts.create);
  const statusAction = useAsyncAction(staffAccounts.setStatus);

  const onCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const result = await createAction.run(username.trim(), role);
    if (result) {
      setCreated(result);
      setUsername('');
      resource.refetch();
    }
  };

  const toggle = async (account: StaffAccountSummary) => {
    await statusAction.run(account.id, !account.enabled);
    if (!statusAction.error) resource.refetch();
  };

  return (
    <Box>
      <PageHeader
        title="Staff accounts"
        description="Create Registrar, Librarian, and Course Administrator logins, and deactivate them when they leave."
      />

      <ErrorBanner error={resource.error} />
      <ErrorBanner error={statusAction.error} />

      <form onSubmit={onCreate}>
          <Card.Root mb="6">
          <Card.Header>
            <Card.Title>New staff account</Card.Title>
          </Card.Header>
          <Card.Body>
            <Stack gap="3">
              <ErrorBanner error={createAction.error} />
              <HStack gap="2" align="flex-end" wrap="wrap">
                <Box flex="1" minW="14rem">
                  <Text fontSize="sm" mb="1">
                    Username
                  </Text>
                  <Input
                    size="sm"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="e.g. j.smith"
                    aria-label="Username"
                    required
                  />
                </Box>
                <Box minW="12rem">
                  <Text fontSize="sm" mb="1">
                    Role
                  </Text>
                  <NativeSelect.Root size="sm">
                    <NativeSelect.Field
                      value={role}
                      onChange={(e) => setRole(e.currentTarget.value as Role)}
                      aria-label="Role"
                    >
                      {STAFF_ROLES.map((value) => (
                        <option key={value} value={value}>
                          {ROLE_LABELS[value]}
                        </option>
                      ))}
                    </NativeSelect.Field>
                    <NativeSelect.Indicator />
                  </NativeSelect.Root>
                </Box>
                <Button size="sm" type="submit" loading={createAction.pending}>
                  Create
                </Button>
              </HStack>
            </Stack>
          </Card.Body>
        </Card.Root>
      </form>

      {created ? (
        <Alert.Root status="success" mb="6">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Account created — password shown once</Alert.Title>
            <Alert.Description>
              <Text>
                Username: <Code>{created.username}</Code>
              </Text>
              <Text>
                Initial password: <Code>{created.initialPassword}</Code>
              </Text>
              <Text mt="2" fontSize="sm">
                Unlike a student&rsquo;s, a staff initial password has no lookup path afterwards.
                Pass it on now.
              </Text>
            </Alert.Description>
          </Alert.Content>
        </Alert.Root>
      ) : null}

      <DataTable<StaffAccountSummary>
        columns={[
          { key: 'username', header: 'Username', cell: (row) => row.username },
          {
            key: 'role',
            header: 'Role',
            width: '11rem',
            cell: (row) => (
              <Badge colorPalette={ROLE_COLORS[row.role]} variant="subtle">
                {ROLE_LABELS[row.role]}
              </Badge>
            ),
          },
          {
            key: 'enabled',
            header: 'Status',
            width: '8rem',
            cell: (row) => (
              <Badge colorPalette={row.enabled ? 'green' : 'gray'} variant="subtle">
                {row.enabled ? 'Active' : 'Disabled'}
              </Badge>
            ),
          },
          {
            key: 'actions',
            header: '',
            width: '9rem',
            align: 'end' as const,
            cell: (row) => (
              <Button
                size="xs"
                variant="outline"
                colorPalette={row.enabled ? 'red' : undefined}
                onClick={() => toggle(row)}
              >
                {row.enabled ? 'Deactivate' : 'Reactivate'}
              </Button>
            ),
          },
        ]}
        rows={resource.data?.content ?? []}
        keyOf={(row) => String(row.id)}
        loading={resource.loading}
        empty="No staff accounts yet."
      />

      <Pagination data={resource.data} page={resource.page} onPageChange={resource.setPage} />
    </Box>
  );
}
