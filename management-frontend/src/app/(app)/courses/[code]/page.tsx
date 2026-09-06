'use client';

import { Box, Heading } from '@chakra-ui/react';
import { Users } from 'lucide-react';
import { useParams, useRouter } from 'next/navigation';
import { useEffect } from 'react';

import CursorPagination from '@/components/CursorPagination';
import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import RecordCard from '@/components/RecordCard';
import EmptyState from '@/components/ui/EmptyState';
import Key from '@/components/ui/Key';
import RecordSkeleton from '@/components/ui/RecordSkeleton';
import DetailLayout from '@/components/DetailLayout';
import Breadcrumb from '@/components/Breadcrumb';
import { courses, enrollments } from '@/lib/api/endpoints';
import type { Enrollment } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { can } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import useCursorResource from '@/lib/hooks/useCursorResource';
import useResource from '@/lib/hooks/useResource';
import { remember } from '@/lib/recent';

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
  const { session } = useAuth();

  const { data, loading, error } = useResource(() => courses.get(code), [code]);

  useEffect(() => {
    if (!data) return;
    remember({
      kind: 'course',
      code: data.courseCode,
      label: data.name,
      href: `/courses/${encodeURIComponent(data.courseCode)}`,
    });
  }, [data]);
  const showRoster = can(session?.role, 'enrollments:read');

  if (loading) {
    return (
      <RecordSkeleton fields={5} />
    );
  }

  return (
    <Box>
      <Breadcrumb
        parent={{ href: '/courses', label: 'Courses' }}
        current={data ? data.name : code}
      />
      <PageHeader
        title={data ? data.name : code}
        description={data ? `${data.credits} credits` : undefined}
      />

      <ErrorBanner error={error} />

      {data ? (
        <DetailLayout
          record={
            <RecordCard
              title="Course"
              fields={[
                { label: 'Course code', value: <Key>{data.courseCode}</Key> },
                { label: 'Name', value: data.name },
                { label: 'Credits', value: data.credits },
                { label: 'Students enrolled', value: data.enrolledCount },
                { label: 'Description', value: data.description || '—' },
                { label: 'Created', value: new Date(data.createdAt).toLocaleString() },
              ]}
            />
          }
        >
          {showRoster ? <Roster code={data.courseCode} /> : null}
        </DetailLayout>
      ) : null}
    </Box>
  );
}

/** Every student enrolled in this course; each row opens that student's profile. */
function Roster({ code }: { code: string }) {
  const router = useRouter();
  const resource = useCursorResource<Enrollment>(
    (_query, cursor) => enrollments.byCourse(code, cursor),
    { deps: [code] },
  );

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
            cell: (row) => <Key>{row.student.studentCode}</Key>,
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
