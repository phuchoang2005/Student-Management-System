'use client';

import { Stack } from '@chakra-ui/react';
import { useEffect, useState, type FormEvent } from 'react';

import ErrorBanner from './ErrorBanner';
import FormDialog from './FormDialog';
import FormField from './FormField';
import { books } from '@/lib/api/endpoints';
import useAsyncAction from '@/lib/hooks/useAsyncAction';

/**
 * Add a book, optionally already assigned to a student.
 *
 * The owner is named by student code. There is no id to type and no student picker to build: an
 * unknown code comes back as a 400 with the offending field named, which is exactly what the form
 * needs to show.
 */
export default function BookFormDialog({
  open,
  onClose,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [isbn, setIsbn] = useState('');
  const [title, setTitle] = useState('');
  const [author, setAuthor] = useState('');
  const [publishedDate, setPublishedDate] = useState('');
  const [ownerStudentCode, setOwnerStudentCode] = useState('');

  const action = useAsyncAction(() =>
    books.create({
      isbn,
      title,
      author,
      publishedDate: publishedDate || null,
      ownerStudentCode: ownerStudentCode || null,
    }),
  );

  useEffect(() => {
    if (!open) return;
    setIsbn('');
    setTitle('');
    setAuthor('');
    setPublishedDate('');
    setOwnerStudentCode('');
    action.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const result = await action.run();
    if (result) onSaved();
  };

  return (
    <FormDialog
      open={open}
      title="Add book"
      submitLabel="Add"
      pending={action.pending}
      onClose={onClose}
      onSubmit={onSubmit}
    >
      <Stack gap="4">
        <ErrorBanner error={action.error} />
        <FormField
          label="ISBN"
          name="isbn"
          value={isbn}
          onChange={(e) => setIsbn(e.target.value)}
          error={action.error?.fieldError('isbn')}
          required
        />
        <FormField
          label="Title"
          name="title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          error={action.error?.fieldError('title')}
          required
        />
        <FormField
          label="Author"
          name="author"
          value={author}
          onChange={(e) => setAuthor(e.target.value)}
          error={action.error?.fieldError('author')}
          required
        />
        <FormField
          label="Published"
          name="publishedDate"
          type="date"
          value={publishedDate}
          onChange={(e) => setPublishedDate(e.target.value)}
          error={action.error?.fieldError('publishedDate')}
        />
        <FormField
          label="Assign to student code"
          name="ownerStudentCode"
          value={ownerStudentCode}
          onChange={(e) => setOwnerStudentCode(e.target.value)}
          error={action.error?.fieldError('ownerStudentCode')}
          helper="Optional. Leave blank to shelve the book unassigned."
        />
      </Stack>
    </FormDialog>
  );
}
