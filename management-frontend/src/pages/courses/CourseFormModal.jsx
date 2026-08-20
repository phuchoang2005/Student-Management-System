import { useState } from 'react';
import { courses } from '../../api/endpoints.js';
import useAsyncAction from '../../hooks/useAsyncAction.js';
import Modal from '../../components/Modal.jsx';
import Field from '../../components/Field.jsx';
import ErrorBanner from '../../components/ErrorBanner.jsx';

/** UC-8 (create) and UC-9 (update). `courseCode` is immutable, so the PUT body omits it. */
export default function CourseFormModal({ course, onClose, onSaved }) {
  const editing = Boolean(course);

  const [form, setForm] = useState({
    courseCode: course?.courseCode ?? '',
    name: course?.name ?? '',
    description: course?.description ?? '',
    credits: course?.credits != null ? String(course.credits) : '',
  });
  const set = (key) => (value) => setForm((f) => ({ ...f, [key]: value }));

  const submit = useAsyncAction(() => {
    const body = {
      name: form.name,
      description: form.description || null,
      credits: form.credits === '' ? null : Number(form.credits),
    };
    return editing
      ? courses.update(course.courseCode, body)
      : courses.create({ ...body, courseCode: form.courseCode });
  });

  const onSubmit = async (e) => {
    e.preventDefault();
    try {
      onSaved?.(await submit.run());
    } catch {
      // submit.error is rendered below.
    }
  };

  const err = submit.error;
  const fieldError = (name) => err?.fieldError?.(name);

  return (
    <Modal
      title={editing ? `Edit ${course.courseCode}` : 'Create course'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={submit.pending}>
            Cancel
          </button>
          <button type="submit" form="course-form" className="btn btn--primary" disabled={submit.pending}>
            {submit.pending ? 'Saving…' : editing ? 'Save changes' : 'Create'}
          </button>
        </>
      }
    >
      <form id="course-form" onSubmit={onSubmit}>
        <ErrorBanner error={err} />

        {!editing && (
          <Field
            label="Course code"
            name="courseCode"
            value={form.courseCode}
            onChange={set('courseCode')}
            error={fieldError('courseCode')}
            hint="Up to 20 characters. Cannot be changed later."
            autoFocus
            required
          />
        )}

        <Field
          label="Name"
          name="name"
          value={form.name}
          onChange={set('name')}
          error={fieldError('name')}
          autoFocus={editing}
          required
        />

        <Field
          label="Description"
          name="description"
          value={form.description}
          onChange={set('description')}
          error={fieldError('description')}
          hint="Optional."
        >
          <textarea
            className="field__input"
            rows={3}
            value={form.description}
            onChange={(e) => set('description')(e.target.value)}
          />
        </Field>

        <Field
          label="Credits"
          name="credits"
          type="number"
          value={form.credits}
          onChange={set('credits')}
          error={fieldError('credits')}
          hint="Must be at least 1."
          required
        />
      </form>
    </Modal>
  );
}
