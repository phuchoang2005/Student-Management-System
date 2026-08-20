import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { courses } from '../../api/endpoints.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { can } from '../../auth/permissions.js';
import usePagedResource from '../../hooks/usePagedResource.js';
import useAsyncAction from '../../hooks/useAsyncAction.js';
import DataTable from '../../components/DataTable.jsx';
import Pagination from '../../components/Pagination.jsx';
import ErrorBanner from '../../components/ErrorBanner.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import ConfirmDialog from '../../components/ConfirmDialog.jsx';
import CourseFormModal from './CourseFormModal.jsx';
import { useToast } from '../../components/Toast.jsx';

/** UC-15 (search), UC-8/9/10 (create, update, remove) for a Course Administrator. */
export default function CourseListPage() {
  const { session } = useAuth();
  const toast = useToast();
  const writable = can(session.role, 'courses:write');

  const fetcher = useCallback((query, page, size) => courses.search(query, page, size), []);
  const list = usePagedResource(fetcher);

  const [editing, setEditing] = useState(null); // course | 'new' | null
  const [deleting, setDeleting] = useState(null);
  const remove = useAsyncAction(courses.remove);

  const onDelete = async () => {
    try {
      await remove.run(deleting.courseCode);
      toast.show(`Removed ${deleting.courseCode}.`);
      setDeleting(null);
      list.refetch();
    } catch {
      // remove.error renders in the dialog.
    }
  };

  const columns = [
    {
      key: 'courseCode',
      header: 'Code',
      className: 'mono',
      render: (c) => <Link to={`/courses/${encodeURIComponent(c.courseCode)}`}>{c.courseCode}</Link>,
    },
    { key: 'name', header: 'Name' },
    { key: 'credits', header: 'Credits' },
    ...(writable
      ? [
          {
            key: 'actions',
            header: '',
            className: 'is-actions',
            render: (c) => (
              <div className="btn-row btn-row--end">
                <button type="button" className="btn btn--sm" onClick={() => setEditing(c)}>
                  Edit
                </button>
                <button type="button" className="btn btn--sm btn--danger" onClick={() => setDeleting(c)}>
                  Delete
                </button>
              </div>
            ),
          },
        ]
      : []),
  ];

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">Courses</h1>
          <p className="page-subtitle">Search by course code or name.</p>
        </div>
        {writable && (
          <button type="button" className="btn btn--primary" onClick={() => setEditing('new')}>
            Create course
          </button>
        )}
      </div>

      <div className="card">
        <input
          className="search-input"
          placeholder="Search courses…"
          value={list.query}
          onChange={(e) => list.setQuery(e.target.value)}
          style={{ marginBottom: 'var(--s-4)' }}
        />

        <ErrorBanner error={list.error} />

        <DataTable
          columns={columns}
          rows={list.content}
          rowKey={(c) => c.id}
          loading={list.loading}
          empty={<EmptyState title="No courses found" description="Try a different search." />}
        />

        <Pagination
          page={list.page}
          totalPages={list.totalPages}
          totalElements={list.totalElements}
          onPageChange={list.setPage}
        />
      </div>

      {editing && (
        <CourseFormModal
          course={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(course) => {
            setEditing(null);
            toast.show(`Saved ${course.courseCode}.`);
            list.refetch();
          }}
        />
      )}

      {deleting && (
        <ConfirmDialog
          title="Remove course"
          message={`Remove ${deleting.courseCode} — ${deleting.name}?`}
          confirmLabel="Remove"
          onConfirm={onDelete}
          onCancel={() => {
            setDeleting(null);
            remove.reset();
          }}
          pending={remove.pending}
          error={remove.error}
        />
      )}
    </>
  );
}
