'use client';

import { Box, Dialog, HStack, Icon, Input, Portal, Spinner, Stack, Text } from '@chakra-ui/react';
import { Search } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';

import Key from '@/components/ui/Key';
import { books, courses, students } from '@/lib/api/endpoints';
import { useAuth } from '@/lib/auth/AuthContext';
import { can, navItemsFor } from '@/lib/auth/permissions';

const DEBOUNCE_MS = 250;
const PER_KIND = 4;

interface Entry {
  href: string;
  /** The business key, when the entry is a record. Sections have none. */
  code?: string;
  label: string;
  kind: string;
}

/**
 * Jump straight to a record by typing its code.
 *
 * This is worth having in *this* application specifically. Every record here is addressed by a
 * human-readable business key — `BS7-14-ir7pui`, `CRS00003`, an ISBN — and no numeric id ever
 * crosses the boundary (`lib/api/types.ts`). Staff read those codes off paper, off email, off each
 * other; the natural motion is "I have a code, take me to it". Without this, that motion is: pick
 * the right section, wait for a list, find the search box, type, click the row.
 *
 * It searches only what the signed-in role may read, using `can()` — the same capability map the
 * route guards and the sidebar consult. A Librarian's palette never queries courses, so the
 * palette cannot become a side door around the read model the rest of the app is careful about.
 * The server would refuse anyway; not asking is what keeps the two consistent.
 */
export default function CommandPalette({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { session } = useAuth();
  const router = useRouter();

  const [query, setQuery] = useState('');
  const [records, setRecords] = useState<Entry[]>([]);
  const [loading, setLoading] = useState(false);
  const [active, setActive] = useState(0);
  const latest = useRef(0);

  // ⌘K on macOS, Ctrl+K elsewhere — the shortcut people already try.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        onOpenChange(!open);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onOpenChange]);

  const role = session?.role;

  const sections: Entry[] = (role ? navItemsFor(role) : []).map((item) => ({
    href: item.href,
    label: item.label,
    kind: 'Go to',
  }));

  const search = useCallback(
    async (text: string) => {
      const ticket = ++latest.current;
      const jobs: Promise<Entry[]>[] = [];

      if (can(role, 'students:read')) {
        jobs.push(
          students
            .search(text, undefined, PER_KIND)
            .then((page) =>
              page.content.map((student) => ({
                href: `/students/${encodeURIComponent(student.studentCode)}`,
                code: student.studentCode,
                label: `${student.firstName} ${student.lastName}`,
                kind: 'Student',
              })),
            )
            .catch(() => []),
        );
      }
      if (can(role, 'courses:read')) {
        jobs.push(
          courses
            .search(text, undefined, PER_KIND)
            .then((page) =>
              page.content.map((course) => ({
                href: `/courses/${encodeURIComponent(course.courseCode)}`,
                code: course.courseCode,
                label: course.name,
                kind: 'Course',
              })),
            )
            .catch(() => []),
        );
      }
      if (can(role, 'books:read')) {
        jobs.push(
          books
            .search(text, undefined, PER_KIND)
            .then((page) =>
              page.content.map((book) => ({
                href: `/books/${encodeURIComponent(book.isbn)}`,
                code: book.isbn,
                label: book.title,
                kind: 'Book',
              })),
            )
            .catch(() => []),
        );
      }

      const found = (await Promise.all(jobs)).flat();
      if (ticket !== latest.current) return;
      setRecords(found);
      setLoading(false);
    },
    [role],
  );

  useEffect(() => {
    const text = query.trim();
    if (!text) {
      setRecords([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    const timer = setTimeout(() => void search(text), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query, search]);

  // A palette that reopens on yesterday's search is a palette you have to clear first.
  useEffect(() => {
    if (!open) {
      setQuery('');
      setRecords([]);
      setActive(0);
    }
  }, [open]);

  const text = query.trim().toLowerCase();
  const matchedSections = text
    ? sections.filter((section) => section.label.toLowerCase().includes(text))
    : sections;
  const entries = [...matchedSections, ...records];

  useEffect(() => setActive(0), [query]);

  const go = (entry: Entry) => {
    onOpenChange(false);
    router.push(entry.href);
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (entries.length === 0) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActive((i) => (i + 1) % entries.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((i) => (i - 1 + entries.length) % entries.length);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      go(entries[active]);
    }
  };

  if (!session) return null;

  return (
    <Dialog.Root
      open={open}
      onOpenChange={(event) => onOpenChange(event.open)}
      placement="top"
      scrollBehavior="inside"
      size="lg"
    >
      <Portal>
        <Dialog.Backdrop />
        <Dialog.Positioner>
          <Dialog.Content mt="20" overflow="hidden">
            <Dialog.Title srOnly>Find a record or section</Dialog.Title>
            <HStack gap="3" px="5" py="4" borderBottomWidth="1px" borderColor="border">
              <Icon as={Search} boxSize="4" strokeWidth={1.5} color="fg.subtle" aria-hidden />
              <Input
                autoFocus
                unstyled
                flex="1"
                textStyle="body"
                placeholder="Search by code, name, or section…"
                aria-label="Search by code, name, or section"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={onKeyDown}
                // The dialog traps focus and this input is the only thing in it that takes focus,
                // so the global ring would draw a second box inside the one the user just opened.
                // The caret carries the indication instead.
                _focusVisible={{ outline: 'none' }}
              />
              {loading ? <Spinner size="xs" borderWidth="1.5px" color="fg.subtle" /> : null}
            </HStack>

            <Stack gap="0" maxH="24rem" overflowY="auto" py="1" role="listbox" aria-label="Results">
              {entries.length === 0 ? (
                <Text textStyle="body" color="fg.muted" px="5" py="6">
                  {query.trim()
                    ? 'Nothing matches that. Codes, names, and section names all work.'
                    : 'Type a code, a name, or a section.'}
                </Text>
              ) : (
                entries.map((entry, index) => (
                  <HStack
                    key={`${entry.kind}:${entry.href}`}
                    role="option"
                    aria-selected={index === active}
                    gap="3"
                    px="5"
                    py="3"
                    cursor="pointer"
                    bg={index === active ? 'bg.muted' : undefined}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => go(entry)}
                  >
                    {entry.code ? <Key>{entry.code}</Key> : null}
                    <Text textStyle="body" truncate flex="1">
                      {entry.label}
                    </Text>
                    <Text textStyle="meta" color="fg.subtle" flexShrink={0}>
                      {entry.kind}
                    </Text>
                  </HStack>
                ))
              )}
            </Stack>
          </Dialog.Content>
        </Dialog.Positioner>
      </Portal>
    </Dialog.Root>
  );
}
