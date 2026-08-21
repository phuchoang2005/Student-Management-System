'use client';

import { Field, Input } from '@chakra-ui/react';
import type { ComponentProps } from 'react';

/**
 * One labelled input with its server-side validation message underneath.
 *
 * `error` comes straight from `ApiError.fieldError(name)`, so a 400 from Bean Validation or from a
 * domain check lands under the right field with no per-form wiring.
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
    <Field.Root invalid={!!error} required={required}>
      <Field.Label>
        {label}
        {required ? <Field.RequiredIndicator /> : null}
      </Field.Label>
      <Input name={name} {...inputProps} />
      {helper && !error ? <Field.HelperText>{helper}</Field.HelperText> : null}
      {error ? <Field.ErrorText>{error}</Field.ErrorText> : null}
    </Field.Root>
  );
}
