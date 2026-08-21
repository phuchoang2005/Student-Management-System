'use client';

import {
  Box,
  Button,
  Card,
  Center,
  Code,
  HStack,
  Input,
  Spinner,
  Stack,
  Text,
} from '@chakra-ui/react';
import NextLink from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';

import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import RecordCard from '@/components/RecordCard';
import { books } from '@/lib/api/endpoints';
import { useAuth } from '@/lib/auth/AuthContext';
import { can } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import useResource from '@/lib/hooks/useResource';

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
  const router = useRouter();
  const { session } = useAuth();
  const mayWrite = can(session?.role, 'books:write');

  const { data, loading, error, refetch } = useResource(() => books.get(isbn), [isbn]);

  if (loading) {
    return (
      <Center py="16">
        <Spinner size="lg" />
      </Center>
    );
  }

  return (
    <Box maxW="46rem">
      <PageHeader
        title={data ? data.title : isbn}
        description={data ? data.author : undefined}
        actions={
          <Button size="sm" variant="outline" onClick={() => router.back()}>
            Back
          </Button>
        }
      />

      <ErrorBanner error={error} />

      {data ? (
        <Stack gap="6">
          <RecordCard
            title="Book"
            fields={[
              { label: 'ISBN', value: <Code>{data.isbn}</Code> },
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

          {mayWrite ? (
            <OwnershipControls
              isbn={data.isbn}
              currentOwner={data.ownerStudentCode}
              onChanged={refetch}
            />
          ) : null}
        </Stack>
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
      setStudentCode('');
      onChanged();
    }
  };

  const onRelease = async () => {
    const result = await release.run(isbn);
    if (result) onChanged();
  };

  return (
    <Card.Root>
      <Card.Header>
        <Card.Title>Ownership</Card.Title>
      </Card.Header>
      <Card.Body>
        <Stack gap="4">
          <ErrorBanner error={assign.error} />
          <ErrorBanner error={release.error} />

          <form onSubmit={onAssign}>
            <HStack gap="2" align="flex-end">
              <Box flex="1">
                <Text fontSize="sm" mb="1">
                  {currentOwner ? 'Reassign to student code' : 'Assign to student code'}
                </Text>
                <Input
                  size="sm"
                  value={studentCode}
                  onChange={(e) => setStudentCode(e.target.value)}
                  placeholder="e.g. S00123"
                  aria-label="Student code"
                  required
                />
              </Box>
              <Button size="sm" type="submit" loading={assign.pending}>
                {currentOwner ? 'Reassign' : 'Assign'}
              </Button>
            </HStack>
          </form>

          <HStack justify="space-between">
            <Text fontSize="sm" color="fg.muted">
              {currentOwner
                ? `Currently held by ${currentOwner}.`
                : 'This book is on the shelf.'}
            </Text>
            <Button
              size="sm"
              variant="outline"
              loading={release.pending}
              disabled={!currentOwner}
              onClick={onRelease}
            >
              Release
            </Button>
          </HStack>
        </Stack>
      </Card.Body>
    </Card.Root>
  );
}
