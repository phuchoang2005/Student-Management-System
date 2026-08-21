'use client';

import { useCallback, useState } from 'react';

import { ApiError } from '@/lib/api/client';

/**
 * Submit state for a form: `{run, pending, error, reset}`.
 *
 * `error` is narrowed to `ApiError` where possible, so a form renders `error.fieldError('email')`
 * under the field and an `ErrorBanner` above it — every form gets inline 400 validation and a
 * top-level 409/404 message without per-form code.
 */
export interface AsyncAction<Args extends unknown[], Result> {
  run: (...args: Args) => Promise<Result | undefined>;
  pending: boolean;
  error: ApiError | null;
  reset: () => void;
}

export function useAsyncAction<Args extends unknown[], Result>(
  fn: (...args: Args) => Promise<Result>,
): AsyncAction<Args, Result> {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const run = useCallback(
    async (...args: Args) => {
      setPending(true);
      setError(null);
      try {
        return await fn(...args);
      } catch (err) {
        setError(
          err instanceof ApiError ? err : new ApiError(0, { message: (err as Error).message }),
        );
        return undefined;
      } finally {
        setPending(false);
      }
    },
    // The caller passes an inline closure; re-creating `run` when it changes is correct and cheap.
    [fn],
  );

  const reset = useCallback(() => setError(null), []);

  return { run, pending, error, reset };
}

export default useAsyncAction;
