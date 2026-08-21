'use client';

import { Box, Button, Code, Stack, Text } from '@chakra-ui/react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

import BookFormDialog from '@/components/BookFormDialog';
import ConfirmDialog from '@/components/ConfirmDialog';
import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import Pagination from '@/components/Pagination';
import SearchInput from '@/components/SearchInput';
import { books, me } from '@/lib/api/endpoints';
import type { BookSummary } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { can } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import usePagedResource from '@/lib/hooks/usePagedResource';

/**
 * The catalogue for the Librarian; "my books" for a Student.
 *
 * The Registrar and Course Administrator are not here at all — neither the nav item nor the API
 * grant exists for them any more, because neither role does anything with book ownership.
 */
export default function BooksPage() {
  return (
    <RequireAuth capability="books:read">
      <Books />
    </RequireAuth>
  );
}

function Books() {
  const { session } = useAuth();
  const router = useRouter();
  const isStudent = session?.role === 'STUDENT';
  const mayWrite = can(session?.role, 'books:write');

  const resource = usePagedResource<BookSummary>((query, page) =>
    isStudent
      ? me.books(page).then((page$) => ({
          ...page$,
          // /me/books omits the owner -- every row is the caller's -- so the column is filled in
          // here rather than sent on every row.
          content: page$.content.map((book) => ({ ...book, ownerStudentCode: null })),
        }))
      : books.search(query || undefined, page),
  );

  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<BookSummary | null>(null);
  const removeAction = useAsyncAction(books.remove);

  const confirmDelete = async () => {
    if (!deleting) return;
    await removeAction.run(deleting.isbn);
    if (!removeAction.error) {
      setDeleting(null);
      resource.refetch();
    }
  };

  return (
    <Box>
      <PageHeader
        title={isStudent ? 'My books' : 'Books'}
        description={
          isStudent
            ? 'The books currently assigned to you.'
            : 'Search by ISBN, title, or author. Select a book to manage its ownership.'
        }
        actions={
          mayWrite ? (
            <Button size="sm" onClick={() => setCreating(true)}>
              Add book
            </Button>
          ) : undefined
        }
      />

      <ErrorBanner error={resource.error} />
      <ErrorBanner error={removeAction.error} />

      {!isStudent ? (
        <SearchInput value={resource.query} onChange={resource.setQuery} placeholder="Search books…" />
      ) : null}

      <DataTable<BookSummary>
        columns={[
          { key: 'isbn', header: 'ISBN', width: '13rem', cell: (row) => <Code>{row.isbn}</Code> },
          { key: 'title', header: 'Title', cell: (row) => row.title },
          { key: 'author', header: 'Author', cell: (row) => row.author },
          ...(isStudent
            ? []
            : [
                {
                  key: 'owner',
                  header: 'Held by',
                  width: '9rem',
                  cell: (row: BookSummary) =>
                    row.ownerStudentCode ? (
                      <Code>{row.ownerStudentCode}</Code>
                    ) : (
                      <Text fontSize="sm" color="fg.muted">
                        On shelf
                      </Text>
                    ),
                },
              ]),
          ...(mayWrite
            ? [
                {
                  key: 'actions',
                  header: '',
                  width: '6rem',
                  align: 'end' as const,
                  cell: (row: BookSummary) => (
                    <Stack direction="row" gap="1" justify="flex-end">
                      <Button
                        size="xs"
                        variant="outline"
                        colorPalette="red"
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
        keyOf={(row) => row.isbn}
        loading={resource.loading}
        empty={isStudent ? 'You are not holding any books.' : 'No books match that search.'}
        onRowClick={(row) => router.push(`/books/${encodeURIComponent(row.isbn)}`)}
      />

      <Pagination data={resource.data} page={resource.page} onPageChange={resource.setPage} />

      <BookFormDialog
        open={creating}
        onClose={() => setCreating(false)}
        onSaved={() => {
          setCreating(false);
          resource.refetch();
        }}
      />
      <ConfirmDialog
        open={!!deleting}
        title="Remove book"
        message={
          deleting
            ? `Remove ${deleting.title} (${deleting.isbn})? If a student is holding it, their record is left untouched.`
            : ''
        }
        pending={removeAction.pending}
        onCancel={() => setDeleting(null)}
        onConfirm={confirmDelete}
      />
    </Box>
  );
}
