'use client';

import { Stack, Textarea } from '@chakra-ui/react';
import { useEffect, useState, type FormEvent } from 'react';

import ErrorBanner from './ErrorBanner';
import FormDialog from './FormDialog';
import FormField from './FormField';
import { courses } from '@/lib/api/endpoints';
import type { CourseSummary } from '@/lib/api/types';
import useAsyncAction from '@/lib/hooks/useAsyncAction';

/**
 * Create / edit a course. `courseCode` is immutable — the PUT body does not accept it — so it is
 * disabled when editing rather than silently ignored.
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

  const [courseCode, setCourseCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [credits, setCredits] = useState('3');

  const action = useAsyncAction(async () => {
    const body = { name, description: description || null, credits: Number(credits) };
    if (editing) return courses.update(course!.courseCode, body);
    return courses.create({ ...body, courseCode });
  });

  useEffect(() => {
    if (!open) return;
    setCourseCode(course?.courseCode ?? '');
    setName(course?.name ?? '');
    setDescription('');
    setCredits(String(course?.credits ?? 3));
    action.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, course?.courseCode]);

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
      pending={action.pending}
      onClose={onClose}
      onSubmit={onSubmit}
    >
      <Stack gap="4">
        <ErrorBanner error={action.error} />
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
        <Textarea
          name="description"
          placeholder="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          aria-label="Description"
        />
      </Stack>
    </FormDialog>
  );
}
