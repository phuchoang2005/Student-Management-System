'use client';

import { Tooltip as ChakraTooltip, Portal } from '@chakra-ui/react';
import type { ReactNode } from 'react';

/**
 * Chakra v3 ships tooltips as composable parts rather than a component, so every app has to write
 * this file. Ours exists for one purpose: giving the icon-only controls (pagination arrows, the
 * theme toggle) an accessible name that is also visible to a sighted mouse user.
 *
 * It renders through a `Portal` so a tooltip inside `Table.ScrollArea` is not clipped by the
 * scroll container.
 */
export default function Tooltip({
  content,
  children,
  disabled,
}: {
  content: ReactNode;
  children: ReactNode;
  disabled?: boolean;
}) {
  if (disabled) return <>{children}</>;

  return (
    <ChakraTooltip.Root openDelay={300} closeDelay={100}>
      <ChakraTooltip.Trigger asChild>{children}</ChakraTooltip.Trigger>
      <Portal>
        <ChakraTooltip.Positioner>
          <ChakraTooltip.Content>{content}</ChakraTooltip.Content>
        </ChakraTooltip.Positioner>
      </Portal>
    </ChakraTooltip.Root>
  );
}
