'use client';

import { Box, Center, Code, Heading, Spinner, Stack } from '@chakra-ui/react';
import { ArrowLeft, Users } from 'lucide-react';
import { useParams, useRouter } from 'next/navigation';

import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import Pagination from '@/components/Pagination';
import RecordCard from '@/components/RecordCard';
import Button from '@/components/ui/Button';
import EmptyState from '@/components/ui/EmptyState';
import { courses, enrollments } from '@/lib/api/endpoints';
import type { Enrollment } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { can } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import usePagedResource from '@/lib/hooks/usePagedResource';
import useResource from '@/lib/hooks/useResource';

/**
 * One course, plus its roster for the roles responsible for enrollments.
 *
 * The roster is not a field on `GET /courses/{code}`: a Student browsing a course they are taking
 * would otherwise receive the names and email addresses of everyone else taking it. It is its own
 * read (`GET /enrollments?courseCode=`), open to the Registrar and Course Administrator only.
 */
export default function CourseDetailPage() {
  return (
    <RequireAuth capability="courses:read">
      <CourseDetail />
    </RequireAuth>
  );
}

function CourseDetail() {
  const params = useParams<{ code: string }>();
  const code = decodeURIComponent(params.code);
  const router = useRouter();
  const { session } = useAuth();

  const { data, loading, error } = useResource(() => courses.get(code), [code]);
  const showRoster = can(session?.role, 'enrollments:read');

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
        title={data ? data.name : code}
        description={data ? `${data.credits} credits` : undefined}
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
            title="Course"
            fields={[
              { label: 'Course code', value: <Code>{data.courseCode}</Code> },
              { label: 'Name', value: data.name },
              { label: 'Credits', value: data.credits },
              { label: 'Description', value: data.description || '—' },
              { label: 'Created', value: new Date(data.createdAt).toLocaleString() },
            ]}
          />
          {showRoster ? <Roster code={data.courseCode} /> : null}
        </Stack>
      ) : null}
    </Box>
  );
}

/** Every student enrolled in this course; each row opens that student's profile. */
function Roster({ code }: { code: string }) {
  const router = useRouter();
  const resource = usePagedResource<Enrollment>((_query, page) => enrollments.byCourse(code, page), {
    deps: [code],
  });

  return (
    <Box>
      <Heading size="md" mb="4" fontWeight="semibold">
        Enrolled students
      </Heading>
      <ErrorBanner error={resource.error} />
      <DataTable<Enrollment>
        columns={[
          {
            key: 'studentCode',
            header: 'Code',
            width: '9rem',
            cell: (row) => <Code>{row.student.studentCode}</Code>,
          },
          {
            key: 'name',
            header: 'Student',
            cell: (row) => `${row.student.firstName} ${row.student.lastName}`,
          },
          { key: 'email', header: 'Email', cell: (row) => row.student.email },
          {
            key: 'enrolledAt',
            header: 'Enrolled',
            width: '11rem',
            cell: (row) => new Date(row.enrolledAt).toLocaleDateString(),
          },
        ]}
        rows={resource.data?.content ?? []}
        keyOf={(row) => row.student.studentCode}
        loading={resource.loading}
        empty={
          <EmptyState
            icon={Users}
            title="No students enrolled"
            description="Nobody is taking this course yet. Enrollments are made from the Enrollments tab."
          />
        }
        onRowClick={(row) => router.push(`/students/${encodeURIComponent(row.student.studentCode)}`)}
      />
      <Pagination data={resource.data} page={resource.page} onPageChange={resource.setPage} />
    </Box>
  );
}
