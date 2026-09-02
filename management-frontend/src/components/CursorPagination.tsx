'use client';

import { HStack, IconButton } from '@chakra-ui/react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

import Tooltip from '@/components/ui/Tooltip';
import type { CursorPage } from '@/lib/api/types';

/**
 * Prev/Next-only navigation for a {@link CursorPage} endpoint (PM-045) — no "Page N of M" text,
 * since a cursor carries no total count. See `Pagination` for the page-number variant staff-accounts
 * still uses.
 */
export default function CursorPagination<T>({
  data,
  canGoPrev,
  canGoNext,
  onPrev,
  onNext,
}: {
  data: CursorPage<T> | null;
  canGoPrev: boolean;
  canGoNext: boolean;
  onPrev: () => void;
  onNext: () => void;
}) {
  if (!data || (!canGoPrev && !canGoNext)) return null;

  return (
    <HStack justify="flex-end" mt="6" gap="2">
      {/* Icon-only, so each carries both a tooltip and an aria-label (§14). The tooltip is
          suppressed on the disabled edge, where a disabled trigger never fires the hover. */}
      <Tooltip content="Previous page" disabled={!canGoPrev}>
        <IconButton
          aria-label="Previous page"
          variant="outline"
          colorPalette="gray"
          size="sm"
          disabled={!canGoPrev}
          onClick={onPrev}
        >
          <ChevronLeft strokeWidth={1.5} />
        </IconButton>
      </Tooltip>
      <Tooltip content="Next page" disabled={!canGoNext}>
        <IconButton
          aria-label="Next page"
          variant="outline"
          colorPalette="gray"
          size="sm"
          disabled={!canGoNext}
          onClick={onNext}
        >
          <ChevronRight strokeWidth={1.5} />
        </IconButton>
      </Tooltip>
    </HStack>
  );
}
