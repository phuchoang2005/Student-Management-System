'use client';

import { Portal, Toast, Toaster as ChakraToaster, createToaster } from '@chakra-ui/react';

/**
 * The confirmation channel this app did not have.
 *
 * Until now a successful write said nothing at all: the dialog closed, the list refetched, and the
 * user was left to infer from a redrawn table that anything had happened. Failure had a voice
 * (`ErrorBanner`) and success did not, which is the wrong way round — a failure is visible in its
 * consequences, a success often is not.
 *
 * Deliberately kept for **success only**. Errors stay inline, next to the field or the form that
 * produced them, because `ErrorBanner` already resolves this backend's ambiguous 401/403 cases into
 * written guidance and a toast would move that guidance away from the thing it is about, then take
 * it off screen after four seconds.
 *
 * Visually it is a plain panel with a hairline and no status colour. There is no green tick because
 * there is no red cross to pair it with: the toast only ever appears when something worked, so its
 * presence *is* the signal, and a colour would be the second device saying the same thing that §5
 * and §10 rule out. Moss is not used here either — the accent means "a record is in this state",
 * not "your action succeeded".
 */
export const toaster = createToaster({
  placement: 'bottom-end',
  pauseOnPageIdle: true,
  duration: 4000,
  max: 3,
});

/**
 * Confirm a completed action, in the words the control that started it used.
 *
 * The rule is that an action keeps its name for its whole life: the button that says "Register
 * student" produces "Student registered", and "Release" produces "Book released". Passing a
 * sentence that renames the operation is how an interface stops being learnable.
 */
export function confirmDone(message: string) {
  toaster.create({ type: 'success', description: message });
}

export function Toaster() {
  return (
    <Portal>
      <ChakraToaster toaster={toaster} insetInline={{ mdDown: '4' }}>
        {(toast) => (
          <Toast.Root
            width={{ md: 'sm' }}
            bg="bg.panel"
            color="fg"
            borderWidth="1px"
            borderColor="border"
            borderRadius="l2"
            boxShadow="sm"
          >
            <Toast.Description textStyle="body">{toast.description}</Toast.Description>
            <Toast.CloseTrigger />
          </Toast.Root>
        )}
      </ChakraToaster>
    </Portal>
  );
}
