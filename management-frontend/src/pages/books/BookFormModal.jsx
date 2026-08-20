import { useState } from 'react';
import { books } from '../../api/endpoints.js';
import useAsyncAction from '../../hooks/useAsyncAction.js';
import Modal from '../../components/Modal.jsx';
import Field from '../../components/Field.jsx';
import ErrorBanner from '../../components/ErrorBanner.jsx';

/**
 * UC-4. There is no update endpoint for books, so this only ever creates.
 *
 * `publishedDate` and `ownerId` are both optional. An unknown `ownerId` comes back as a 400 with an
 * `errors[]` entry (UnknownStudentException is a DomainValidationException), not a 404, so it
 * renders inline under the field.
 */
export default function BookFormModal({ onClose, onCreated }) {
  const [form, setForm] = useState({ isbn: '', title: '', author: '', publishedDate: '', ownerId: '' });
  const set = (key) => (value) => setForm((f) => ({ ...f, [key]: value }));

  const submit = useAsyncAction(() =>
    books.create({
      isbn: form.isbn,
      title: form.title,
      author: form.author,
      publishedDate: form.publishedDate || null,
      ownerId: form.ownerId ? Number(form.ownerId) : null,
    }),
  );

  const onSubmit = async (e) => {
    e.preventDefault();
    try {
      onCreated?.(await submit.run());
    } catch {
      // submit.error is rendered below.
    }
  };

  const err = submit.error;
  const fieldError = (name) => err?.fieldError?.(name);

  return (
    <Modal
      title="Add book"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={submit.pending}>
            Cancel
          </button>
          <button type="submit" form="book-form" className="btn btn--primary" disabled={submit.pending}>
            {submit.pending ? 'Saving…' : 'Add book'}
          </button>
        </>
      }
    >
      <form id="book-form" onSubmit={onSubmit}>
        <ErrorBanner error={err} />

        <Field
          label="ISBN"
          name="isbn"
          value={form.isbn}
          onChange={set('isbn')}
          error={fieldError('isbn')}
          hint="Up to 20 characters."
          autoFocus
          required
        />
        <Field
          label="Title"
          name="title"
          value={form.title}
          onChange={set('title')}
          error={fieldError('title')}
          required
        />
        <Field
          label="Author"
          name="author"
          value={form.author}
          onChange={set('author')}
          error={fieldError('author')}
          required
        />
        <Field
          label="Published date"
          name="publishedDate"
          type="date"
          value={form.publishedDate}
          onChange={set('publishedDate')}
          error={fieldError('publishedDate')}
          hint="Optional."
        />
        <Field
          label="Owner (student id)"
          name="ownerId"
          type="number"
          value={form.ownerId}
          onChange={set('ownerId')}
          error={fieldError('ownerId')}
          hint="Optional. A numeric student id, not a student code — leave blank to add it unowned."
        />
      </form>
    </Modal>
  );
}
