'use client';

import { Stack } from '@chakra-ui/react';
import { useEffect, useState, type FormEvent } from 'react';

import ErrorBanner from './ErrorBanner';
import FormDialog from './FormDialog';
import FormField, { TextareaField } from './FormField';
import { courses } from '@/lib/api/endpoints';
import type { CourseDetail, CourseSummary } from '@/lib/api/types';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import useResource from '@/lib/hooks/useResource';

/**
 * Create / edit a course. `courseCode` is immutable — the PUT body does not accept it — so it is
 * disabled when editing rather than silently ignored.
 *
 * Editing re-fetches through `courses.get` for the same reason `StudentFormDialog` does: the caller
 * holds only the `CourseSummary` behind its list row, which carries no `description`. Seeding the
 * field from the summary opened every edit with it blank and the PUT then wrote that blank back —
 * quieter than the student bug, since nothing here is `required`, and worse, because it destroyed
 * the description instead of merely refusing to submit.
 */
export default function CourseFormDialog({
  open,
  course,
  onClose,
  onSaved,
}: {
  open: boolean;
  course?: CourseSummary;
  onClose: () => void;
  onSaved: () => void;
}) {
  const editing = !!course;

  const detail = useResource<CourseDetail>(
    () => courses.get(course!.courseCode),
    [course?.courseCode],
    { enabled: open && editing },
  );

  const [courseCode, setCourseCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [credits, setCredits] = useState('3');

  const action = useAsyncAction(async () => {
    const body = { name, description: description || null, credits: Number(credits) };
    if (editing) return courses.update(course!.courseCode, body);
    return courses.create({ ...body, courseCode });
  });

  // The summary seeds everything it carries immediately; `description` follows with the detail.
  useEffect(() => {
    if (!open) return;
    setCourseCode(course?.courseCode ?? '');
    setName(course?.name ?? '');
    setDescription('');
    setCredits(String(course?.credits ?? 3));
    action.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, course?.courseCode]);

  // Code guard as in StudentFormDialog: `useResource` keeps the previous record until the next
  // request resolves, so opening course B right after course A would briefly seed B with A's text.
  useEffect(() => {
    if (!detail.data || detail.data.courseCode !== course?.courseCode) return;
    setName(detail.data.name);
    setDescription(detail.data.description ?? '');
    setCredits(String(detail.data.credits));
  }, [detail.data, course?.courseCode]);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const result = await action.run();
    if (result) onSaved();
  };

  return (
    <FormDialog
      open={open}
      title={editing ? `Edit ${course!.courseCode}` : 'Create course'}
      submitLabel={editing ? 'Save changes' : 'Create'}
      // Also while the detail loads — submitting early is what blanked the description.
      pending={action.pending || detail.loading}
      onClose={onClose}
      onSubmit={onSubmit}
    >
      <Stack gap="4">
        <ErrorBanner error={action.error} />
        <ErrorBanner error={detail.error} />
        <FormField
          label="Course code"
          name="courseCode"
          value={courseCode}
          onChange={(e) => setCourseCode(e.target.value)}
          error={action.error?.fieldError('courseCode')}
          disabled={editing}
          helper={editing ? 'The course code cannot be changed.' : 'Unique, up to 20 characters.'}
          required={!editing}
        />
        <FormField
          label="Name"
          name="name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={action.error?.fieldError('name')}
          required
        />
        <FormField
          label="Credits"
          name="credits"
          type="number"
          min={1}
          value={credits}
          onChange={(e) => setCredits(e.target.value)}
          error={action.error?.fieldError('credits')}
          required
        />
        <TextareaField
          label="Description"
          name="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={action.error?.fieldError('description')}
          disabled={detail.loading}
          rows={3}
          helper="Optional."
        />
      </Stack>
    </FormDialog>
  );
}
