'use client';

import { Button, Dialog, Portal, Text } from '@chakra-ui/react';

/** Destructive-action gate. Every delete in the app goes through it. */
export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Delete',
  pending,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  pending?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(e) => (e.open ? undefined : onCancel())}
      role="alertdialog"
      size="sm"
    >
      <Portal>
        <Dialog.Backdrop />
        <Dialog.Positioner>
          <Dialog.Content>
            <Dialog.Header>
              <Dialog.Title>{title}</Dialog.Title>
            </Dialog.Header>
            <Dialog.Body>
              <Text fontSize="sm">{message}</Text>
            </Dialog.Body>
            <Dialog.Footer>
              <Button variant="outline" onClick={onCancel}>
                Cancel
              </Button>
              <Button colorPalette="red" loading={pending} onClick={onConfirm}>
                {confirmLabel}
              </Button>
            </Dialog.Footer>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  );
}
