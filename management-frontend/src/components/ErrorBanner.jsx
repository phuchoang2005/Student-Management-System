import { ApiError } from '../api/client.js';

/**
 * Top-level error message for a page or form.
 *
 * 409s carry a specific, already user-readable message from the backend ("Student 'S001' already
 * exists"), so it is passed through verbatim -- both the least code and the best output.
 *
 * Field-level 400 errors are rendered inline by <Field>, so they are only summarised here when the
 * form has no matching field to attach them to.
 */
export default function ErrorBanner({ error, fallback = 'Something went wrong.' }) {
  if (!error) return null;

  const isApi = error instanceof ApiError;

  // The must-change-password gate writes a status and returns -- no body, so ApiError's fallback
  // message ("Request failed (403)") would be the only thing to show. Say something useful instead.
  if (isApi && error.status === 403 && error.isBodyless) {
    return (
      <div className="banner banner--error">
        You don&apos;t have permission for this action.
      </div>
    );
  }

  const message = isApi ? error.message : (error?.message ?? fallback);
  const fieldErrors = isApi ? error.errors : [];

  return (
    <div className="banner banner--error">
      {message}
      {fieldErrors.length > 0 && (
        <ul className="banner__list">
          {fieldErrors.map((e) => (
            <li key={`${e.field}-${e.message}`}>
              <strong>{e.field}</strong>: {e.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
