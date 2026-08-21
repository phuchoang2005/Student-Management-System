'use client';

import { Alert, Code, Stack, Text } from '@chakra-ui/react';
import { useEffect, useState, type FormEvent } from 'react';

import ErrorBanner from './ErrorBanner';
import FormDialog from './FormDialog';
import FormField from './FormField';
import { students } from '@/lib/api/endpoints';
import type { StudentRegistration, StudentSummary } from '@/lib/api/types';
import useAsyncAction from '@/lib/hooks/useAsyncAction';

/**
 * Register / edit, in one dialog — the fields are the same and only `studentCode` differs, since it
 * is the business key and immutable after registration.
 *
 * Registration ends by showing the generated password once. That is not a nicety: the plaintext is
 * returned in this response and nowhere else afterwards, recoverable only through UC-23 and only
 * until the student changes it.
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

  // Reset whenever the dialog opens, so a cancelled edit never leaks into the next one.
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
      </FormDialog>
    );
  }

  return (
    <FormDialog
      open={open}
      title={editing ? `Edit ${student!.studentCode}` : 'Register student'}
      submitLabel={editing ? 'Save changes' : 'Register'}
      pending={action.pending}
      onClose={onClose}
      onSubmit={onSubmit}
    >
      <Stack gap="4">
        <ErrorBanner error={action.error} />
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
          required
        />
      </Stack>
    </FormDialog>
  );
}
