'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { Page } from '@/lib/api/types';

/**
 * Search + page state for any `PageResponse` endpoint. Drives every list screen, since all of them
 * return the same envelope.
 *
 * Owns `{query, page, data, loading, error}`, debounces `query`, resets `page` to 0 on a query
 * change, and exposes `refetch()` for after a write. There is no cache-invalidation problem worth a
 * data library here — after any write, the list simply refetches.
 */
export interface PagedResource<T> {
  query: string;
  setQuery: (value: string) => void;
  page: number;
  setPage: (value: number) => void;
  data: Page<T> | null;
  loading: boolean;
  error: unknown;
  refetch: () => void;
}

export function usePagedResource<T>(
  fetcher: (query: string, page: number) => Promise<Page<T>>,
  {
    enabled = true,
    debounceMs = 300,
    deps = [],
  }: {
    enabled?: boolean;
    debounceMs?: number;
    /**
     * Values the fetcher closes over that aren't `query` or `page`. Required whenever the fetcher
     * is parameterised by something the hook doesn't own — the student code on `/enrollments`, the
     * selected course's roster — because the fetcher itself lives in a ref (see below) and changing
     * it cannot trigger a refetch on its own. Omitting them leaves the previous target's rows on
     * screen after the target changes.
     */
    deps?: unknown[];
  } = {},
): PagedResource<T> {
  const [query, setQueryRaw] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<T> | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  // Held in a ref so an inline arrow fetcher (the common case at call sites) doesn't retrigger the
  // effect on every render.
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const setQuery = useCallback((value: string) => {
    setQueryRaw(value);
    setPage(0);
  }, []);

  // A new target starts at its own first page, not wherever the previous one had been paged to.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => setPage(0), deps);

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
      .current(debouncedQuery, page)
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
  }, [enabled, debouncedQuery, page, reloadToken, ...deps]);

  const refetch = useCallback(() => setReloadToken((n) => n + 1), []);

  return useMemo(
    () => ({ query, setQuery, page, setPage, data, loading, error, refetch }),
    [query, setQuery, page, data, loading, error, refetch],
  );
}

export default usePagedResource;
