'use client';

import { Box, Drawer, Flex, HStack, Icon, Kbd, Portal, Separator, Stack, Text } from '@chakra-ui/react';
import { KeyRound, LogOut, Menu, Search, UserRound } from 'lucide-react';
import NextLink from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

import CommandPalette from '@/components/CommandPalette';
import PageTransition from '@/components/motion/PageTransition';
import Button from '@/components/ui/Button';
import RoleBadge from '@/components/ui/RoleBadge';
import ThemeToggle from '@/components/ui/ThemeToggle';
import type { Role } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { CHANGE_PASSWORD_HREF, navItemsFor } from '@/lib/auth/permissions';

const RAIL_WIDTH = '15rem';

/**
 * The application frame: one rail, one content column.
 *
 * The rail *is* the role matrix made visible — a Librarian sees Students and Books, a Course
 * Administrator sees Courses and Enrollments, a System Administrator sees exactly two things.
 * During a role-switching demo the nav is the explanation, so it renders from the same list the
 * route guards consult (`permissions.ts`) rather than a hand-kept copy.
 *
 * ## Three changes from the previous shell
 *
 * **The topbar is gone above `md`.** It held a title that repeated the wordmark and four controls
 * that all belong to the signed-in person rather than to the page. Those moved to the bottom of the
 * rail, which also fixes the rail being a short stack of links floating in a tall empty column.
 * On narrow screens the topbar comes back, because there is no rail to put them in.
 *
 * **The content column is capped and centred.** It used to be `maxW="80rem"` pinned hard left, so
 * on a wide display a four-column table sat in the left half of the screen with its last column
 * flung at the far edge. §2 asks for 1200–1440px of content with generous outer margins; the cap
 * below is the measure, and `mx="auto"` is the margins.
 *
 * **It responds.** Below `md` the rail becomes a drawer. There were no breakpoints in this app at
 * all before, and a fixed 15rem rail beside a horizontally-scrolling table is not usable on a phone.
 */
