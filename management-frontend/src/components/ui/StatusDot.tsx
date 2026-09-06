'use client';

import { Box, HStack, Text } from '@chakra-ui/react';
import type { ReactNode } from 'react';

/**
 * The accent, doing its one job.
 *
 * §5 allows a single accent beside the primary, and the previous build honoured that cap by never
 * using the accent at all — leaving an app that was indigo and grey while `matcha` sat unused in the
 * token file. Here moss means exactly one thing, everywhere it appears: **this record is currently
 * in a state that someone is responsible for.** A book is out on loan; a session is live right now.
 *
 * It is never a role, never a severity, and never decoration. When the state is the *absence* of
 * that responsibility — a book on the shelf, an ended session — the dot goes hollow and neutral
 * rather than switching to a second hue, because there is no second hue.
 *
 * The dot is an indicator, not the message: the label beside it always says the same thing in
 * words, so the state survives both colour-blindness and a greyscale print (§14).
 */
export default function StatusDot({
  active,
  children,
}: {
  /** True when the record is in the state worth marking. */
  active: boolean;
  children: ReactNode;
}) {
  return (
    <HStack gap="2" minW="0">
      <Box
        boxSize="1.5"
        borderRadius="full"
        flexShrink={0}
        // The nominal accent (#6B8E7A) is only ever a fill, a border, or an indicator — this is the
        // indicator case, so it is the one place the 500 step is correct as-is.
        bg={active ? 'matcha.500' : 'transparent'}
        borderWidth={active ? undefined : '1px'}
        borderColor="border"
        aria-hidden
      />
      <Text textStyle="body" color={active ? 'fg' : 'fg.muted'} truncate>
        {children}
      </Text>
    </HStack>
  );
}
