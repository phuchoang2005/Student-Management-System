'use client';

import { Box, Flex, Heading, Text } from '@chakra-ui/react';
import type { ReactNode } from 'react';

/**
 * Hierarchy Level 1 (§3): the page title, its one-line explanation, and the actions that belong to
 * the page as a whole. Separation from the content below is space rather than a rule —
 * "whitespace must communicate hierarchy" (§2).
 *
 * The description is capped at 68 characters rather than the previous `46rem` (roughly 90), which
 * was past the point where the eye reliably finds the start of the next line. §4's reading measure
 * and the `prose` text style's 1.65 line-height are meant to be used together.
 */
export default function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <Flex justify="space-between" align="flex-start" gap="6" mb="10" wrap="wrap">
      <Box minW="0">
        <Heading size="lg">{title}</Heading>
        {description ? (
          <Text textStyle="prose" color="fg.muted" mt="3" maxW="68ch">
            {description}
          </Text>
        ) : null}
      </Box>
      {actions ? (
        <Flex gap="2" flexShrink={0}>
          {actions}
        </Flex>
      ) : null}
    </Flex>
  );
}
