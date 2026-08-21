'use client';

import { Button, CloseButton, Dialog, Portal } from '@chakra-ui/react';
import type { FormEvent, ReactNode } from 'react';

/**
 * The create/edit modal every write flow uses. It owns the `<form>` so Enter submits, and keeps the
 * submit button disabled while a request is in flight.
 */
export default function FormDialog({
  open,
  title,
  submitLabel = 'Save',
  pending,
  onClose,
  onSubmit,
  children,
}: {
  open: boolean;
  title: string;
  submitLabel?: string;
  pending?: boolean;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  children: ReactNode;
}) {
  return (
    <Dialog.Root open={open} onOpenChange={(e) => (e.open ? undefined : onClose())} size="md">
      <Portal>
        <Dialog.Backdrop />
        <Dialog.Positioner>
          <Dialog.Content>
            {/* A real <form> element rather than `Dialog.Content as="form"`: the polymorphic `as`
                prop does not re-type the submit handler, and this nests identically. */}
            <form onSubmit={onSubmit}>
              <Dialog.Header>
                <Dialog.Title>{title}</Dialog.Title>
              </Dialog.Header>
              <Dialog.Body>{children}</Dialog.Body>
              <Dialog.Footer>
                <Button variant="outline" type="button" onClick={onClose}>
                  Cancel
                </Button>
                <Button type="submit" loading={pending}>
                  {submitLabel}
                </Button>
              </Dialog.Footer>
            </form>
            <Dialog.CloseTrigger asChild>
              <CloseButton size="sm" />
            </Dialog.CloseTrigger>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  );
}
