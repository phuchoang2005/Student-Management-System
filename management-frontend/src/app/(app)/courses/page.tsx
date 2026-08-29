'use client';

import { Box, Code, Stack } from '@chakra-ui/react';
import { GraduationCap, Plus } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

import ConfirmDialog from '@/components/ConfirmDialog';
import CourseFormDialog from '@/components/CourseFormDialog';
import CursorPagination from '@/components/CursorPagination';
import DataTable from '@/components/DataTable';
import ErrorBanner from '@/components/ErrorBanner';
import PageHeader from '@/components/PageHeader';
import SearchInput from '@/components/SearchInput';
import Button from '@/components/ui/Button';
import EmptyState from '@/components/ui/EmptyState';
import { courses, me } from '@/lib/api/endpoints';
import type { CourseSummary } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { can } from '@/lib/auth/permissions';
import RequireAuth from '@/lib/auth/RequireAuth';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import useCursorResource from '@/lib/hooks/useCursorResource';

/**
 * One route, two shapes again.
 *
 * A **Student** sees only the courses they are enrolled in, from `/me/courses` — the tab answers
 * "what am I taking", not "what does the school offer" — and each row opens that course's detail.
 *
 * **Registrar** and **Course Administrator** see the catalogue, with write actions for the latter.
 */
export default function CoursesPage() {
  return (
    <RequireAuth capability="courses:read">
      <Courses />
    </RequireAuth>
  );
}

function Courses() {
  const { session } = useAuth();
  const router = useRouter();
  const isStudent = session?.role === 'STUDENT';
  const mayWrite = can(session?.role, 'courses:write');

  const resource = useCursorResource<CourseSummary>((query, cursor) =>
    isStudent ? me.courses(cursor) : courses.search(query || undefined, cursor),
  );

  const [editing, setEditing] = useState<CourseSummary | null>(null);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<CourseSummary | null>(null);

  const removeAction = useAsyncAction(courses.remove);

  const confirmDelete = async () => {
    if (!deleting) return;
    await removeAction.run(deleting.courseCode);
    if (!removeAction.error) {
      setDeleting(null);
      resource.refetch();
    }
  };

  return (
    <Box>
      <PageHeader
        title={isStudent ? 'My courses' : 'Courses'}
        description={
          isStudent
            ? 'The courses you are enrolled in. Select one to see its detail.'
            : 'Search by course code or name. Select a course to see its detail.'
        }
        actions={
          mayWrite ? (
            <Button onClick={() => setCreating(true)}>
              <Plus strokeWidth={1.5} />
              Create course
            </Button>
          ) : undefined
        }
      />

      <ErrorBanner error={resource.error} />
      <ErrorBanner error={removeAction.error} />

      {!isStudent ? (
        <SearchInput
          value={resource.query}
          onChange={resource.setQuery}
          placeholder="Search courses…"
        />
      ) : null}

      <DataTable<CourseSummary>
        columns={[
          {
            key: 'courseCode',
            header: 'Code',
            width: '9rem',
            cell: (row) => <Code>{row.courseCode}</Code>,
          },
          { key: 'name', header: 'Name', cell: (row) => row.name },
          { key: 'credits', header: 'Credits', width: '6rem', cell: (row) => row.credits },
          {
            key: 'enrolledCount',
            header: 'Students',
            width: '7rem',
            cell: (row) => row.enrolledCount,
          },
          ...(mayWrite
            ? [
                {
                  key: 'actions',
                  header: '',
                  width: '10rem',
                  align: 'end' as const,
                  cell: (row: CourseSummary) => (
                    <Stack direction="row" gap="2" justify="flex-end">
                      <Button
                        size="sm"
                        tone="neutral" variant="outline"
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
        keyOf={(row) => row.courseCode}
        loading={resource.loading}
        empty={
          <EmptyState
            icon={GraduationCap}
            title={
              isStudent
                ? 'You are not enrolled in any course yet'
                : resource.query
                  ? 'No courses match that search'
                  : 'No courses yet'
            }
            description={
              isStudent
                ? 'Once the registrar enrolls you, your courses will appear here.'
                : resource.query
                  ? 'Try a different course code or name.'
                  : 'Create the first course to begin building the catalogue.'
            }
            action={
              mayWrite && !resource.query ? (
                <Button onClick={() => setCreating(true)}>
                  <Plus strokeWidth={1.5} />
                  Create course
                </Button>
              ) : undefined
            }
          />
        }
        onRowClick={(row) => router.push(`/courses/${encodeURIComponent(row.courseCode)}`)}
      />

      <CursorPagination
        data={resource.data}
        canGoPrev={resource.canGoPrev}
        canGoNext={resource.canGoNext}
        onPrev={resource.goPrev}
        onNext={resource.goNext}
      />

      <CourseFormDialog
        open={creating}
        onClose={() => setCreating(false)}
        onSaved={() => {
          setCreating(false);
          resource.refetch();
        }}
      />
      <CourseFormDialog
        open={!!editing}
        course={editing ?? undefined}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null);
          resource.refetch();
        }}
      />
      <ConfirmDialog
        open={!!deleting}
        title="Remove course"
        message={
          deleting
            ? `Remove ${deleting.courseCode} — ${deleting.name}? Every enrollment in it ends; the students themselves are untouched.`
            : ''
        }
        pending={removeAction.pending}
        onCancel={() => setDeleting(null)}
        onConfirm={confirmDelete}
      />
    </Box>
  );
}
