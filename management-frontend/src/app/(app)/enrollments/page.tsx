'use client';

import { Box, Code, HStack, Heading, Stack } from '@chakra-ui/react';
import { ClipboardList, GraduationCap, Plus, Search, Users } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';

import ConfirmDialog from '@/components/ConfirmDialog';
import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import FormField from '@/components/FormField';
import PageHeader from '@/components/PageHeader';
import Pagination from '@/components/Pagination';
import SearchInput from '@/components/SearchInput';
import Button from '@/components/ui/Button';
import EmptyState from '@/components/ui/EmptyState';
import SurfaceCard from '@/components/ui/SurfaceCard';
import { courses, enrollments } from '@/lib/api/endpoints';
import type { CourseSummary, Enrollment } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { can } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import usePagedResource from '@/lib/hooks/usePagedResource';

/**
 * Two very different jobs share this route, because they are the two ways an enrollment gets looked
 * at:
 *
 *   **Registrar** works student-first — type a student code, see what that student is taking, and
 *   enroll or end from there. They never see or type a numeric id; the code is the whole handle.
 *
 *   **Course Administrator** works course-first — pick a course, see who is in it, click through to
 *   a student's profile. That is their only route into a student record: the role has no Students
 *   tab, by design.
 *
 * A Student is not here at all. Their enrolled courses are on their own Courses tab, served by
 * `/me/courses` and scoped by the session rather than by anything they type.
 */
export default function EnrollmentsPage() {
  return (
    <RequireAuth capability="enrollments:read">
      <Enrollments />
    </RequireAuth>
  );
}

function Enrollments() {
  const { session } = useAuth();
  return can(session?.role, 'enrollments:write') ? <RegistrarView /> : <CourseAdminView />;
}

// ------------------------------------------------------------------------------------------
// Registrar: look a student up by code, then manage their enrollments
// ------------------------------------------------------------------------------------------

function RegistrarView() {
  const router = useRouter();
  const [input, setInput] = useState('');
  /** The code actually being shown — only set on submit, so typing doesn't fire a request per key. */
  const [studentCode, setStudentCode] = useState('');
  const [enrolling, setEnrolling] = useState(false);
  const [ending, setEnding] = useState<Enrollment | null>(null);

  const resource = usePagedResource<Enrollment>(
    (_query, page) => enrollments.byStudent(studentCode, page),
    { enabled: !!studentCode, deps: [studentCode] },
  );

  const endAction = useAsyncAction(enrollments.remove);

  const onLookup = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setStudentCode(input.trim());
  };

  const confirmEnd = async () => {
    if (!ending) return;
    await endAction.run(studentCode, ending.course.courseCode);
    if (!endAction.error) {
      setEnding(null);
      resource.refetch();
    }
  };

  return (
    <Box>
      <PageHeader
        title="Enrollments"
        description="Look a student up by their student code to see and manage the courses they are taking."
        actions={
          studentCode ? (
            <Button onClick={() => setEnrolling(true)}>
              <Plus strokeWidth={1.5} />
              Enroll in a course
            </Button>
          ) : undefined
        }
      />

      <form onSubmit={onLookup}>
        <SurfaceCard mb="8">
          <HStack gap="4" align="flex-end">
            <Box flex="1" maxW="20rem">
              <FormField
                label="Student code"
                name="studentCode"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder="e.g. S00123"
                required
              />
            </Box>
            <Button type="submit">
              <Search strokeWidth={1.5} />
              Look up
            </Button>
          </HStack>
        </SurfaceCard>
      </form>

      <ErrorBanner error={resource.error} />
      <ErrorBanner error={endAction.error} />

      {!studentCode ? (
        <EmptyState
          icon={ClipboardList}
          title="No student selected"
          description="Enter a student code above. Every course that student is enrolled in will be listed here, ready to end or add to."
        />
      ) : (
        <>
          <Heading size="md" mb="4" fontWeight="semibold">
            Courses for <Code>{studentCode}</Code>
          </Heading>
          <DataTable<Enrollment>
            columns={[
              {
                key: 'courseCode',
                header: 'Code',
                width: '9rem',
                cell: (row) => <Code>{row.course.courseCode}</Code>,
              },
              { key: 'name', header: 'Course', cell: (row) => row.course.name },
              {
                key: 'credits',
                header: 'Credits',
                width: '6rem',
                cell: (row) => row.course.credits,
              },
              {
                key: 'enrolledAt',
                header: 'Enrolled',
                width: '11rem',
                cell: (row) => new Date(row.enrolledAt).toLocaleDateString(),
              },
              {
                key: 'actions',
                header: '',
                width: '7rem',
                align: 'end' as const,
                cell: (row) => (
                  <Button
                    size="sm"
                    tone="danger"
                    variant="outline"
                    onClick={(event) => {
                      event.stopPropagation();
                      setEnding(row);
                    }}
                  >
                    End
                  </Button>
                ),
              },
            ]}
            rows={resource.data?.content ?? []}
            keyOf={(row) => row.course.courseCode}
            loading={resource.loading}
            empty={
              <EmptyState
                icon={GraduationCap}
                title="Not enrolled in anything"
                description={`${studentCode} is not taking any course yet. Use "Enroll in a course" above to add one.`}
              />
            }
            onRowClick={(row) =>
              router.push(`/courses/${encodeURIComponent(row.course.courseCode)}`)
            }
          />
          <Pagination data={resource.data} page={resource.page} onPageChange={resource.setPage} />
        </>
      )}

      <EnrollDialog
        open={enrolling}
        studentCode={studentCode}
        onClose={() => setEnrolling(false)}
        onSaved={() => {
          setEnrolling(false);
          resource.refetch();
        }}
      />
      <ConfirmDialog
        open={!!ending}
        title="End enrollment"
        message={
          ending
            ? `End ${studentCode}'s enrollment in ${ending.course.courseCode}? Only the enrollment is removed — the student and the course are untouched.`
            : ''
        }
        confirmLabel="End enrollment"
        pending={endAction.pending}
        onCancel={() => setEnding(null)}
        onConfirm={confirmEnd}
      />
    </Box>
  );
}

