'use client';

import { Box, Flex, Heading, Text } from '@chakra-ui/react';
import type { ReactNode } from 'react';

/**
 * Hierarchy Level 1 (§3): the page title, its one-line explanation, and the actions that belong to
 * the page as a whole. Separation from the content below is 32px of space rather than a rule —
 * "whitespace must communicate hierarchy" (§2).
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
    <Flex justify="space-between" align="flex-start" gap="6" mb="8" wrap="wrap">
      <Box>
        <Heading size="lg">{title}</Heading>
        {description ? (
          <Text color="fg.muted" fontSize="sm" mt="2" maxW="46rem">
            {description}
          </Text>
        ) : null}
      </Box>
      {actions ? <Flex gap="2">{actions}</Flex> : null}
    </Flex>
  );
}
