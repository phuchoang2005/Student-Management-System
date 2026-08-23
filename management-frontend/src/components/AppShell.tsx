'use client';

import { Box, Flex, HStack, Heading, Icon, Stack, Text } from '@chakra-ui/react';
import NextLink from 'next/link';
import { usePathname, useRouter } from 'next/navigation';

import PageTransition from '@/components/motion/PageTransition';
import Button from '@/components/ui/Button';
import RoleBadge from '@/components/ui/RoleBadge';
import ThemeToggle from '@/components/ui/ThemeToggle';
import { useAuth } from '@/lib/auth/AuthContext';
import { navItemsFor } from '@/lib/auth/permissions';

/**
 * Sidebar + topbar + page slot.
 *
 * The sidebar is the visible form of the role matrix: a Librarian sees Students and Books, a Course
 * Administrator sees Courses and Enrollments, a System Administrator sees exactly two items. During
 * a role-switching demo the nav *is* the explanation, so it's rendered from the same list the route
 * guards consult (`permissions.ts`) rather than a hand-kept copy.
 *
 * The chrome itself is deliberately quiet (§15): one hairline under the topbar, one down the side of
 * the nav, and no fill anywhere except on the active item. Only the page slot animates — a shell
 * that re-enters on every navigation would be the app performing rather than responding.
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
        px="8"
        py="4"
        borderBottomWidth="1px"
        borderColor="border"
        bg="bg.panel"
        gap="6"
      >
        <Heading size="md" fontWeight="semibold" letterSpacing="-0.01em">
          Student Management
        </Heading>
        <HStack gap="4">
          {session ? (
            <>
              <RoleBadge role={session.role} />
              <Text fontSize="sm" color="fg.muted" maxW="16rem" truncate>
                {session.username}
              </Text>
              <ThemeToggle />
              <Button tone="neutral" variant="outline" size="sm" onClick={handleLogout}>
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
          borderColor="border"
          bg="bg.panel"
          p="4"
        >
          <Stack gap="2">
            {items.map((item) => {
              const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
              return (
                <Button
                  key={item.href}
                  asChild
                  size="sm"
                  tone="neutral"
                  justifyContent="flex-start"
                  fontWeight={active ? 'medium' : 'normal'}
                  variant={active ? 'subtle' : 'ghost'}
                  color={active ? 'accent.fg' : 'fg.muted'}
                  bg={active ? 'accent.subtle' : undefined}
                  _hover={{ bg: active ? 'accent.subtle' : 'bg.muted', color: 'fg' }}
                >
                  <NextLink href={item.href} aria-current={active ? 'page' : undefined}>
                    <Icon as={item.icon} boxSize="4" strokeWidth={1.5} aria-hidden />
                    {item.label}
                  </NextLink>
                </Button>
              );
            })}
          </Stack>
        </Box>

        {/* §2: 1200–1440px of content, generous outer margins. */}
        <Box as="main" flex="1" px="8" py="8" maxW="80rem" w="full">
          <PageTransition>{children}</PageTransition>
        </Box>
      </Flex>
    </Flex>
  );
}
