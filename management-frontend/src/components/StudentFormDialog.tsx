'use client';

import { Alert, Code, Stack, Text } from '@chakra-ui/react';
import { useEffect, useState, type FormEvent } from 'react';

import FadeIn from './motion/FadeIn';
import ErrorBanner from './ErrorBanner';
import FormDialog from './FormDialog';
import FormField from './FormField';
import { students } from '@/lib/api/endpoints';
import type { StudentDetail, StudentRegistration, StudentSummary } from '@/lib/api/types';
import useAsyncAction from '@/lib/hooks/useAsyncAction';
import useResource from '@/lib/hooks/useResource';

/**
 * Register / edit, in one dialog — the fields are the same and only `studentCode` differs, since it
 * is the business key and immutable after registration.
 *
 * Registration ends by showing the generated password once. That is not a nicety: the plaintext is
 * returned in this response and nowhere else afterwards, recoverable only through UC-23 and only
 * until the student changes it.
 *
 * Editing re-fetches the student through `students.get`, because the caller only holds the
 * `StudentSummary` its list row was built from and that shape carries no `dateOfBirth`. Seeding the
 * field from the summary left it empty on every edit, and since it is `required` the browser's own
 * constraint check rejected the form before React saw it — `onSubmit` never fired and the Save
 * button looked broken. The list stays cheap and only the record being edited is fetched in full.
 */
export default function StudentFormDialog({
  open,
  student,
  onClose,
  onSaved,
}: {
  open: boolean;
  /** Absent for a registration. */
  student?: StudentSummary;
  onClose: () => void;
  onSaved: () => void;
}) {
  const editing = !!student;

  const detail = useResource<StudentDetail>(
    () => students.get(student!.studentCode),
    [student?.studentCode],
    { enabled: open && editing },
  );

  const [studentCode, setStudentCode] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [registered, setRegistered] = useState<StudentRegistration | null>(null);

  const action = useAsyncAction(async () => {
    const body = { firstName, lastName, email, dateOfBirth };
    if (editing) return students.update(student!.studentCode, body);
    return students.register({ ...body, studentCode });
  });

  // Reset whenever the dialog opens, so a cancelled edit never leaks into the next one. The
  // summary seeds every field it carries straight away, so an edit renders populated rather than
  // blank while the detail request is still in flight.
  useEffect(() => {
    if (!open) return;
    setStudentCode(student?.studentCode ?? '');
    setFirstName(student?.firstName ?? '');
    setLastName(student?.lastName ?? '');
    setEmail(student?.email ?? '');
    setDateOfBirth('');
    setRegistered(null);
    action.reset();
    // Only the open/target pair should reset the form.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, student?.studentCode]);

  // Fill in from the full record once it lands — `dateOfBirth` exists nowhere else. The code guard
  // matters: `useResource` holds the previous record until the next request resolves, so without it
  // opening student B straight after student A would seed B's form with A's values for one render.
  useEffect(() => {
    if (!detail.data || detail.data.studentCode !== student?.studentCode) return;
    setFirstName(detail.data.firstName);
    setLastName(detail.data.lastName);
    setEmail(detail.data.email);
    setDateOfBirth(detail.data.dateOfBirth);
  }, [detail.data, student?.studentCode]);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const result = await action.run();
    if (!result) return;
    if (!editing && 'initialPassword' in result) {
      // Hold the dialog open on the one-time password instead of closing over it.
      setRegistered(result as StudentRegistration);
      return;
    }
    onSaved();
  };

  if (registered) {
    return (
      <FormDialog
        open={open}
        title="Student registered"
        submitLabel="Done"
        onClose={onSaved}
        onSubmit={(event) => {
          event.preventDefault();
          onSaved();
        }}
      >
        <FadeIn>
        <Alert.Root status="success" mb="4">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Title>Shown once</Alert.Title>
            <Alert.Description>
              Give these to the student now. The password is recoverable afterwards only from their
              detail page, and only until they change it.
            </Alert.Description>
          </Alert.Content>
        </Alert.Root>
        <Stack gap="2">
          <Text fontSize="sm">
            Student code: <Code>{registered.studentCode}</Code>
          </Text>
          <Text fontSize="sm">
            Username: <Code>{registered.username}</Code>
          </Text>
          <Text fontSize="sm">
            Initial password: <Code>{registered.initialPassword}</Code>
          </Text>
        </Stack>
        </FadeIn>
      </FormDialog>
    );
  }

  return (
    <FormDialog
      open={open}
      title={editing ? `Edit ${student!.studentCode}` : 'Register student'}
      submitLabel={editing ? 'Save changes' : 'Register'}
      // Also while the detail loads: submitting a half-seeded form would blank the fields the
      // summary doesn't carry, which is the bug this dialog just stopped having.
      pending={action.pending || detail.loading}
      onClose={onClose}
      onSubmit={onSubmit}
    >
      <Stack gap="4">
        <ErrorBanner error={action.error} />
        <ErrorBanner error={detail.error} />
        <FormField
          label="Student code"
          name="studentCode"
          value={studentCode}
          onChange={(e) => setStudentCode(e.target.value)}
          error={action.error?.fieldError('studentCode')}
          disabled={editing}
          helper={editing ? 'The student code cannot be changed.' : 'Unique, up to 20 characters.'}
          required={!editing}
        />
        <FormField
          label="First name"
          name="firstName"
          value={firstName}
          onChange={(e) => setFirstName(e.target.value)}
          error={action.error?.fieldError('firstName')}
          required
        />
        <FormField
          label="Last name"
          name="lastName"
          value={lastName}
          onChange={(e) => setLastName(e.target.value)}
          error={action.error?.fieldError('lastName')}
          required
        />
        <FormField
          label="Email"
          name="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={action.error?.fieldError('email')}
          helper="Also the student's login username; changing it renames their account."
          required
        />
        <FormField
          label="Date of birth"
          name="dateOfBirth"
          type="date"
          value={dateOfBirth}
          onChange={(e) => setDateOfBirth(e.target.value)}
          error={action.error?.fieldError('dateOfBirth')}
          disabled={detail.loading}
          required
        />
      </Stack>
    </FormDialog>
  );
}