export default function AppShell({ children }: { children: React.ReactNode }) {
  const { session, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [navOpen, setNavOpen] = useState(false);
  const [findOpen, setFindOpen] = useState(false);

  // A drawer that survives the navigation it triggered would cover the page it just opened.
  useEffect(() => setNavOpen(false), [pathname]);

  const handleLogout = async () => {
    await logout();
    router.replace('/login');
  };

  const rail = (
    <RailContent
      onFind={() => setFindOpen(true)}
      pathname={pathname}
      role={session?.role}
      username={session?.username}
      onLogout={handleLogout}
    />
  );

  return (
    <Flex minH="100vh" direction="column">
      {/* Only below `md`, where the rail is behind a trigger and the wordmark has nowhere else. */}
      <Flex
        as="header"
        hideFrom="md"
        align="center"
        justify="space-between"
        gap="4"
        px="4"
        py="3"
        borderBottomWidth="1px"
        borderColor="border"
        bg="bg.panel"
        position="sticky"
        top="0"
        zIndex="docked"
      >
        <HStack gap="2" minW="0">
          <Button
            tone="neutral"
            variant="ghost"
            size="sm"
            px="2"
            aria-label="Open sections"
            onClick={() => setNavOpen(true)}
          >
            <Icon as={Menu} boxSize="5" strokeWidth={1.5} aria-hidden />
          </Button>
          <Wordmark />
        </HStack>
        <ThemeToggle />
      </Flex>

      <CommandPalette open={findOpen} onOpenChange={setFindOpen} />

      <Drawer.Root open={navOpen} onOpenChange={(e) => setNavOpen(e.open)} placement="start">
        <Portal>
          <Drawer.Backdrop />
          <Drawer.Positioner>
            <Drawer.Content maxW={RAIL_WIDTH}>
              <Drawer.Title srOnly>Sections</Drawer.Title>
              {rail}
            </Drawer.Content>
          </Drawer.Positioner>
        </Portal>
      </Drawer.Root>

      <Flex flex="1" align="stretch">
        <Box
          hideBelow="md"
          w={RAIL_WIDTH}
          flexShrink={0}
          borderRightWidth="1px"
          borderColor="border"
          bg="bg.panel"
          position="sticky"
          top="0"
          alignSelf="flex-start"
          h="100vh"
        >
          {rail}
        </Box>

        <Box as="main" flex="1" minW="0" px={{ base: '4', md: '8' }} py={{ base: '6', md: '10' }}>
          {/* §2: 1200–1440px of content. 1100px of measure plus the rail lands inside that band. */}
          <Box maxW="68.75rem" mx="auto" w="full">
            <PageTransition>{children}</PageTransition>
          </Box>
        </Box>
      </Flex>
    </Flex>
  );
}

function Wordmark() {
  return (
    <Text textStyle="title" fontSize="1rem" truncate>
      Student Management
    </Text>
  );
}

/**
 * The rail's three zones, shared by the fixed column and the drawer so they cannot drift apart.
 *
 * Zone 1 names the application and the vantage point you are looking from — the role sits directly
 * under the wordmark because in this system it is the single fact that determines what the rest of
 * the rail contains. Zone 2 is the sections. Zone 3 is you: the account, pinned to the bottom, so
 * "sign out" and "change password" are never mistaken for places to go.
 */
function RailContent({
  pathname,
  role,
  username,
  onLogout,
  onFind,
}: {
  pathname: string;
  role: Role | undefined;
  username?: string;
  onLogout: () => void;
  onFind: () => void;
}) {
  const items = navItemsFor(role);

  return (
    <Flex direction="column" h="full" p="4" gap="6">
      <Stack gap="3" px="2" pt="2">
        <Wordmark />
        {role ? (
          <HStack gap="2">
            <RoleBadge role={role} />
          </HStack>
        ) : null}
      </Stack>

      {/* The palette's shortcut is worth nothing if only its author knows it exists, so it gets a
          visible control that names the key. */}
      <Button
        tone="neutral"
        variant="outline"
        size="sm"
        justifyContent="flex-start"
        fontWeight="normal"
        color="fg.muted"
        onClick={onFind}
      >
        <Icon as={Search} boxSize="4" strokeWidth={1.5} aria-hidden />
        <Box as="span" flex="1" textAlign="left">
          Find a record
        </Box>
        <Kbd size="sm">⌘K</Kbd>
      </Button>

      <Stack as="nav" aria-label="Sections" gap="1" flex="1">
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

      <Stack gap="2">
        <Separator />
        {username ? (
          <Button
            asChild
            size="sm"
            tone="neutral"
            variant="ghost"
            justifyContent="flex-start"
            fontWeight="normal"
            color="fg.muted"
            _hover={{ bg: 'bg.muted', color: 'fg' }}
          >
            <NextLink href="/account" title={username}>
              <Icon as={UserRound} boxSize="4" strokeWidth={1.5} aria-hidden />
              <Box as="span" truncate>
                {username}
              </Box>
            </NextLink>
          </Button>
        ) : null}
        <Button
          asChild
          size="sm"
          tone="neutral"
          variant="ghost"
          justifyContent="flex-start"
          fontWeight="normal"
          color="fg.muted"
          _hover={{ bg: 'bg.muted', color: 'fg' }}
        >
          <NextLink href={CHANGE_PASSWORD_HREF}>
            <Icon as={KeyRound} boxSize="4" strokeWidth={1.5} aria-hidden />
            Change password
          </NextLink>
        </Button>
        <HStack gap="2">
          <Button
            size="sm"
            tone="neutral"
            variant="ghost"
            justifyContent="flex-start"
            fontWeight="normal"
            color="fg.muted"
            flex="1"
            _hover={{ bg: 'bg.muted', color: 'fg' }}
            onClick={onLogout}
          >
            <Icon as={LogOut} boxSize="4" strokeWidth={1.5} aria-hidden />
            Sign out
          </Button>
          <Box hideBelow="md">
            <ThemeToggle />
          </Box>
        </HStack>
      </Stack>
    </Flex>
  );
}
