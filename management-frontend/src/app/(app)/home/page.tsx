'use client';

import { Box, Grid, HStack, Heading, Icon, Separator, Stack, Text } from '@chakra-ui/react';
import { ArrowRight } from 'lucide-react';
import NextLink from 'next/link';

import PageHeader from '@/components/PageHeader';
import Key from '@/components/ui/Key';
import type { Role } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { ROLE_LABELS, navItemsFor } from '@/lib/auth/permissions';
import { useRecentRecords } from '@/lib/recent';

/**
 * What each role is responsible for, in its own words. Mirrors the login board's `ROLE_WORK`, which
 * is what someone read a moment ago while choosing this vantage point.
 */
const ROLE_WORK: Record<Role, string> = {
  REGISTRAR: 'You register students and decide which courses they take.',
  LIBRARIAN: 'You keep the catalogue and track who is holding which book.',
  COURSE_ADMINISTRATOR: 'You own the course catalogue and read its rosters.',
  STUDENT: 'You can see your own record, your own courses, and your own books.',
  SYSTEM_ADMINISTRATOR:
    'You provision staff accounts and end sessions. This role reaches no student, book, or course record.',
};

/**
 * One line per section, saying what is actually there.
 *
 * Keyed by role as well as path, because the same route is a different screen depending on who
 * opens it: `/students` is a searchable roll for a Registrar and is the Student's own record
 * rendered directly, with no list and no search box at all.
 */
const SECTION_WORK: Record<string, string> = {
  'REGISTRAR:/students': 'Search the roll, register someone new, or correct a record.',
  'LIBRARIAN:/students': 'Look up a student to see what they are holding.',
  'STUDENT:/students': 'Your own record, as the registrar holds it.',
  'REGISTRAR:/courses': 'Browse the catalogue and open a course.',
  'COURSE_ADMINISTRATOR:/courses': 'Create and maintain courses, and read their rosters.',
  'STUDENT:/courses': 'The courses you are enrolled in.',
  'LIBRARIAN:/books': 'Add books, assign them to a student, or take them back.',
  'STUDENT:/books': 'The books you are holding.',
  'REGISTRAR:/enrollments': 'Find a student, then add or end their courses.',
  'COURSE_ADMINISTRATOR:/enrollments': 'Pick a course to read who is in it.',
  'SYSTEM_ADMINISTRATOR:/staff-accounts': 'Create staff logins, and disable ones no longer in use.',
  'SYSTEM_ADMINISTRATOR:/sessions': 'See who is signed in right now, and end a session.',
};

const KIND_LABEL: Record<string, string> = {
  student: 'Student',
  course: 'Course',
  book: 'Book',
};

/**
 * Where every role lands after signing in.
 *
 * Deliberately **not** a metrics dashboard. This API exposes no aggregates — `CursorPage<T>` carries
 * no total, and the only real counts anywhere are a course's `enrolledCount` and the staff-account
 * page's `totalElements` — so a wall of big numbers here could only be invented, and an invented
 * number on a records system is worse than no number.
 *
 * It answers the two questions a start page can actually answer: *what am I responsible for*, and
 * *where was I*. The first is drawn from the same `navItemsFor()` the shell and the login board use.
 * The second is the one thing the sidebar cannot do — business keys like `BS7-14-ir7pui` are
 * unmemorable by design, and until now the only route back to a record you had open ten minutes ago
 * was to search for it again.
 */
export default function HomePage() {
  const { session } = useAuth();
  const recent = useRecentRecords();

  if (!session) return null;
  const sections = navItemsFor(session.role).filter((item) => item.href !== '/home');

  return (
    <Box>
      <PageHeader title={ROLE_LABELS[session.role]} description={ROLE_WORK[session.role]} />

      <Stack gap="0" as="ul" listStyleType="none" borderTopWidth="1px" borderColor="border">
        {sections.map((section) => (
          <Box
            as="li"
            key={section.href}
            borderBottomWidth="1px"
            borderColor="border.muted"
            _hover={{ bg: 'bg.muted' }}
            transitionProperty="background-color"
            transitionDuration="fast"
            transitionTimingFunction="zen"
          >
            <HStack asChild gap="4" px={{ base: '0', md: '3' }} py="5" align="center">
              <NextLink href={section.href}>
                <Icon as={section.icon} boxSize="5" strokeWidth={1.5} color="fg.muted" aria-hidden />
                <Box flex="1" minW="0">
                  <Text textStyle="title" fontSize="1.0625rem">
                    {section.label}
                  </Text>
                  <Text textStyle="body" color="fg.muted" mt="0.5">
                    {SECTION_WORK[`${session.role}:${section.href}`] ?? ''}
                  </Text>
                </Box>
                <Icon
                  as={ArrowRight}
                  boxSize="4"
                  strokeWidth={1.5}
                  color="fg.subtle"
                  flexShrink={0}
                  aria-hidden
                />
              </NextLink>
            </HStack>
          </Box>
        ))}
      </Stack>

      {recent.length > 0 ? (
        <Box mt="16">
          <Heading size="md" mb="1">
            Where you were
          </Heading>
          <Text textStyle="body" color="fg.muted" mb="6">
            Records you opened this session. Cleared when you sign out.
          </Text>
          <Separator />
          <Grid templateColumns={{ base: '1fr', md: 'repeat(2, minmax(0, 1fr))' }} gap="0">
            {recent.map((record) => (
              <HStack
                asChild
                key={record.href}
                gap="4"
                py="4"
                px={{ base: '0', md: '3' }}
                borderBottomWidth="1px"
                borderColor="border.muted"
                _hover={{ bg: 'bg.muted' }}
                transitionProperty="background-color"
                transitionDuration="fast"
                transitionTimingFunction="zen"
              >
                <NextLink href={record.href}>
                  <Box minW="0" flex="1">
                    {/* The kind sits beside the key rather than pinned to the far edge: in a
                        two-column grid that put a one-word label half a screen from the record it
                        describes, which is the same mistake the course table's figures used to
                        make. */}
                    <HStack gap="3" minW="0">
                      <Key>{record.code}</Key>
                      <Text textStyle="meta" color="fg.subtle">
                        {KIND_LABEL[record.kind]}
                      </Text>
                    </HStack>
                    <Text textStyle="body" color="fg.muted" truncate>
                      {record.label}
                    </Text>
                  </Box>
                </NextLink>
              </HStack>
            ))}
          </Grid>
        </Box>
      ) : null}
    </Box>
  );
}
