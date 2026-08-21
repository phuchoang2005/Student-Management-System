'use client';

import { Badge, Box, Button, Flex, HStack, Heading, Stack, Text } from '@chakra-ui/react';
import NextLink from 'next/link';
import { usePathname, useRouter } from 'next/navigation';

import { useAuth } from '@/lib/auth/AuthContext';
import { ROLE_COLORS, ROLE_LABELS, navItemsFor } from '@/lib/auth/permissions';

/**
 * Sidebar + topbar + page slot.
 *
 * The sidebar is the visible form of the role matrix: a Librarian sees Students and Books, a Course
 * Administrator sees Courses and Enrollments, a System Administrator sees exactly two items. During
 * a role-switching demo the nav *is* the explanation, so it's rendered from the same list the route
 * guards consult (`permissions.ts`) rather than a hand-kept copy.
 */
export default function AppShell({ children }: { children: React.ReactNode }) {
  const { session, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  const items = navItemsFor(session?.role);

  const handleLogout = async () => {
    await logout();
    router.replace('/login');
  };

  return (
    <Flex minH="100vh" direction="column">
      <Flex
        as="header"
        align="center"
        justify="space-between"
        px="6"
        py="3"
        borderBottomWidth="1px"
        bg="bg.panel"
        gap="4"
      >
        <Heading size="md">Student Management</Heading>
        <HStack gap="3">
          {session ? (
            <>
              <Badge colorPalette={ROLE_COLORS[session.role]} variant="subtle">
                {ROLE_LABELS[session.role]}
              </Badge>
              <Text fontSize="sm" color="fg.muted" maxW="16rem" truncate>
                {session.username}
              </Text>
              <Button size="xs" variant="outline" onClick={handleLogout}>
                Log out
              </Button>
            </>
          ) : null}
        </HStack>
      </Flex>

      <Flex flex="1" align="stretch">
        <Box
          as="nav"
          aria-label="Sections"
          w="15rem"
          flexShrink={0}
          borderRightWidth="1px"
          bg="bg.panel"
          p="3"
        >
          <Stack gap="1">
            {items.map((item) => {
              const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
              return (
                <Button
                  key={item.href}
                  asChild
                  size="sm"
                  justifyContent="flex-start"
                  variant={active ? 'subtle' : 'ghost'}
                  colorPalette={active ? 'blue' : undefined}
                >
                  <NextLink href={item.href} aria-current={active ? 'page' : undefined}>
                    {item.label}
                  </NextLink>
                </Button>
              );
            })}
          </Stack>
        </Box>

        <Box as="main" flex="1" p="6" maxW="72rem">
          {children}
        </Box>
      </Flex>
    </Flex>
  );
}
