'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { CursorPage } from '@/lib/api/types';

/**
 * Search + cursor-stack state for any `CursorPage` endpoint (PM-045). Drives the students, courses,
 * books, and enrollments list screens — the one list screen still on `Page`/`PageResponse` is
 * staff-accounts, which keeps using {@link usePagedResource}.
 *
 * Owns `{query, data, loading, error}`, debounces `query`, resets to the first page on a query
 * change, and exposes `refetch()` for after a write. Backward navigation is a client-held stack of
 * previously-seen cursors (push on Next, pop on Prev) — standard practice for cursor pagination,
 * since a cursor alone has no notion of "the page before this one".
 */
export interface CursorResource<T> {
  query: string;
  setQuery: (value: string) => void;
  data: CursorPage<T> | null;
  loading: boolean;
  error: unknown;
  canGoPrev: boolean;
  canGoNext: boolean;
  goPrev: () => void;
  goNext: () => void;
  refetch: () => void;
}

export function useCursorResource<T>(
  fetcher: (query: string, cursor: string | undefined) => Promise<CursorPage<T>>,
  {
    enabled = true,
    debounceMs = 300,
    deps = [],
  }: {
    enabled?: boolean;
    debounceMs?: number;
    /**
     * Values the fetcher closes over that aren't `query` or the cursor. Same rationale as
     * `usePagedResource`'s `deps` — required whenever the fetcher is parameterised by something the
     * hook doesn't own (the student code on `/enrollments`, the selected course's roster).
     */
    deps?: unknown[];
  } = {},
): CursorResource<T> {
  const [query, setQueryRaw] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  // The cursor stack: index 0 is always `undefined` (the first page). `goNext` pushes the current
  // page's `nextCursor`; `goPrev` pops back to the previous one.
  const [cursorStack, setCursorStack] = useState<(string | undefined)[]>([undefined]);
  const [data, setData] = useState<CursorPage<T> | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  // Held in a ref so an inline arrow fetcher (the common case at call sites) doesn't retrigger the
  // effect on every render.
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const cursor = cursorStack[cursorStack.length - 1];

  const setQuery = useCallback((value: string) => {
    setQueryRaw(value);
    setCursorStack([undefined]);
  }, []);

  // A new target starts at its own first page, not wherever the previous one had been paged to.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => setCursorStack([undefined]), deps);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), debounceMs);
    return () => clearTimeout(timer);
  }, [query, debounceMs]);

  useEffect(() => {
    if (!enabled) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    fetcherRef
      .current(debouncedQuery, cursor)
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err);
          setData(null);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, debouncedQuery, cursor, reloadToken, ...deps]);

  const goNext = useCallback(() => {
    setCursorStack((stack) => {
      if (!data?.nextCursor) return stack;
      return [...stack, data.nextCursor];
    });
  }, [data]);

  const goPrev = useCallback(() => {
    setCursorStack((stack) => (stack.length > 1 ? stack.slice(0, -1) : stack));
  }, []);

  const refetch = useCallback(() => setReloadToken((n) => n + 1), []);

  return useMemo(
    () => ({
      query,
      setQuery,
      data,
      loading,
      error,
      canGoPrev: cursorStack.length > 1,
      canGoNext: !!data?.nextCursor,
      goPrev,
      goNext,
      refetch,
    }),
    [query, setQuery, data, loading, error, cursorStack, goPrev, goNext, refetch],
  );
}

export default useCursorResource;
