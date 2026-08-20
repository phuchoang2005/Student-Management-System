import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Search + page state for any endpoint returning the PageResponse envelope
 * `{page, size, totalElements, totalPages, content}`.
 *
 * Drives all four list screens, both halves of /me, and the staff-account list -- every one of them
 * returns that same envelope, which is why one hook covers them all.
 *
 * `fetcher(query, page)` must be stable (wrap it in useCallback at the call site).
 */
export default function usePagedResource(fetcher, { size = 20, debounceMs = 300 } = {}) {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [reloadToken, setReloadToken] = useState(0);

  // Debounce the query, and reset to page 0 whenever it changes -- staying on page 3 of the old
  // result set while typing a new search shows an empty table for no good reason.
  useEffect(() => {
    const id = setTimeout(() => {
      setDebouncedQuery(query);
      setPage(0);
    }, debounceMs);
    return () => clearTimeout(id);
  }, [query, debounceMs]);

  // Guards against a slow early request landing after a fast later one and overwriting it.
  const requestId = useRef(0);

  useEffect(() => {
    const current = ++requestId.current;
    setLoading(true);
    setError(null);

    Promise.resolve(fetcher(debouncedQuery, page, size))
      .then((result) => {
        if (current !== requestId.current) return;
        setData(result);
      })
      .catch((err) => {
        if (current !== requestId.current) return;
        setError(err);
        setData(null);
      })
      .finally(() => {
        if (current === requestId.current) setLoading(false);
      });
  }, [fetcher, debouncedQuery, page, size, reloadToken]);

  /** Called after any write; the list simply refetches rather than patching local state. */
  const refetch = useCallback(() => setReloadToken((n) => n + 1), []);

  return {
    query,
    setQuery,
    page,
    setPage,
    data,
    content: data?.content ?? [],
    totalPages: data?.totalPages ?? 0,
    totalElements: data?.totalElements ?? 0,
    loading,
    error,
    refetch,
  };
}
