'use client';

import { Field, Input, NativeSelect, Textarea } from '@chakra-ui/react';
import type { ComponentProps, ReactNode } from 'react';

/**
 * One labelled input with its server-side validation message underneath.
 *
 * `error` comes straight from `ApiError.fieldError(name)`, so a 400 from Bean Validation or from a
 * domain check lands under the right field with no per-form wiring.
 *
 * The two siblings below exist because `staff-accounts` and `CourseFormDialog` were each rendering a
 * bare `NativeSelect` / `Textarea` with a hand-written label — §10 wants one implementation per
 * control, and a form where only two of five fields show their errors in the same place is the kind
 * of inconsistency that reads as carelessness even when nobody can name it.
 */
export default function FormField({
  label,
  name,
  error,
  helper,
  required,
  ...inputProps
}: {
  label: string;
  name: string;
  error?: string;
  helper?: string;
  required?: boolean;
} & ComponentProps<typeof Input>) {
  return (
    <FieldShell label={label} error={error} helper={helper} required={required}>
      <Input name={name} {...inputProps} />
    </FieldShell>
  );
}

export function TextareaField({
  label,
  name,
  error,
  helper,
  required,
  ...textareaProps
}: {
  label: string;
  name: string;
  error?: string;
  helper?: string;
  required?: boolean;
} & ComponentProps<typeof Textarea>) {
  return (
    <FieldShell label={label} error={error} helper={helper} required={required}>
      <Textarea name={name} {...textareaProps} />
    </FieldShell>
  );
}

export function SelectField({
  label,
  name,
  error,
  helper,
  required,
  children,
  ...selectProps
}: {
  label: string;
  name: string;
  error?: string;
  helper?: string;
  required?: boolean;
  children: ReactNode;
} & ComponentProps<typeof NativeSelect.Field>) {
  return (
    <FieldShell label={label} error={error} helper={helper} required={required}>
      <NativeSelect.Root>
        <NativeSelect.Field name={name} {...selectProps}>
          {children}
        </NativeSelect.Field>
        <NativeSelect.Indicator />
      </NativeSelect.Root>
    </FieldShell>
  );
}

/** Label, control, and exactly one message slot — the shape all three share. */
function FieldShell({
  label,
  error,
  helper,
  required,
  children,
}: {
  label: string;
  error?: string;
  helper?: string;
  required?: boolean;
  children: ReactNode;
}) {
  return (
    <Field.Root invalid={!!error} required={required}>
      <Field.Label fontWeight="medium">
        {label}
        {required ? <Field.RequiredIndicator /> : null}
      </Field.Label>
      {children}
      {helper && !error ? <Field.HelperText>{helper}</Field.HelperText> : null}
      {error ? <Field.ErrorText>{error}</Field.ErrorText> : null}
    </Field.Root>
  );
}
