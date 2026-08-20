import { useState } from 'react';
import { Link } from 'react-router-dom';
import { enrollments } from '../../api/endpoints.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { can } from '../../auth/permissions.js';
import useAsyncAction from '../../hooks/useAsyncAction.js';
import Field from '../../components/Field.jsx';
import ErrorBanner from '../../components/ErrorBanner.jsx';
import ConfirmDialog from '../../components/ConfirmDialog.jsx';
import { useToast } from '../../components/Toast.jsx';

/**
 * UC-11, UC-12, UC-20.
 *
 * An action screen rather than a list, because enrollments have no list endpoint at all: the
 * resource is keyed by the student id + course code pair, and only that exact pair can be fetched.
 */
export default function EnrollmentPage() {
  const { session } = useAuth();
  const toast = useToast();
  const writable = can(session.role, 'enrollments:write');

  const [lookupStudentId, setLookupStudentId] = useState('');
  const [lookupCourseCode, setLookupCourseCode] = useState('');
  const [found, setFound] = useState(null);

  const [createStudentId, setCreateStudentId] = useState('');
  const [createCourseCode, setCreateCourseCode] = useState('');

  const [ending, setEnding] = useState(false);

  const lookup = useAsyncAction(enrollments.get);
  const create = useAsyncAction(enrollments.create);
  const remove = useAsyncAction(enrollments.remove);

  const onLookup = async (e) => {
    e.preventDefault();
    setFound(null);
    try {
      setFound(await lookup.run(Number(lookupStudentId), lookupCourseCode));
    } catch {
      // lookup.error renders below; a 404 means that pairing doesn't exist.
    }
  };

  const onCreate = async (e) => {
    e.preventDefault();
    try {
      const created = await create.run(Number(createStudentId), createCourseCode);
      toast.show(`Enrolled student #${created.studentId} in ${created.courseCode}.`);
      setCreateStudentId('');
      setCreateCourseCode('');
    } catch {
      // create.error renders below.
    }
  };

  const onEnd = async () => {
    try {
      await remove.run(found.student.id, found.course.courseCode);
      toast.show('Enrollment ended.');
      setEnding(false);
      setFound(null);
    } catch {
      // remove.error renders in the dialog.
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">Enrollments</h1>
          <p className="page-subtitle">
            Keyed by student id and course code — there is no list endpoint for this resource.
          </p>
        </div>
      </div>

      {writable && (
        <div className="card">
          <div className="card__title">Enroll a student</div>
          <form onSubmit={onCreate}>
            {/* An unknown student id or course code comes back as a 400 with an errors[] entry,
                not a 404, so both land inline under their field. */}
            <ErrorBanner error={create.error} />
            <div className="field-grid">
              <Field
                label="Student id"
                name="studentId"
                type="number"
                value={createStudentId}
                onChange={setCreateStudentId}
                error={create.error?.fieldError?.('studentId')}
                hint="Numeric id, from the student's detail page."
                required
              />
              <Field
                label="Course code"
                name="courseCode"
                value={createCourseCode}
                onChange={setCreateCourseCode}
                error={create.error?.fieldError?.('courseCode')}
                hint="For example CS101."
                required
              />
            </div>
            <button type="submit" className="btn btn--primary" disabled={create.pending}>
              {create.pending ? 'Enrolling…' : 'Enroll'}
            </button>
          </form>
        </div>
      )}

      <div className="card">
        <div className="card__title">Look up an enrollment</div>
        <form onSubmit={onLookup}>
          <div className="field-grid">
            <Field
              label="Student id"
              name="lookupStudentId"
              type="number"
              value={lookupStudentId}
              onChange={setLookupStudentId}
              required
            />
            <Field
              label="Course code"
              name="lookupCourseCode"
              value={lookupCourseCode}
              onChange={setLookupCourseCode}
              required
            />
          </div>
          <button type="submit" className="btn" disabled={lookup.pending}>
            {lookup.pending ? 'Looking up…' : 'Look up'}
          </button>
        </form>

        {lookup.error?.status === 404 ? (
          <div className="banner banner--warning" style={{ marginTop: 'var(--s-4)' }}>
            No enrollment exists for that student and course.
          </div>
        ) : (
          <div style={{ marginTop: 'var(--s-4)' }}>
            <ErrorBanner error={lookup.error} />
          </div>
        )}

        {found && (
          <div style={{ marginTop: 'var(--s-5)' }}>
            <div className="dl">
              <span className="dl__key">Student</span>
              <span>
                <Link to={`/students/${encodeURIComponent(found.student.studentCode)}`}>
                  {found.student.firstName} {found.student.lastName}
                </Link>{' '}
                <span className="mono muted">({found.student.studentCode})</span>
              </span>
              <span className="dl__key">Student id</span>
              <span className="mono">{found.student.id}</span>
              <span className="dl__key">Course</span>
              <span>
                <Link to={`/courses/${encodeURIComponent(found.course.courseCode)}`}>
                  {found.course.name}
                </Link>{' '}
                <span className="mono muted">({found.course.courseCode})</span>
              </span>
              <span className="dl__key">Credits</span>
              <span>{found.course.credits}</span>
              <span className="dl__key">Enrolled at</span>
              <span>{new Date(found.enrolledAt).toLocaleString()}</span>
            </div>

            {writable && (
              <div className="btn-row" style={{ marginTop: 'var(--s-4)' }}>
                <button type="button" className="btn btn--danger" onClick={() => setEnding(true)}>
                  End enrollment
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {ending && found && (
        <ConfirmDialog
          title="End enrollment"
          message={`End ${found.student.firstName} ${found.student.lastName}'s enrollment in ${found.course.courseCode}?`}
          confirmLabel="End enrollment"
          onConfirm={onEnd}
          onCancel={() => {
            setEnding(false);
            remove.reset();
          }}
          pending={remove.pending}
          error={remove.error}
        />
      )}
    </>
  );
}
