'use client';

import { Grid, Stack } from '@chakra-ui/react';
import type { ReactNode } from 'react';

/**
 * The shape every detail screen takes: the record itself on the left, what it is connected to on
 * the right.
 *
 * Previously a detail page stacked a full-width `RecordCard` on top of its related tables. Six short
 * label/value pairs stretched across the whole measure put the values a long way from their labels
 * and left most of the card empty, while the tables — which is what the page is *for* once you know
 * whose record it is — started below the fold.
 *
 * Splitting them puts the identity where identity belongs (narrow, scannable, always visible) and
 * gives the related lists the width they actually use. Below `lg` it stacks back, record first,
 * because on a phone "who is this" still has to come before "what are they enrolled in".
 */
export default function DetailLayout({
  record,
  children,
}: {
  /** The identity column — normally a `RecordCard`. */
  record: ReactNode;
  /** The related lists. */
  children?: ReactNode;
}) {
  return (
    <Grid
      gap={{ base: '10', lg: '12' }}
      alignItems="start"
      templateColumns={{ base: '1fr', lg: '21rem minmax(0, 1fr)' }}
    >
      {record}
      {children ? <Stack gap="12">{children}</Stack> : null}
    </Grid>
  );
}
