import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { books as booksApi, students } from '../../api/endpoints.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { can } from '../../auth/permissions.js';
import useAsyncAction from '../../hooks/useAsyncAction.js';
import ErrorBanner from '../../components/ErrorBanner.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import ForbiddenPage from '../ForbiddenPage.jsx';

/** UC-17 (detail) and UC-23 (re-read the initial password). */
export default function StudentDetailPage() {
  const { code } = useParams();
  const { session } = useAuth();

  const [student, setStudent] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  // §9: StudentDetail.books is hardcoded to [] server-side, so the real owned-books list is
  // fetched separately by owner id. This compensates for the stub rather than disclosing it,
  // because the data genuinely is reachable -- just not from this endpoint.
  const [ownedBooks, setOwnedBooks] = useState(null);

  const [initialPassword, setInitialPassword] = useState(null);
  const revealPassword = useAsyncAction(students.initialPassword);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setInitialPassword(null);
    revealPassword.reset();

    students
      .get(code)
      .then((detail) => {
        if (cancelled) return;
        setStudent(detail);
        return booksApi
          .search(undefined, 0, 100, detail.id)
          .then((page) => !cancelled && setOwnedBooks(page.content))
          .catch(() => !cancelled && setOwnedBooks([]));
      })
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false));

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code]);

  const onRevealPassword = async () => {
    try {
      setInitialPassword(await revealPassword.run(code));
    } catch {
      // revealPassword.error is rendered inline; a 404 means it has already been changed.
    }
  };

  if (loading) return <div className="spinner-text">Loading…</div>;

  // A student requesting another student's record gets a 403 from the server -- the UI does no
  // special-casing for scoping, it just renders what comes back.
  if (error?.status === 403) return <ForbiddenPage />;
  if (error?.status === 404) {
    return <EmptyState title="Student not found" description={`No student with code ${code}.`} />;
  }
  if (error) return <ErrorBanner error={error} />;

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">
            {student.firstName} {student.lastName}
          </h1>
          <p className="page-subtitle mono">{student.studentCode}</p>
        </div>
        <Link to="/students" className="btn">
          Back to students
        </Link>
      </div>

      <div className="card">
        <div className="card__title">Details</div>
        <div className="dl">
          <span className="dl__key">Student code</span>
          <span className="mono">{student.studentCode}</span>
          <span className="dl__key">Email</span>
          <span>{student.email}</span>
          <span className="dl__key">Date of birth</span>
          <span>{student.dateOfBirth}</span>
          <span className="dl__key">Registered</span>
          <span>{new Date(student.createdAt).toLocaleString()}</span>
          <span className="dl__key">Last updated</span>
          <span>{new Date(student.updatedAt).toLocaleString()}</span>
        </div>
      </div>

      {/* UC-23 -- Registrar only. */}
      {can(session.role, 'students:initial-password') && (
        <div className="card">
          <div className="card__title">Initial password</div>

          {initialPassword ? (
            <div className="credential">
              <div className="dl">
                <span className="dl__key">Username</span>
                <span className="mono">{initialPassword.username}</span>
                <span className="dl__key">Initial password</span>
                <span className="credential__value">{initialPassword.initialPassword}</span>
              </div>
            </div>
          ) : (
            <>
              {/* The backend returns the same 404 whether the student doesn't exist, has no
                  account, or has already changed the password -- deliberate information hiding. */}
              {revealPassword.error?.status === 404 ? (
                <p className="muted">
                  No unchanged initial password is available for this student. They have most likely
                  already changed it.
                </p>
              ) : (
                <ErrorBanner error={revealPassword.error} />
              )}
              <button
                type="button"
                className="btn"
                onClick={onRevealPassword}
                disabled={revealPassword.pending}
              >
                {revealPassword.pending ? 'Checking…' : 'Reveal initial password'}
              </button>
            </>
          )}
        </div>
      )}

      <div className="card">
        <div className="card__title">Owned books</div>
        {ownedBooks === null ? (
          <div className="spinner-text">Loading…</div>
        ) : ownedBooks.length === 0 ? (
          <EmptyState title="No books assigned" />
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>ISBN</th>
                <th>Title</th>
                <th>Author</th>
              </tr>
            </thead>
            <tbody>
              {ownedBooks.map((b) => (
                <tr key={b.id}>
                  <td className="mono">
                    <Link to={`/books/${encodeURIComponent(b.isbn)}`}>{b.isbn}</Link>
                  </td>
                  <td>{b.title}</td>
                  <td>{b.author}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="muted" style={{ marginTop: 'var(--s-3)', fontSize: 'var(--t-xs)' }}>
          Fetched from <code>GET /books?owner={student.id}</code> — this endpoint&apos;s own{' '}
          <code>books</code> field is a stub that always returns an empty list.
        </p>
      </div>

      <div className="card">
        <div className="card__title">Enrolled courses</div>
        {/* §9 disclosure: unlike books, there is no staff-facing endpoint to compensate with --
            EnrollmentLookup.findByStudent is reachable only through /me. */}
        <div className="disclosure">
          Enrollments are not exposed on this endpoint (US-5.5 composition pending). The{' '}
          <code>courses</code> field returns an empty list regardless of what the student is enrolled
          in. A specific enrollment can still be looked up on the{' '}
          <Link to="/enrollments">Enrollments</Link> page.
        </div>
      </div>
    </>
  );
}
