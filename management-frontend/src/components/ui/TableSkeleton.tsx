'use client';

import { Box, Skeleton, Stack } from '@chakra-ui/react';

/**
 * What a list looks like while it is arriving.
 *
 * Every list screen used to render a centred spinner at `py="16"`, which meant each navigation
 * collapsed the page to a single dot and then snapped a full table into place. The flash is the
 * problem: nothing about the spinner told you what was coming, and the layout jumped twice.
 *
 * This holds the table's own rhythm instead — the header rule, then rows on the same ~52px pitch —
 * so the page keeps its shape and the real rows replace the placeholders in position. That is also
 * the §8 reading of motion: the interface should look like it is settling, not rebuilding.
 *
 * Six rows is a deliberate stopping point. It fills the fold without implying a page length the
 * response may not have, and a placeholder taller than the answer is its own small lie.
 */
export default function TableSkeleton({ columns = 4, rows = 6 }: { columns?: number; rows?: number }) {
  return (
    <Box borderTopWidth="1px" borderColor="border" aria-busy="true" aria-live="polite">
      <Box srOnly>Loading…</Box>
      <Stack gap="0">
        {Array.from({ length: rows }, (_, row) => (
          <Box
            key={row}
            display="flex"
            gap="5"
            px={{ base: '0', md: '5' }}
            py="4"
            borderBottomWidth="1px"
            borderColor="border.muted"
          >
            {Array.from({ length: columns }, (_, column) => (
              <Skeleton
                key={column}
                h="4"
                borderRadius="sm"
                // The first column is a business key and the rest are prose of varying length —
                // uniform bars read as a loading *graphic*, which draws more attention than the
                // content it stands in for.
                flex={column === 0 ? '0 0 6rem' : column % 2 ? '1' : '0 0 8rem'}
              />
            ))}
          </Box>
        ))}
      </Stack>
    </Box>
  );
}
