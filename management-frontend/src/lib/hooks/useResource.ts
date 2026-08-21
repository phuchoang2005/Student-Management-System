'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

/** One-shot fetch of a single record, with the same loading/error/refetch shape as the paged hook. */
export function useResource<T>(
  fetcher: () => Promise<T>,
  deps: unknown[] = [],
  { enabled = true }: { enabled?: boolean } = {},
) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  useEffect(() => {
    if (!enabled) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    fetcherRef
      .current()
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
  }, [enabled, reloadToken, ...deps]);

  const refetch = useCallback(() => setReloadToken((n) => n + 1), []);

  return { data, loading, error, refetch };
}

export default useResource;
