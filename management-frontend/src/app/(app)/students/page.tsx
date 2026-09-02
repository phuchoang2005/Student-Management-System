'use client';

import { Alert, Box, Center, Code, Spinner, Stack } from '@chakra-ui/react';
import { UserPlus, Users } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

import ConfirmDialog from '@/components/ConfirmDialog';
import FadeIn from '@/components/motion/FadeIn';
import Button from '@/components/ui/Button';
import EmptyState from '@/components/ui/EmptyState';
import CursorPagination from '@/components/CursorPagination';
import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import RecordCard from '@/components/RecordCard';
import SearchInput from '@/components/SearchInput';
import StudentFormDialog from '@/components/StudentFormDialog';
import { me, students } from '@/lib/api/endpoints';
import type { StudentSummary } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { can } from '@/lib/auth/permissions';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import useCursorResource from '@/lib/hooks/useCursorResource';
import useResource from '@/lib/hooks/useResource';

/**
 * One route, two shapes.
 *
 * A **Student** sees their own record rendered directly — no list, no search box. There is nothing
 * else for them here: the server would scope a search to a single row anyway, and making them
 * search for themselves was the thing this page was fixing.
 *
 * A **Registrar** or **Librarian** sees the roll: search, table, pagination, and (Registrar only)
 * the write actions.
 */
export default function StudentsPage() {
  const { session } = useAuth();
  if (session?.role === 'STUDENT') return <MyRecord />;
  return <StudentRoll />;
}

/** The Student's own record, from `/me/profile` — the only endpoint that tells them their own code. */
function MyRecord() {
  const { data, loading, error } = useResource(() => me.profile());

  if (loading) {
    return (
      <Center py="16">
        <Spinner size="lg" color="fg.subtle" borderWidth="1.5px" />
      </Center>
    );
  }

  return (
    <Box maxW="42rem">
      <PageHeader title="My details" description="The record the registrar holds for you." />
      <ErrorBanner error={error} />
      {data ? (
        <RecordCard
          fields={[
            { label: 'Student code', value: <Code>{data.studentCode}</Code> },
            { label: 'First name', value: data.firstName },
            { label: 'Last name', value: data.lastName },
            { label: 'Email', value: data.email },
            { label: 'Date of birth', value: data.dateOfBirth },
          ]}
        />
      ) : null}
      <Alert.Root status="info" mt="6" size="sm">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Description>
            To correct anything here, ask the registrar — your details are theirs to maintain.
          </Alert.Description>
        </Alert.Content>
      </Alert.Root>
    </Box>
  );
}

/** The staff view: search + table + pagination, with write actions for the Registrar. */
function StudentRoll() {
  const { session } = useAuth();
  const router = useRouter();
  const mayWrite = can(session?.role, 'students:write');

  const resource = useCursorResource<StudentSummary>((query, cursor) =>
    students.search(query || undefined, cursor),
  );

  const [editing, setEditing] = useState<StudentSummary | null>(null);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<StudentSummary | null>(null);

  const removeAction = useAsyncAction(students.remove);

  const confirmDelete = async () => {
    if (!deleting) return;
    // `run` resolves to `undefined` only when it caught an error, and to the call's result
    // otherwise -- `null` here, since DELETE answers 204. That return value is the whole success
    // check: `removeAction.error` is the state captured by this render, so reading it back here
    // would report the *previous* attempt's outcome.
    const result = await removeAction.run(deleting.studentCode);
    if (result !== undefined) {
      setDeleting(null);
      resource.refetch();
    }
  };

  return (
    <Box>
      <PageHeader
        title="Students"
        description="Search by code, name, or email. Select a student to see their record."
        actions={
          mayWrite ? (
            <Button onClick={() => setCreating(true)}>
              <UserPlus strokeWidth={1.5} />
              Register student
            </Button>
          ) : undefined
        }
      />

      <ErrorBanner error={resource.error} />
      <ErrorBanner error={removeAction.error} />

      <SearchInput
        value={resource.query}
        onChange={resource.setQuery}
        placeholder="Search students…"
      />

      <DataTable<StudentSummary>
        columns={[
          {
            key: 'studentCode',
            header: 'Code',
            width: '9rem',
            cell: (row) => <Code>{row.studentCode}</Code>,
          },
          { key: 'firstName', header: 'First name', cell: (row) => row.firstName },
          { key: 'lastName', header: 'Last name', cell: (row) => row.lastName },
          { key: 'email', header: 'Email', cell: (row) => row.email },
          ...(mayWrite
            ? [
                {
                  key: 'actions',
                  header: '',
                  width: '10rem',
                  align: 'end' as const,
                  cell: (row: StudentSummary) => (
                    <Stack direction="row" gap="2" justify="flex-end">
                      <Button
                        size="sm"
                        tone="neutral"
                        variant="outline"
                        onClick={(event) => {
                          event.stopPropagation();
                          setEditing(row);
                        }}
                      >
                        Edit
                      </Button>
                      <Button
                        size="sm"
                        tone="danger"
                        variant="outline"
                        onClick={(event) => {
                          event.stopPropagation();
                          setDeleting(row);
                        }}
                      >
                        Delete
                      </Button>
                    </Stack>
                  ),
                },
              ]
            : []),
        ]}
        rows={resource.data?.content ?? []}
        keyOf={(row) => row.studentCode}
        loading={resource.loading}
        empty={
          <EmptyState
            icon={Users}
            title={resource.query ? 'No students match that search' : 'No students yet'}
            description={
              resource.query
                ? 'Try a different code, name, or email — search matches all three.'
                : 'Register your first student to begin managing academic records.'
            }
            action={
              mayWrite && !resource.query ? (
                <Button onClick={() => setCreating(true)}>
                  <UserPlus strokeWidth={1.5} />
                  Register student
                </Button>
              ) : undefined
            }
          />
        }
        onRowClick={(row) => router.push(`/students/${encodeURIComponent(row.studentCode)}`)}
      />

      <CursorPagination
        data={resource.data}
        canGoPrev={resource.canGoPrev}
        canGoNext={resource.canGoNext}
        onPrev={resource.goPrev}
        onNext={resource.goNext}
      />

      <StudentFormDialog
        open={creating}
        onClose={() => setCreating(false)}
        onSaved={() => {
          setCreating(false);
          resource.refetch();
        }}
      />
      <StudentFormDialog
        open={!!editing}
        student={editing ?? undefined}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null);
          resource.refetch();
        }}
      />
      <ConfirmDialog
        open={!!deleting}
        title="Remove student"
        message={
          deleting
            ? `Remove ${deleting.firstName} ${deleting.lastName} (${deleting.studentCode})? Their login account is deleted, their enrollments end, and any books they hold are released.`
            : ''
        }
        pending={removeAction.pending}
        onCancel={() => setDeleting(null)}
        onConfirm={confirmDelete}
      />
    </Box>
  );
}
