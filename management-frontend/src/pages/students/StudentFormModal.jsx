import { useState } from 'react';
import { students } from '../../api/endpoints.js';
import useAsyncAction from '../../hooks/useAsyncAction.js';
import Modal from '../../components/Modal.jsx';
import Field from '../../components/Field.jsx';
import ErrorBanner from '../../components/ErrorBanner.jsx';

/**
 * UC-1 (register) and UC-2 (update) in one modal -- the fields are identical apart from
 * `studentCode`, which is immutable: the PUT body doesn't accept it.
 *
 * On register, `onRegistered` receives the response, which carries the one-time `initialPassword`.
 */
export default function StudentFormModal({ student, onClose, onSaved, onRegistered }) {
  const editing = Boolean(student);

  const [form, setForm] = useState({
    studentCode: student?.studentCode ?? '',
    firstName: student?.firstName ?? '',
    lastName: student?.lastName ?? '',
    email: student?.email ?? '',
    dateOfBirth: student?.dateOfBirth ?? '',
  });

  const set = (key) => (value) => setForm((f) => ({ ...f, [key]: value }));

  const submit = useAsyncAction(async () => {
    if (editing) {
      const { studentCode, ...body } = form;
      return students.update(student.studentCode, body);
    }
    return students.register(form);
  });

  const onSubmit = async (e) => {
    e.preventDefault();
    try {
      const result = await submit.run();
      if (editing) onSaved?.(result);
      else onRegistered?.(result);
    } catch {
      // submit.error is rendered below.
    }
  };

  const err = submit.error;
  const fieldError = (name) => err?.fieldError?.(name);

  return (
    <Modal
      title={editing ? `Edit ${student.studentCode}` : 'Register student'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={submit.pending}>
            Cancel
          </button>
          <button
            type="submit"
            form="student-form"
            className="btn btn--primary"
            disabled={submit.pending}
          >
            {submit.pending ? 'Saving…' : editing ? 'Save changes' : 'Register'}
          </button>
        </>
      }
    >
      <form id="student-form" onSubmit={onSubmit}>
        <ErrorBanner error={err} />

        {!editing && (
          <Field
            label="Student code"
            name="studentCode"
            value={form.studentCode}
            onChange={set('studentCode')}
            error={fieldError('studentCode')}
            hint="Up to 20 characters. Cannot be changed later."
            autoFocus
            required
          />
        )}

        <div className="field-grid">
          <Field
            label="First name"
            name="firstName"
            value={form.firstName}
            onChange={set('firstName')}
            error={fieldError('firstName')}
            autoFocus={editing}
            required
          />
          <Field
            label="Last name"
            name="lastName"
            value={form.lastName}
            onChange={set('lastName')}
            error={fieldError('lastName')}
            required
          />
        </div>

        <Field
          label="Email"
          name="email"
          type="email"
          value={form.email}
          onChange={set('email')}
          error={fieldError('email')}
          hint={editing ? undefined : 'Becomes the student’s login username.'}
          required
        />

        <Field
          label="Date of birth"
          name="dateOfBirth"
          type="date"
          value={form.dateOfBirth}
          onChange={set('dateOfBirth')}
          error={fieldError('dateOfBirth')}
          required
        />
      </form>
    </Modal>
  );
}
