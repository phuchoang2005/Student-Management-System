import { useCallback, useState } from 'react';

/**
 * Submit state for forms and row actions: `{run, pending, error, reset}`.
 *
 * `error` is left as the raw ApiError so a form can render `error.fieldError('email')` under the
 * field and <ErrorBanner error={error}/> above it. That pairing is what gives every form inline 400
 * validation and a top-level 409/404 message without any per-form code.
 */
export default function useAsyncAction(fn) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);

  const run = useCallback(
    async (...args) => {
      setPending(true);
      setError(null);
      try {
        return await fn(...args);
      } catch (err) {
        setError(err);
        // Rethrown so callers can skip their success path; the state above is already set.
        throw err;
      } finally {
        setPending(false);
      }
    },
    [fn],
  );

  const reset = useCallback(() => setError(null), []);

  return { run, pending, error, reset };
}
