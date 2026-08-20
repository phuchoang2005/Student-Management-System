import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { students } from '../../api/endpoints.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { can } from '../../auth/permissions.js';
import usePagedResource from '../../hooks/usePagedResource.js';
import useAsyncAction from '../../hooks/useAsyncAction.js';
import DataTable from '../../components/DataTable.jsx';
import Pagination from '../../components/Pagination.jsx';
import ErrorBanner from '../../components/ErrorBanner.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import ConfirmDialog from '../../components/ConfirmDialog.jsx';
import StudentFormModal from './StudentFormModal.jsx';
import { useToast } from '../../components/Toast.jsx';

/** UC-13 (search), UC-1/2/3 (register, update, remove) for a Registrar. */
export default function StudentListPage() {
  const { session } = useAuth();
  const toast = useToast();
  const writable = can(session.role, 'students:write');

  const fetcher = useCallback((query, page, size) => students.search(query, page, size), []);
  const list = usePagedResource(fetcher);

  const [editing, setEditing] = useState(null); // student | 'new' | null
  const [deleting, setDeleting] = useState(null);
  const [registration, setRegistration] = useState(null);

  const remove = useAsyncAction(students.remove);

  const onDelete = async () => {
    try {
      await remove.run(deleting.studentCode);
      toast.show(`Removed ${deleting.studentCode}.`);
      setDeleting(null);
      list.refetch();
    } catch {
      // remove.error renders in the dialog.
    }
  };

  const columns = [
    {
      key: 'studentCode',
      header: 'Code',
      className: 'mono',
      render: (s) => <Link to={`/students/${encodeURIComponent(s.studentCode)}`}>{s.studentCode}</Link>,
    },
    { key: 'firstName', header: 'First name' },
    { key: 'lastName', header: 'Last name' },
    { key: 'email', header: 'Email' },
    ...(writable
      ? [
          {
            key: 'actions',
            header: '',
            className: 'is-actions',
            render: (s) => (
              <div className="btn-row btn-row--end">
                <button type="button" className="btn btn--sm" onClick={() => setEditing(s)}>
                  Edit
                </button>
                <button
                  type="button"
                  className="btn btn--sm btn--danger"
                  onClick={() => setDeleting(s)}
                >
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
          <h1 className="page-title">Students</h1>
          <p className="page-subtitle">
            {session.role === 'STUDENT'
              ? 'Scoped server-side to your own record.'
              : 'Search by code, name, or email.'}
          </p>
        </div>
        {writable && (
          <button type="button" className="btn btn--primary" onClick={() => setEditing('new')}>
            Register student
          </button>
        )}
      </div>

      <div className="card">
        <input
          className="search-input"
          placeholder="Search students…"
          value={list.query}
          onChange={(e) => list.setQuery(e.target.value)}
          style={{ marginBottom: 'var(--s-4)' }}
        />

        <ErrorBanner error={list.error} />

        <DataTable
          columns={columns}
          rows={list.content}
          rowKey={(s) => s.id}
          loading={list.loading}
          empty={<EmptyState title="No students found" description="Try a different search." />}
        />

        <Pagination
          page={list.page}
          totalPages={list.totalPages}
          totalElements={list.totalElements}
          onPageChange={list.setPage}
        />
      </div>

      {editing && (
        <StudentFormModal
          student={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            toast.show('Student updated.');
            list.refetch();
          }}
          onRegistered={(result) => {
            setEditing(null);
            setRegistration(result);
            list.refetch();
          }}
        />
      )}

      {/* UC-1: the initial password is returned exactly once, here. It stays re-readable from the
          detail page until the student changes it. */}
      {registration && (
        <ConfirmDialog
          title="Student registered"
          danger={false}
          confirmLabel="Done"
          onConfirm={() => setRegistration(null)}
          onCancel={() => setRegistration(null)}
          message={
            <span>
              <strong>
                {registration.firstName} {registration.lastName}
              </strong>{' '}
              ({registration.studentCode}) can now sign in.
              <div className="credential" style={{ marginTop: 'var(--s-4)' }}>
                <div className="dl">
                  <span className="dl__key">Username</span>
                  <span className="mono">{registration.username}</span>
                  <span className="dl__key">Initial password</span>
                  <span className="credential__value">{registration.initialPassword}</span>
                </div>
              </div>
              <p className="muted" style={{ marginTop: 'var(--s-3)', fontSize: 'var(--t-sm)' }}>
                They will be forced to change it at first login.
              </p>
            </span>
          }
        />
      )}

      {deleting && (
        <ConfirmDialog
          title="Remove student"
          message={`Remove ${deleting.studentCode} — ${deleting.firstName} ${deleting.lastName}? This also removes their account.`}
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
