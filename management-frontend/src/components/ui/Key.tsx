'use client';

import { Text } from '@chakra-ui/react';
import type { ComponentProps } from 'react';

/**
 * A business key: a student code, a course code, an ISBN.
 *
 * These are the handles this whole system is addressed by — no numeric id ever crosses the HTTP
 * boundary (`lib/api/types.ts`), so `S00123` and `CS100` are what staff read, type, and say out
 * loud. That makes them the app's real typographic material rather than incidental data, which is
 * why they get the mono face and `tabular-nums` from the `key` text style: in a column, every code
 * occupies the same width and the eye can run straight down the left edge.
 *
 * Deliberately **not** a chip. `<Code>` wrapped each one in a grey rounded box, which is legible on
 * its own and turns into twelve stacked rectangles the moment it appears in a table column — the
 * decoration ends up louder than the value. The monospace face already separates a code from prose;
 * a second device to say the same thing is what §5 and §10 rule out.
 */
export default function Key({
  children,
  muted = false,
  ...rest
}: {
  /** For a code that is context rather than the row's subject — an owner on a book row, say. */
  muted?: boolean;
} & ComponentProps<typeof Text>) {
  return (
    <Text as="span" textStyle="key" color={muted ? 'fg.muted' : 'fg'} {...rest}>
      {children}
    </Text>
  );
}
