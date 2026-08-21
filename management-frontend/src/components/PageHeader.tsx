'use client';

import { Box, Flex, Heading, Text } from '@chakra-ui/react';
import type { ReactNode } from 'react';

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
    <Flex justify="space-between" align="flex-start" gap="4" mb="5" wrap="wrap">
      <Box>
        <Heading size="lg">{title}</Heading>
        {description ? (
          <Text color="fg.muted" fontSize="sm" mt="1">
            {description}
          </Text>
        ) : null}
      </Box>
      {actions ? <Flex gap="2">{actions}</Flex> : null}
    </Flex>
  );
}
