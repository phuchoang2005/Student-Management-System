'use client';

import type { ComponentProps } from 'react';

import Button from '@/components/ui/Button';

/**
 * A button that lives inside a table row.
 *
 * Row actions used to be outline buttons, and the destructive one carried the danger tone — so a
 * roll of twenty students drew twenty red-outlined **Delete** buttons down the right-hand side, and
 * the single loudest thing on a page for reading student records was the one action nobody came to
 * perform. §15's list of what the app must never feel starts with "noisy", and a column of warnings
 * is exactly that.
 *
 * Red has not gone anywhere: it is on the confirm button inside `ConfirmDialog`, which is the point
 * where something is actually about to be destroyed. Here the control is only a way in, so it is
 * quiet until pointed at — and `destructive` makes it turn red on hover and focus, which is the
 * moment the warning is worth something.
 */
export default function RowAction({
  destructive = false,
  ...rest
}: { destructive?: boolean } & ComponentProps<typeof Button>) {
  return (
    <Button
      size="sm"
      tone="neutral"
      variant="ghost"
      color="fg.muted"
      _hover={destructive ? { bg: 'red.subtle', color: 'red.fg' } : { bg: 'bg.muted', color: 'fg' }}
      _focusVisible={destructive ? { bg: 'red.subtle', color: 'red.fg' } : undefined}
      {...rest}
    />
  );
}
