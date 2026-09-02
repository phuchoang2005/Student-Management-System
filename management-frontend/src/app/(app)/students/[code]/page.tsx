'use client';

import { Box, Center, Code, Heading, Spinner, Stack, Text } from '@chakra-ui/react';
import { ArrowLeft, BookOpen, GraduationCap } from 'lucide-react';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';

import CursorPagination from '@/components/CursorPagination';
import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import RecordCard from '@/components/RecordCard';
import Button from '@/components/ui/Button';
import EmptyState from '@/components/ui/EmptyState';
import { books, enrollments, students } from '@/lib/api/endpoints';
import type { BookSummary, Enrollment } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { can } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import useCursorResource from '@/lib/hooks/useCursorResource';
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
        <Spinner size="lg" color="fg.subtle" borderWidth="1.5px" />
      </Center>
    );
  }

  return (
    <Box>
      <PageHeader
        title={data ? `${data.firstName} ${data.lastName}` : code}
        description={data ? data.email : undefined}
        actions={
          <Button tone="neutral" variant="outline" onClick={() => router.back()}>
            <ArrowLeft strokeWidth={1.5} />
            Back
          </Button>
        }
      />

      <ErrorBanner error={error} />

      {data ? (
        <Stack gap="8">
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
    <Stack gap="2" align="flex-end">
      <Button size="sm" tone="neutral" variant="outline" loading={action.pending} onClick={reveal}>
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
  const resource = useCursorResource<BookSummary>(
    (_query, cursor) => books.search(undefined, cursor, 20, code),
    { deps: [code] },
  );

  return (
    <Box>
      <Heading size="md" mb="4" fontWeight="semibold">
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
        empty={
          <EmptyState
            icon={BookOpen}
            title="No books on loan"
            description="This student is not holding anything from the library right now."
          />
        }
        onRowClick={(row) => router.push(`/books/${encodeURIComponent(row.isbn)}`)}
      />
      <CursorPagination
        data={resource.data}
        canGoPrev={resource.canGoPrev}
        canGoNext={resource.canGoNext}
        onPrev={resource.goPrev}
        onNext={resource.goNext}
      />
    </Box>
  );
}

/** The Registrar's and Course Administrator's half: `GET /enrollments?studentCode=`. */
function EnrolledCourses({ code }: { code: string }) {
  const router = useRouter();
  const resource = useCursorResource<Enrollment>(
    (_query, cursor) => enrollments.byStudent(code, cursor),
    { deps: [code] },
  );

  return (
    <Box>
      <Heading size="md" mb="4" fontWeight="semibold">
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
        empty={
          <EmptyState
            icon={GraduationCap}
            title="No enrolled courses"
            description="This student has not been enrolled in any course yet."
          />
        }
        onRowClick={(row) => router.push(`/courses/${encodeURIComponent(row.course.courseCode)}`)}
      />
      <CursorPagination
        data={resource.data}
        canGoPrev={resource.canGoPrev}
        canGoNext={resource.canGoNext}
        onPrev={resource.goPrev}
        onNext={resource.goNext}
      />
    </Box>
  );
}
