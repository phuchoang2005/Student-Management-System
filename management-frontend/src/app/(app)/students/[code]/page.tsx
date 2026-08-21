'use client';

import { Box, Button, Center, Code, Heading, Spinner, Stack, Text } from '@chakra-ui/react';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';

import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import Pagination from '@/components/Pagination';
import RecordCard from '@/components/RecordCard';
import { books, enrollments, students } from '@/lib/api/endpoints';
import type { BookSummary, Enrollment } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { can } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import usePagedResource from '@/lib/hooks/usePagedResource';
import useResource from '@/lib/hooks/useResource';

/**
 * One student, plus whichever side of their record the viewing role is responsible for:
 *
 *   Librarian                → the books they are holding
 *   Registrar / Course Admin → the courses they are enrolled in
 *
 * Neither list is embedded in `GET /students/{code}`. Each is its own paged, separately authorized
 * read, so a Librarian never receives a course list and a Course Administrator never receives a
 * book list simply by opening a profile.
 *
 * The Course Administrator reaches this page only by clicking through a course roster — it has no
 * Students tab — which is why the route is guarded on `students:read` rather than on nav presence.
 */
export default function StudentDetailPage() {
  return (
    <RequireAuth capability="students:read">
      <StudentDetail />
    </RequireAuth>
  );
}

function StudentDetail() {
  const params = useParams<{ code: string }>();
  const code = decodeURIComponent(params.code);
  const router = useRouter();
  const { session } = useAuth();

  const { data, loading, error } = useResource(() => students.get(code), [code]);

  const showBooks = can(session?.role, 'books:read') && session?.role === 'LIBRARIAN';
  const showCourses = can(session?.role, 'enrollments:read');

  if (loading) {
    return (
      <Center py="16">
        <Spinner size="lg" />
      </Center>
    );
  }

  return (
    <Box>
      <PageHeader
        title={data ? `${data.firstName} ${data.lastName}` : code}
        description={data ? data.email : undefined}
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
            title="Record"
            fields={[
              { label: 'Student code', value: <Code>{data.studentCode}</Code> },
              { label: 'First name', value: data.firstName },
              { label: 'Last name', value: data.lastName },
              { label: 'Email', value: data.email },
              { label: 'Date of birth', value: data.dateOfBirth },
              { label: 'Registered', value: new Date(data.createdAt).toLocaleString() },
            ]}
            actions={
              can(session?.role, 'students:initial-password') ? (
                <InitialPasswordButton code={data.studentCode} />
              ) : undefined
            }
          />

          {showBooks ? <BorrowedBooks code={data.studentCode} /> : null}
          {showCourses ? <EnrolledCourses code={data.studentCode} /> : null}
        </Stack>
      ) : null}
    </Box>
  );
}

/** UC-23. The 404 here is deliberate information-hiding, so its message says so rather than "not found". */
function InitialPasswordButton({ code }: { code: string }) {
  const action = useAsyncAction(students.initialPassword);
  const [revealed, setRevealed] = useState<string | null>(null);

  const reveal = async () => {
    const result = await action.run(code);
    if (result) setRevealed(result.initialPassword);
  };

  if (revealed) {
    return (
      <Text fontSize="sm">
        Initial password: <Code>{revealed}</Code>
      </Text>
    );
  }

  return (
    <Stack gap="1" align="flex-end">
      <Button size="xs" variant="outline" loading={action.pending} onClick={reveal}>
        Show initial password
      </Button>
      {action.error ? (
        <Text fontSize="xs" color="fg.error" maxW="20rem" textAlign="right">
          {action.error.status === 404
            ? 'No initial password is available — this student has already chosen their own.'
            : action.error.message}
        </Text>
      ) : null}
    </Stack>
  );
}

/** The Librarian's half: `GET /books?ownerStudentCode=`, addressed by code, never by an owner id. */
function BorrowedBooks({ code }: { code: string }) {
  const router = useRouter();
  const resource = usePagedResource<BookSummary>(
    (_query, page) => books.search(undefined, page, 20, code),
    { deps: [code] },
  );

  return (
    <Box>
      <Heading size="md" mb="3">
        Books on loan
      </Heading>
      <ErrorBanner error={resource.error} />
      <DataTable<BookSummary>
        columns={[
          { key: 'isbn', header: 'ISBN', width: '13rem', cell: (row) => <Code>{row.isbn}</Code> },
          { key: 'title', header: 'Title', cell: (row) => row.title },
          { key: 'author', header: 'Author', cell: (row) => row.author },
        ]}
        rows={resource.data?.content ?? []}
        keyOf={(row) => row.isbn}
        loading={resource.loading}
        empty="This student is not holding any books."
        onRowClick={(row) => router.push(`/books/${encodeURIComponent(row.isbn)}`)}
      />
      <Pagination data={resource.data} page={resource.page} onPageChange={resource.setPage} />
    </Box>
  );
}

/** The Registrar's and Course Administrator's half: `GET /enrollments?studentCode=`. */
function EnrolledCourses({ code }: { code: string }) {
  const router = useRouter();
  const resource = usePagedResource<Enrollment>((_query, page) => enrollments.byStudent(code, page), {
    deps: [code],
  });

  return (
    <Box>
      <Heading size="md" mb="3">
        Enrolled courses
      </Heading>
      <ErrorBanner error={resource.error} />
      <DataTable<Enrollment>
        columns={[
          {
            key: 'courseCode',
            header: 'Code',
            width: '9rem',
            cell: (row) => <Code>{row.course.courseCode}</Code>,
          },
          { key: 'name', header: 'Course', cell: (row) => row.course.name },
          { key: 'credits', header: 'Credits', width: '6rem', cell: (row) => row.course.credits },
          {
            key: 'enrolledAt',
            header: 'Enrolled',
            width: '11rem',
            cell: (row) => new Date(row.enrolledAt).toLocaleDateString(),
          },
        ]}
        rows={resource.data?.content ?? []}
        keyOf={(row) => row.course.courseCode}
        loading={resource.loading}
        empty="This student is not enrolled in any course."
        onRowClick={(row) => router.push(`/courses/${encodeURIComponent(row.course.courseCode)}`)}
      />
      <Pagination data={resource.data} page={resource.page} onPageChange={resource.setPage} />
    </Box>
  );
}
