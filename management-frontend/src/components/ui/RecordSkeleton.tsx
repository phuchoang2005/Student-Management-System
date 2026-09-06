'use client';

import { Skeleton, Stack } from '@chakra-ui/react';

import SurfaceCard from '@/components/ui/SurfaceCard';

/**
 * What a single record looks like while it is arriving — the `RecordCard` counterpart to
 * `TableSkeleton`, holding the same card boundary and the same label/value pitch so the detail
 * screens stop collapsing to a centred spinner between one record and the next.
 */
export default function RecordSkeleton({ fields = 5 }: { fields?: number }) {
  return (
    <SurfaceCard aria-busy="true">
      <Stack gap="4">
        {Array.from({ length: fields }, (_, i) => (
          <Stack key={i} gap="2">
            <Skeleton h="3" w="6rem" borderRadius="sm" />
            <Skeleton h="4" w={i % 2 ? '60%' : '40%'} borderRadius="sm" />
          </Stack>
        ))}
      </Stack>
    </SurfaceCard>
  );
}
