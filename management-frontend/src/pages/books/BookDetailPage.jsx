import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { books } from '../../api/endpoints.js';
import ErrorBanner from '../../components/ErrorBanner.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import Badge from '../../components/Badge.jsx';
import ForbiddenPage from '../ForbiddenPage.jsx';

/** UC-18. */
export default function BookDetailPage() {
  const { isbn } = useParams();
  const [book, setBook] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    books
      .get(isbn)
      .then((b) => !cancelled && setBook(b))
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [isbn]);

  if (loading) return <div className="spinner-text">Loading…</div>;

  // A Student gets a 403 for any book they don't own -- including unowned ones -- so this is a
  // permission outcome rather than a missing record, and must not render as "not found".
  if (error?.status === 403) {
    return <ForbiddenPage message="This book is not one of yours." />;
  }
  if (error?.status === 404) {
    return <EmptyState title="Book not found" description={`No book with ISBN ${isbn}.`} />;
  }
  if (error) return <ErrorBanner error={error} />;

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">{book.title}</h1>
          <p className="page-subtitle mono">{book.isbn}</p>
        </div>
        <Link to="/books" className="btn">
          Back to books
        </Link>
      </div>

      <div className="card">
        <div className="card__title">Details</div>
        <div className="dl">
          <span className="dl__key">ISBN</span>
          <span className="mono">{book.isbn}</span>
          <span className="dl__key">Title</span>
          <span>{book.title}</span>
          <span className="dl__key">Author</span>
          <span>{book.author}</span>
          <span className="dl__key">Published</span>
          <span>{book.publishedDate ?? <span className="muted">—</span>}</span>
          <span className="dl__key">Added</span>
          <span>{new Date(book.createdAt).toLocaleString()}</span>
          <span className="dl__key">Last updated</span>
          <span>{new Date(book.updatedAt).toLocaleString()}</span>
        </div>
      </div>

      <div className="card">
        <div className="card__title">Owner</div>
        {book.owner ? (
          <div className="dl">
            <span className="dl__key">Student</span>
            <span>
              <Link to={`/students/${encodeURIComponent(book.owner.studentCode)}`}>
                {book.owner.firstName} {book.owner.lastName}
              </Link>
            </span>
            <span className="dl__key">Student code</span>
            <span className="mono">{book.owner.studentCode}</span>
            <span className="dl__key">Student id</span>
            <span className="mono">{book.owner.id}</span>
            <span className="dl__key">Email</span>
            <span>{book.owner.email}</span>
          </div>
        ) : (
          <Badge variant="neutral">unowned</Badge>
        )}
      </div>
    </>
  );
}
