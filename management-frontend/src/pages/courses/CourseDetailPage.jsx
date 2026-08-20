import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { courses } from '../../api/endpoints.js';
import ErrorBanner from '../../components/ErrorBanner.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import ForbiddenPage from '../ForbiddenPage.jsx';

/** UC-19. */
export default function CourseDetailPage() {
  const { code } = useParams();
  const [course, setCourse] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    courses
      .get(code)
      .then((c) => !cancelled && setCourse(c))
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [code]);

  if (loading) return <div className="spinner-text">Loading…</div>;
  if (error?.status === 403) return <ForbiddenPage />;
  if (error?.status === 404) {
    return <EmptyState title="Course not found" description={`No course with code ${code}.`} />;
  }
  if (error) return <ErrorBanner error={error} />;

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">{course.name}</h1>
          <p className="page-subtitle mono">{course.courseCode}</p>
        </div>
        <Link to="/courses" className="btn">
          Back to courses
        </Link>
      </div>

      <div className="card">
        <div className="card__title">Details</div>
        <div className="dl">
          <span className="dl__key">Course code</span>
          <span className="mono">{course.courseCode}</span>
          <span className="dl__key">Name</span>
          <span>{course.name}</span>
          <span className="dl__key">Description</span>
          <span>{course.description || <span className="muted">—</span>}</span>
          <span className="dl__key">Credits</span>
          <span>{course.credits}</span>
          <span className="dl__key">Created</span>
          <span>{new Date(course.createdAt).toLocaleString()}</span>
          <span className="dl__key">Last updated</span>
          <span>{new Date(course.updatedAt).toLocaleString()}</span>
        </div>
      </div>

      <div className="card">
        <div className="card__title">Roster</div>
        {/* §9 disclosure: CourseService.getDetail hardcodes roster to an empty list. Rendering the
            empty array as a table would look like a course with no students, which is different. */}
        <div className="disclosure">
          The course roster is not exposed on this endpoint (US-5.5 composition pending). The{' '}
          <code>roster</code> field returns an empty list regardless of who is enrolled. A specific
          enrollment can still be looked up on the <Link to="/enrollments">Enrollments</Link> page.
        </div>
      </div>
    </>
  );
}
