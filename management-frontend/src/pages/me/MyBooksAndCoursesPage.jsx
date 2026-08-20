import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { me } from '../../api/endpoints.js';
import ErrorBanner from '../../components/ErrorBanner.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import Pagination from '../../components/Pagination.jsx';

/**
 * UC-16. The one endpoint with prefixed paging params -- it composes two independently paged
 * collections, and Spring resolves only one page/size pair per request. Both pages are held here
 * and sent together on every fetch.
 */
const SIZE = 10;

export default function MyBooksAndCoursesPage() {
  const [booksPage, setBooksPage] = useState(0);
  const [coursesPage, setCoursesPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    me.booksAndCourses({ booksPage, booksSize: SIZE, coursesPage, coursesSize: SIZE })
      .then((res) => !cancelled && setData(res))
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [booksPage, coursesPage]);

  if (loading && !data) return <div className="spinner-text">Loading…</div>;
  if (error) return <ErrorBanner error={error} />;

  const books = data?.books;
  const courses = data?.courses;

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">My books &amp; courses</h1>
          <p className="page-subtitle">
            Resolved server-side from your own account — no student id is needed.
          </p>
        </div>
      </div>

      <div className="card">
        <div className="card__title">My books</div>
        {books?.content?.length ? (
          <>
            <table className="data-table">
              <thead>
                <tr>
                  <th>ISBN</th>
                  <th>Title</th>
                  <th>Author</th>
                </tr>
              </thead>
              <tbody>
                {books.content.map((b) => (
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
            <Pagination
              page={books.page}
              totalPages={books.totalPages}
              totalElements={books.totalElements}
              onPageChange={setBooksPage}
            />
          </>
        ) : (
          <EmptyState
            title="No books assigned"
            description="A librarian has not assigned you any books yet."
          />
        )}
      </div>

      <div className="card">
        <div className="card__title">My courses</div>
        {courses?.content?.length ? (
          <>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Name</th>
                  <th>Credits</th>
                </tr>
              </thead>
              <tbody>
                {courses.content.map((c) => (
                  <tr key={c.id}>
                    <td className="mono">
                      <Link to={`/courses/${encodeURIComponent(c.courseCode)}`}>{c.courseCode}</Link>
                    </td>
                    <td>{c.name}</td>
                    <td>{c.credits}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              page={courses.page}
              totalPages={courses.totalPages}
              totalElements={courses.totalElements}
              onPageChange={setCoursesPage}
            />
          </>
        ) : (
          <EmptyState
            title="No courses"
            description="A registrar has not enrolled you in any courses yet."
          />
        )}
      </div>
    </>
  );
}
