'use client';

import { Box, HStack, Stack, Text } from '@chakra-ui/react';
import NextLink from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState, type FormEvent } from 'react';

import ErrorBanner from '@/components/ErrorBanner';
import FormField from '@/components/FormField';
import PageHeader from '@/components/PageHeader';
import RecordCard from '@/components/RecordCard';
import Button from '@/components/ui/Button';
import SurfaceCard from '@/components/ui/SurfaceCard';
import Key from '@/components/ui/Key';
import { confirmDone } from '@/components/ui/toaster';
import RecordSkeleton from '@/components/ui/RecordSkeleton';
import StudentPicker from '@/components/StudentPicker';
import StatusDot from '@/components/ui/StatusDot';
import DetailLayout from '@/components/DetailLayout';
import Breadcrumb from '@/components/Breadcrumb';
import { books } from '@/lib/api/endpoints';
import { useAuth } from '@/lib/auth/AuthContext';
import { can } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import useResource from '@/lib/hooks/useResource';
import { remember } from '@/lib/recent';

/** One book, plus the Librarian's assign/unassign controls. */
export default function BookDetailPage() {
  return (
    <RequireAuth capability="books:read">
      <BookDetail />
    </RequireAuth>
  );
}

function BookDetail() {
  const params = useParams<{ isbn: string }>();
  const isbn = decodeURIComponent(params.isbn);
  const { session } = useAuth();
  const mayWrite = can(session?.role, 'books:write');

  const { data, loading, error, refetch } = useResource(() => books.get(isbn), [isbn]);

  useEffect(() => {
    if (!data) return;
    remember({
      kind: 'book',
      code: data.isbn,
      label: data.title,
      href: `/books/${encodeURIComponent(data.isbn)}`,
    });
  }, [data]);

  if (loading) {
    return (
      <RecordSkeleton fields={6} />
    );
  }

  return (
    <Box>
      <Breadcrumb
        parent={{ href: '/books', label: 'Books' }}
        current={data ? data.title : isbn}
      />
      <PageHeader
        title={data ? data.title : isbn}
        description={data ? data.author : undefined}
      />

      <ErrorBanner error={error} />

      {data ? (
        <DetailLayout
          record={
            <RecordCard
              title="Book"
              fields={[
                { label: 'ISBN', value: <Key>{data.isbn}</Key> },
                { label: 'Title', value: data.title },
                { label: 'Author', value: data.author },
                { label: 'Published', value: data.publishedDate ?? '—' },
                {
                  label: 'Held by',
                  value: data.owner ? (
                    // A Student reading their own book has no Students tab to land on, so only the
                    // roles that can open a profile get a link.
                    can(session?.role, 'students:read') && session?.role !== 'STUDENT' ? (
                      <NextLink href={`/students/${encodeURIComponent(data.owner.studentCode)}`}>
                        <Text as="span" textDecoration="underline">
                          {data.owner.firstName} {data.owner.lastName} ({data.owner.studentCode})
                        </Text>
                      </NextLink>
                    ) : (
                      `${data.owner.firstName} ${data.owner.lastName} (${data.owner.studentCode})`
                    )
                  ) : (
                    'On shelf'
                  ),
                },
              ]}
            />
          }
        >
          {mayWrite ? (
            <OwnershipControls
              isbn={data.isbn}
              currentOwner={data.ownerStudentCode}
              onChanged={refetch}
            />
          ) : null}
        </DetailLayout>
      ) : null}
    </Box>
  );
}

/**
 * Assign / release, both keyed on student code.
 *
 * Release is idempotent server-side — releasing an already-shelved book is a 200 with a null owner,
 * not an error — so the button stays enabled and its result is simply the current state.
 */
function OwnershipControls({
  isbn,
  currentOwner,
  onChanged,
}: {
  isbn: string;
  currentOwner: string | null;
  onChanged: () => void;
}) {
  const [studentCode, setStudentCode] = useState('');
  const assign = useAsyncAction(books.assignOwner);
  const release = useAsyncAction(books.clearOwner);

  const onAssign = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const result = await assign.run(isbn, studentCode);
    if (result) {
      confirmDone('Book assigned');
      setStudentCode('');
      onChanged();
    }
  };

  const onRelease = async () => {
    const result = await release.run(isbn);
    if (result) {
      confirmDone('Book released');
      onChanged();
    }
  };

  return (
    <SurfaceCard title="Ownership">
      <Stack gap="6">
        <ErrorBanner error={assign.error} />
        <ErrorBanner error={release.error} />

        <form onSubmit={onAssign}>
          <HStack gap="4" align="flex-start">
            <Box flex="1">
              <StudentPicker
                label={currentOwner ? 'Reassign to' : 'Assign to'}
                value={studentCode}
                onSelect={setStudentCode}
                error={assign.error?.fieldError('studentCode')}
              />
            </Box>
            <Button type="submit" loading={assign.pending} mt="6" disabled={!studentCode}>
              {currentOwner ? 'Reassign' : 'Assign'}
            </Button>
          </HStack>
        </form>

        <HStack justify="space-between" gap="4">
          <StatusDot active={!!currentOwner}>
            {currentOwner ? <>Held by <Key>{currentOwner}</Key></> : 'On the shelf'}
          </StatusDot>
          <Button
            tone="neutral"
            variant="outline"
            loading={release.pending}
            disabled={!currentOwner}
            onClick={onRelease}
          >
            Release
          </Button>
        </HStack>
      </Stack>
    </SurfaceCard>
  );
}
