'use client';

import { HStack, Text } from '@chakra-ui/react';
import { ChevronRight } from 'lucide-react';
import NextLink from 'next/link';

/**
 * Where this record sits, and the way back up.
 *
 * The detail screens used to carry a **Back** button wired to `router.back()`, which is browser
 * history rather than structure: arriving at a student from a course roster, from the palette, or
 * from a pasted URL all produced a different "back", and one of them produced none at all. A
 * breadcrumb says the same thing the URL does — this is a student, students live here — and is the
 * same on every route into the page.
 */
export default function Breadcrumb({
  parent,
  current,
}: {
  parent: { href: string; label: string };
  current: string;
}) {
  return (
    <HStack gap="2" mb="4" color="fg.muted" minW="0">
      <Text
        asChild
        textStyle="meta"
        _hover={{ color: 'fg', textDecoration: 'underline' }}
        transitionProperty="color"
        transitionDuration="fast"
      >
        <NextLink href={parent.href}>{parent.label}</NextLink>
      </Text>
      <ChevronRight size={14} strokeWidth={1.5} aria-hidden />
      <Text textStyle="meta" color="fg" truncate>
        {current}
      </Text>
    </HStack>
  );
}
