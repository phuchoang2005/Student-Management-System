'use client';

import { Alert } from '@chakra-ui/react';

import Reveal from '@/components/motion/Reveal';
import { ApiError } from '@/lib/api/client';

/**
 * The single place a failed request becomes readable text.
 *
 * The backend's 409 messages are already specific and user-facing ("Student code 'S001' is already
 * in use."), so they are passed through verbatim; only the statuses whose bodies are unhelpful get
 * a written-here message.
 *
 * It animates its own height because it appears above content the user has usually already started
 * reading, and a banner that pops in shunts the form down under the cursor. The mapping below is
 * unchanged — those messages encode real backend behaviour, not styling.
 */
export default function ErrorBanner({ error, title }: { error: unknown; title?: string }) {
  let message = '';
  if (error instanceof ApiError) {
    if (error.status === 401) {
      // Outside the login form a 401 is always a dead session -- expired, or ended by an
      // administrator. The redirect to /login is already under way by the time this renders.
      message = error.message || 'Your session has ended. Please sign in again.';
    } else if (error.status === 403) {
      message = error.isBodyless
        ? 'You must change your password before using this part of the app.'
        : "You don't have permission for this action.";
    } else if (error.status === 0) {
      message = error.message || 'The server could not be reached.';
    } else {
      message = error.message;
    }
  } else if (error) {
    message = (error as Error)?.message ?? 'Something went wrong.';
  }

  return (
    <Reveal show={!!error}>
      <Alert.Root status="error" mb="4">
        <Alert.Indicator />
        <Alert.Content>
          {title ? <Alert.Title>{title}</Alert.Title> : null}
          <Alert.Description>{message}</Alert.Description>
        </Alert.Content>
      </Alert.Root>
    </Reveal>
  );
}
