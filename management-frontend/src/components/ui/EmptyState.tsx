'use client';

import { Box, Center, Icon, Stack, Text } from '@chakra-ui/react';
import type { LucideIcon } from 'lucide-react';
import { Inbox } from 'lucide-react';
import type { ReactNode } from 'react';

/**
 * §13: every empty state explains itself and offers the way out.
 *
 * The old `DataTable` took an `empty` string and rendered it as one line of muted text, which told a
 * Registrar looking at a fresh database that there was nothing there but not what to do about it.
 * The three parts here are the spec's: a small supporting icon, a concise explanation, one primary
 * action. The icon is capped at 32px on purpose — "avoid oversized illustrations that distract from
 * the intended action".
 */
export default function EmptyState({
  icon = Inbox,
  title,
  description,
  action,
}: {
  icon?: LucideIcon;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <Center
      py="16"
      px="8"
      borderWidth="1px"
      borderColor="border"
      borderRadius="l3"
      bg="bg.panel"
    >
      <Stack gap="4" align="center" maxW="26rem" textAlign="center">
        <Icon as={icon} boxSize="8" color="fg.subtle" strokeWidth={1.5} aria-hidden />
        <Box>
          <Text fontWeight="medium">{title}</Text>
          {description ? (
            <Text color="fg.muted" fontSize="sm" mt="2">
              {description}
            </Text>
          ) : null}
        </Box>
        {action}
      </Stack>
    </Center>
  );
}
