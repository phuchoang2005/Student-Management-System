'use client';

import { Button, HStack, Text } from '@chakra-ui/react';

import type { Page } from '@/lib/api/types';

/** `page` is 0-based on the wire; this is the only place that translates it for a human. */
export default function Pagination<T>({
  data,
  page,
  onPageChange,
}: {
  data: Page<T> | null;
  page: number;
  onPageChange: (page: number) => void;
}) {
  if (!data || data.totalPages <= 1) return null;

  return (
    <HStack justify="space-between" mt="4">
      <Text fontSize="sm" color="fg.muted">
        Page {data.page + 1} of {data.totalPages} &middot; {data.totalElements} total
      </Text>
      <HStack>
        <Button
          size="xs"
          variant="outline"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </Button>
        <Button
          size="xs"
          variant="outline"
          disabled={page >= data.totalPages - 1}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </Button>
      </HStack>
    </HStack>
  );
}
