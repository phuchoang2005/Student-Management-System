'use client';

import { Box, Field, HStack, Input, InputGroup, Spinner, Stack, Text } from '@chakra-ui/react';
import { Search } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';

import Key from '@/components/ui/Key';
import { students } from '@/lib/api/endpoints';
import type { StudentSummary } from '@/lib/api/types';

/** Matches the debounce the list screens already use, so search feels the same everywhere. */
const DEBOUNCE_MS = 300;
const MAX_RESULTS = 8;

/**
 * Find a student by typing any part of their code, name, or email.
 *
 * This replaces the worst interaction in the application. `/enrollments` opened on an empty
 * "Student code" field with the placeholder `e.g. S00123` and a **Look up** button, and did nothing
 * at all until a Registrar typed a code exactly — from memory, or from another tab. The screen whose
 * whole job is managing a student's courses had no way to find a student. The Librarian's
 * assign-owner field had the same shape and the same problem.
 *
 * It runs on the same `GET /students?query=` the Students roll uses, so there is no new endpoint and
 * no new permission surface: a caller who may not read students gets nothing back, exactly as they
 * would on the roll.
 *
 * Each result shows the code beside the name, because the code is the value being chosen — hiding it
 * would make the field's result a surprise — and because two students can share a name while a
 * `studentCode` is by definition unique.
 *
 * Built from `Input` plus a listbox rather than a headless combobox: this app composes Chakra
 * primitives directly everywhere else, and the ARIA contract here (`combobox` → `listbox` →
 * `option`, arrows to move, Enter to choose, Escape to dismiss) is small enough to state outright
 * and read in one screen.
 */
export default function StudentPicker({
  value,
  onSelect,
  label = 'Student',
  placeholder = 'Search by code, name, or email…',
  helper,
  error,
  autoFocus,
}: {
  /** The selected `studentCode`, or '' for none. */
  value: string;
  onSelect: (studentCode: string) => void;
  label?: string;
  placeholder?: string;
  helper?: string;
  error?: string;
  autoFocus?: boolean;
}) {
  const [query, setQuery] = useState(value);
  const [results, setResults] = useState<StudentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);

  const listId = useId();
  const latest = useRef(0);
  /** Set while a selection is being applied, so the resulting text change doesn't re-search. */
  const justPicked = useRef(false);

  // Keep the field in step when the selection is changed from outside (a reset, a cleared form).
  useEffect(() => {
    justPicked.current = true;
    setQuery(value);
  }, [value]);

  useEffect(() => {
    if (justPicked.current) {
      justPicked.current = false;
      return;
    }
    const trimmed = query.trim();
    if (!trimmed) {
      setResults([]);
      setOpen(false);
      setLoading(false);
      return;
    }
    setLoading(true);
    const timer = setTimeout(() => {
      // Only the newest request may write the list: typing quickly otherwise lets a slow early
      // response land on top of a later, more specific one.
      const ticket = ++latest.current;
      students
        .search(trimmed, undefined, MAX_RESULTS)
        .then((page) => {
          if (ticket !== latest.current) return;
          setResults(page.content);
          setActive(0);
          // Opened only once there is an answer to show, so the list does not flicker open and
          // closed on every keystroke.
          setOpen(true);
        })
        .catch(() => {
          if (ticket === latest.current) setResults([]);
        })
        .finally(() => {
          if (ticket === latest.current) setLoading(false);
        });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query]);

  const choose = (student: StudentSummary) => {
    justPicked.current = true;
    setQuery(student.studentCode);
    setOpen(false);
    onSelect(student.studentCode);
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Escape') {
      setOpen(false);
      return;
    }
    if (!open || results.length === 0) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActive((i) => (i + 1) % results.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((i) => (i - 1 + results.length) % results.length);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      choose(results[active]);
    }
  };

  return (
    <Field.Root invalid={!!error}>
      <Field.Label>{label}</Field.Label>
      <Box position="relative" w="full">
        <InputGroup
          startElement={<Search size={16} strokeWidth={1.5} aria-hidden />}
          endElement={loading ? <Spinner size="xs" borderWidth="1.5px" color="fg.subtle" /> : undefined}
        >
          <Input
            role="combobox"
            aria-expanded={open}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={open && results[active] ? `${listId}-${active}` : undefined}
            autoComplete="off"
            autoFocus={autoFocus}
            placeholder={placeholder}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={onKeyDown}
            onFocus={() => results.length > 0 && setOpen(true)}
            // A blur that fires before the click lands would close the list out from under the
            // pointer, so the close is deferred by one tick.
            onBlur={() => setTimeout(() => setOpen(false), 120)}
          />
        </InputGroup>

        {open ? (
          <Stack
            id={listId}
            role="listbox"
            aria-label="Matching students"
            gap="0"
            position="absolute"
            top="calc(100% + 4px)"
            left="0"
            right="0"
            zIndex="dropdown"
            maxH="17rem"
            overflowY="auto"
            bg="bg.panel"
            borderWidth="1px"
            borderColor="border"
            borderRadius="l2"
            boxShadow="sm"
            py="1"
          >
            {results.length === 0 ? (
              <Text textStyle="body" color="fg.muted" px="4" py="3">
                No student matches that search.
              </Text>
            ) : (
              results.map((student, index) => (
                <HStack
                  key={student.studentCode}
                  id={`${listId}-${index}`}
                  role="option"
                  aria-selected={index === active}
                  gap="3"
                  px="4"
                  py="2.5"
                  cursor="pointer"
                  bg={index === active ? 'bg.muted' : undefined}
                  onMouseEnter={() => setActive(index)}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => choose(student)}
                >
                  <Key>{student.studentCode}</Key>
                  <Text textStyle="body" truncate>
                    {student.firstName} {student.lastName}
                  </Text>
                </HStack>
              ))
            )}
          </Stack>
        ) : null}
      </Box>

      {helper && !error ? <Field.HelperText>{helper}</Field.HelperText> : null}
      {error ? <Field.ErrorText>{error}</Field.ErrorText> : null}
    </Field.Root>
  );
}
