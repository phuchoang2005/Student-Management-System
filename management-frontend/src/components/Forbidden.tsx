'use client';

import { Box } from '@chakra-ui/react';
import { Lock } from 'lucide-react';

import EmptyState from '@/components/ui/EmptyState';

/**
 * Rendered when the local capability map says the current role can't reach a route — so the user
 * sees an explanation instead of the blank 403 the server would answer with.
 *
 * It uses the same `EmptyState` shape as "no results found" rather than a warning alert: this is not
 * an error the user made, it is a part of the system that simply isn't theirs, and §15 asks the
 * interface to stay calm about that.
 */
export default function Forbidden() {
  return (
    <Box maxW="42rem" mx="auto" py="16">
      <EmptyState
        icon={Lock}
        title="Not available for your role"
        description="This page belongs to a different role's workflow. Use the navigation on the left to reach the parts of the system your account covers — access is enforced by the server as well, so this screen only saves you a failed request."
      />
    </Box>
  );
}
