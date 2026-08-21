'use client';

import { Alert } from '@chakra-ui/react';

import { ApiError } from '@/lib/api/client';

/**
 * The single place a failed request becomes readable text.
 *
 * The backend's 409 messages are already specific and user-facing ("Student code 'S001' is already
 * in use."), so they are passed through verbatim; only the statuses whose bodies are unhelpful get
 * a written-here message.
 */
export default function ErrorBanner({ error, title }: { error: unknown; title?: string }) {
  if (!error) return null;

  let message: string;
  if (error instanceof ApiError) {
    if (error.status === 403) {
      message = error.isBodyless
        ? 'You must change your password before using this part of the app.'
        : "You don't have permission for this action.";
    } else if (error.status === 0) {
      message = error.message || 'The server could not be reached.';
    } else {
      message = error.message;
    }
  } else {
    message = (error as Error)?.message ?? 'Something went wrong.';
  }

  return (
    <Alert.Root status="error" mb="4">
      <Alert.Indicator />
      <Alert.Content>
        {title ? <Alert.Title>{title}</Alert.Title> : null}
        <Alert.Description>{message}</Alert.Description>
      </Alert.Content>
    </Alert.Root>
  );
}