/** Enroll the student already on screen; only the course code is left to supply. */
function EnrollDialog({
  open,
  studentCode,
  onClose,
  onSaved,
}: {
  open: boolean;
  studentCode: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [courseCode, setCourseCode] = useState('');
  const action = useAsyncAction(enrollments.create);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const result = await action.run(studentCode, courseCode.trim());
    if (result) {
      setCourseCode('');
      onSaved();
    }
  };

  // FormDialog would work here too, but this form is a single field and reads better inline.
  if (!open) return null;

  return (
    <form onSubmit={onSubmit}>
      <SurfaceCard
        mt="8"
        title={
          <>
            Enroll <Code>{studentCode}</Code>
          </>
        }
      >
        <Stack gap="6">
          <ErrorBanner error={action.error} />
          <HStack gap="4" align="flex-end" wrap="wrap">
            <Box flex="1" maxW="20rem">
              <FormField
                label="Course code"
                name="courseCode"
                value={courseCode}
                onChange={(e) => setCourseCode(e.target.value)}
                placeholder="e.g. CS101"
                error={action.error?.fieldError('courseCode')}
                required
              />
            </Box>
            <Button type="submit" loading={action.pending}>
              Enroll
            </Button>
            <Button tone="neutral" variant="outline" type="button" onClick={onClose}>
              Cancel
            </Button>
          </HStack>
        </Stack>
      </SurfaceCard>
    </form>
  );
}

// ------------------------------------------------------------------------------------------
// Course Administrator: pick a course, see its roster, click through to a student
// ------------------------------------------------------------------------------------------

function CourseAdminView() {
  const router = useRouter();
  const [selected, setSelected] = useState<CourseSummary | null>(null);

  const courseList = usePagedResource<CourseSummary>((query, page) =>
    courses.search(query || undefined, page),
  );

  const roster = usePagedResource<Enrollment>(
    (_query, page) => enrollments.byCourse(selected!.courseCode, page),
    { enabled: !!selected, deps: [selected?.courseCode] },
  );

  return (
    <Box>
      <PageHeader
        title="Enrollments"
        description="Every current course. Select one to see who is enrolled, and a student to open their profile."
      />

      <ErrorBanner error={courseList.error} />

      <SearchInput
        value={courseList.query}
        onChange={courseList.setQuery}
        placeholder="Search courses…"
      />

      <DataTable<CourseSummary>
        columns={[
          {
            key: 'courseCode',
            header: 'Code',
            width: '9rem',
            cell: (row) => <Code>{row.courseCode}</Code>,
          },
          { key: 'name', header: 'Course', cell: (row) => row.name },
          { key: 'credits', header: 'Credits', width: '6rem', cell: (row) => row.credits },
        ]}
        rows={courseList.data?.content ?? []}
        keyOf={(row) => row.courseCode}
        loading={courseList.loading}
        empty={
          <EmptyState
            icon={GraduationCap}
            title={courseList.query ? 'No courses match that search' : 'No courses yet'}
            description={
              courseList.query
                ? 'Try a different course code or name.'
                : 'Courses are created on the Courses tab; enrollments follow from there.'
            }
          />
        }
        onRowClick={(row) => setSelected(row)}
      />
      <Pagination data={courseList.data} page={courseList.page} onPageChange={courseList.setPage} />

      {selected ? (
        <Box mt="8">
          <HStack justify="space-between" mb="4" gap="4">
            <Heading size="md" fontWeight="semibold">
              Enrolled in <Code>{selected.courseCode}</Code> — {selected.name}
            </Heading>
            <Button size="sm" tone="neutral" variant="outline" onClick={() => setSelected(null)}>
              Clear
            </Button>
          </HStack>
          <ErrorBanner error={roster.error} />
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
            rows={roster.data?.content ?? []}
            keyOf={(row) => row.student.studentCode}
            loading={roster.loading}
            empty={
              <EmptyState
                icon={Users}
                title="No students enrolled"
                description="Nobody is taking this course yet. The Registrar enrolls students from their side of this tab."
              />
            }
            onRowClick={(row) =>
              router.push(`/students/${encodeURIComponent(row.student.studentCode)}`)
            }
          />
          <Pagination data={roster.data} page={roster.page} onPageChange={roster.setPage} />
        </Box>
      ) : null}
    </Box>
  );
}
