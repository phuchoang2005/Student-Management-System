'use client';

import {
  Alert,
  Box,
  Button,
  Card,
  Code,
  HStack,
  Heading,
  Input,
  Stack,
  Text,
} from '@chakra-ui/react';
import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';

import ConfirmDialog from '@/components/ConfirmDialog';
import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import Pagination from '@/components/Pagination';
import SearchInput from '@/components/SearchInput';
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
            <Button size="sm" onClick={() => setEnrolling(true)}>
              Enroll in a course
            </Button>
          ) : undefined
        }
      />

      <form onSubmit={onLookup}>
          <Card.Root mb="6">
          <Card.Body>
            <HStack gap="2" align="flex-end">
              <Box flex="1" maxW="20rem">
                <Text fontSize="sm" mb="1">
                  Student code
                </Text>
                <Input
                  size="sm"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  placeholder="e.g. S00123"
                  aria-label="Student code"
                  required
                />
              </Box>
              <Button size="sm" type="submit">
                Look up
              </Button>
            </HStack>
          </Card.Body>
        </Card.Root>
      </form>

      <ErrorBanner error={resource.error} />
      <ErrorBanner error={endAction.error} />

      {!studentCode ? (
        <Alert.Root status="info">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Description>
              Enter a student code above. Every course that student is enrolled in will be listed.
            </Alert.Description>
          </Alert.Content>
        </Alert.Root>
      ) : (
        <>
          <Heading size="md" mb="3">
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
                    size="xs"
                    variant="outline"
                    colorPalette="red"
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
            empty="This student is not enrolled in any course."
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
        <Card.Root mt="6">
        <Card.Header>
          <Card.Title>
            Enroll <Code>{studentCode}</Code>
          </Card.Title>
        </Card.Header>
        <Card.Body>
          <Stack gap="3">
            <ErrorBanner error={action.error} />
            <HStack gap="2" align="flex-end">
              <Box flex="1" maxW="20rem">
                <Text fontSize="sm" mb="1">
                  Course code
                </Text>
                <Input
                  size="sm"
                  value={courseCode}
                  onChange={(e) => setCourseCode(e.target.value)}
                  placeholder="e.g. CS101"
                  aria-label="Course code"
                  required
                />
              </Box>
              <Button size="sm" type="submit" loading={action.pending}>
                Enroll
              </Button>
              <Button size="sm" variant="outline" type="button" onClick={onClose}>
                Cancel
              </Button>
            </HStack>
          </Stack>
        </Card.Body>
      </Card.Root>
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
        empty="No courses match that search."
        onRowClick={(row) => setSelected(row)}
      />
      <Pagination data={courseList.data} page={courseList.page} onPageChange={courseList.setPage} />

      {selected ? (
        <Box mt="8">
          <HStack justify="space-between" mb="3">
            <Heading size="md">
              Enrolled in <Code>{selected.courseCode}</Code> — {selected.name}
            </Heading>
            <Button size="xs" variant="outline" onClick={() => setSelected(null)}>
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
            empty="No students are enrolled in this course."
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
