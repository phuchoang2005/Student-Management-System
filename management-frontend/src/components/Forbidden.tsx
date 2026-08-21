'use client';

import { Alert, Box, Heading, Text } from '@chakra-ui/react';

/**
 * Rendered when the local capability map says the current role can't reach a route — so the user
 * sees an explanation instead of the blank 403 the server would answer with.
 */
export default function Forbidden() {
  return (
    <Box maxW="lg">
      <Heading size="lg" mb="3">
        Not available for your role
      </Heading>
      <Alert.Root status="warning">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Description>
            This page belongs to a different role&rsquo;s workflow. Use the navigation on the left
            to reach the parts of the system your account covers.
          </Alert.Description>
        </Alert.Content>
      </Alert.Root>
      <Text mt="4" color="fg.muted" fontSize="sm">
        Access is enforced by the server as well; this screen only saves you a failed request.
      </Text>
    </Box>
  );
}
