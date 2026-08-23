'use client';

import { HStack, IconButton, Text } from '@chakra-ui/react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

import Tooltip from '@/components/ui/Tooltip';
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

  const atStart = page <= 0;
  const atEnd = page >= data.totalPages - 1;

  return (
    <HStack justify="space-between" mt="6">
      <Text fontSize="sm" color="fg.muted">
        Page {data.page + 1} of {data.totalPages} &middot; {data.totalElements} total
      </Text>
      <HStack gap="2">
        {/* Icon-only, so each carries both a tooltip and an aria-label (§14). The tooltip is
            suppressed on the disabled edge, where a disabled trigger never fires the hover. */}
        <Tooltip content="Previous page" disabled={atStart}>
          <IconButton
            aria-label="Previous page"
            variant="outline"
            colorPalette="gray"
            size="sm"
            disabled={atStart}
            onClick={() => onPageChange(page - 1)}
          >
            <ChevronLeft strokeWidth={1.5} />
          </IconButton>
        </Tooltip>
        <Tooltip content="Next page" disabled={atEnd}>
          <IconButton
            aria-label="Next page"
            variant="outline"
            colorPalette="gray"
            size="sm"
            disabled={atEnd}
            onClick={() => onPageChange(page + 1)}
          >
            <ChevronRight strokeWidth={1.5} />
          </IconButton>
        </Tooltip>
      </HStack>
    </HStack>
  );
}
