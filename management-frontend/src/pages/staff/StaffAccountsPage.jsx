import { useCallback, useState } from 'react';
import { staffAccounts } from '../../api/endpoints.js';
import usePagedResource from '../../hooks/usePagedResource.js';
import useAsyncAction from '../../hooks/useAsyncAction.js';
import DataTable from '../../components/DataTable.jsx';
import Pagination from '../../components/Pagination.jsx';
import ErrorBanner from '../../components/ErrorBanner.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import ConfirmDialog from '../../components/ConfirmDialog.jsx';
import Modal from '../../components/Modal.jsx';
import Field from '../../components/Field.jsx';
import Badge, { RoleBadge } from '../../components/Badge.jsx';
import { useToast } from '../../components/Toast.jsx';

const STAFF_ROLES = ['REGISTRAR', 'LIBRARIAN', 'COURSE_ADMINISTRATOR'];

/**
 * UC-24, UC-25. The only screen a System Administrator can reach — that role holds zero domain
 * read access, which is a deliberate allow-list in SecurityConfig rather than an oversight.
 *
 * The listing is scoped server-side to STAFF_ROLES, so this never shows sysadmin or student
 * accounts. It exists to surface the numeric user id that PATCH /{id}/status is keyed by: the
 * create response deliberately carries no id.
 */
export default function StaffAccountsPage() {
  const toast = useToast();

  // No search param on this endpoint -- paging only.
  const fetcher = useCallback((query, page, size) => staffAccounts.list(page, size), []);
  const list = usePagedResource(fetcher);

  const [creating, setCreating] = useState(false);
  const [username, setUsername] = useState('');
  const [role, setRole] = useState(STAFF_ROLES[0]);
  const [created, setCreated] = useState(null);
  const [toggling, setToggling] = useState(null);

  const create = useAsyncAction(staffAccounts.create);
  const setStatus = useAsyncAction(staffAccounts.setStatus);

  const onCreate = async (e) => {
    e.preventDefault();
    try {
      const result = await create.run(username, role);
      setCreating(false);
      setCreated(result);
      setUsername('');
      list.refetch();
    } catch {
      // create.error renders in the modal.
    }
  };

  const onToggle = async () => {
    try {
      const next = !toggling.enabled;
      await setStatus.run(toggling.id, next);
      toast.show(`${toggling.username} ${next ? 'reactivated' : 'deactivated'}.`);
      setToggling(null);
      list.refetch();
    } catch {
      // setStatus.error renders in the dialog.
    }
  };

  const columns = [
    { key: 'id', header: 'ID', className: 'mono' },
    { key: 'username', header: 'Username', className: 'mono' },
    { key: 'role', header: 'Role', render: (a) => <RoleBadge role={a.role} /> },
    {
      key: 'enabled',
      header: 'Status',
      render: (a) =>
        a.enabled ? <Badge variant="success">active</Badge> : <Badge variant="danger">disabled</Badge>,
    },
    {
      key: 'actions',
      header: '',
      className: 'is-actions',
      render: (a) => (
        <button
          type="button"
          className={`btn btn--sm ${a.enabled ? 'btn--danger' : ''}`}
          onClick={() => {
            setToggling(a);
            setStatus.reset();
          }}
        >
          {a.enabled ? 'Deactivate' : 'Reactivate'}
        </button>
      ),
    },
  ];

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">Staff accounts</h1>
          <p className="page-subtitle">
            Registrars, librarians, and course administrators. A disabled account cannot sign in.
          </p>
        </div>
        <button type="button" className="btn btn--primary" onClick={() => setCreating(true)}>
          Create staff account
        </button>
      </div>

      <div className="card">
        <ErrorBanner error={list.error} />

        <DataTable
          columns={columns}
          rows={list.content}
          rowKey={(a) => a.id}
          loading={list.loading}
          empty={
            <EmptyState
              title="No staff accounts yet"
              description="Create one to get started. Demo accounts seeded at startup appear here too."
            />
          }
        />

        <Pagination
          page={list.page}
          totalPages={list.totalPages}
          totalElements={list.totalElements}
          onPageChange={list.setPage}
        />
      </div>

      {creating && (
        <Modal
          title="Create staff account"
          onClose={() => setCreating(false)}
          footer={
            <>
              <button type="button" className="btn" onClick={() => setCreating(false)}>
                Cancel
              </button>
              <button
                type="submit"
                form="staff-form"
                className="btn btn--primary"
                disabled={create.pending}
              >
                {create.pending ? 'Creating…' : 'Create'}
              </button>
            </>
          }
        >
          <form id="staff-form" onSubmit={onCreate}>
            <ErrorBanner error={create.error} />

            <Field
              label="Username"
              name="username"
              value={username}
              onChange={setUsername}
              error={create.error?.fieldError?.('username')}
              autoFocus
              required
            />

            {/* SYSTEM_ADMINISTRATOR and STUDENT are rejected by Role.STAFF_ROLES, so they are
                never offered here. */}
            <Field label="Role" name="role" error={create.error?.fieldError?.('role')}>
              <select
                className="field__input"
                value={role}
                onChange={(e) => setRole(e.target.value)}
              >
                {STAFF_ROLES.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </select>
            </Field>
          </form>
        </Modal>
      )}

      {/* The initial password is returned exactly once, at creation, and is never re-readable. */}
      {created && (
        <ConfirmDialog
          title="Staff account created"
          danger={false}
          confirmLabel="Done"
          onConfirm={() => setCreated(null)}
          onCancel={() => setCreated(null)}
          message={
            <span>
              <div className="credential">
                <div className="dl">
                  <span className="dl__key">Username</span>
                  <span className="mono">{created.username}</span>
                  <span className="dl__key">Role</span>
                  <span>{created.role}</span>
                  <span className="dl__key">Initial password</span>
                  <span className="credential__value">{created.initialPassword}</span>
                </div>
              </div>
              <p className="muted" style={{ marginTop: 'var(--s-3)', fontSize: 'var(--t-sm)' }}>
                Copy it now — this is the only time it is shown. They will be forced to change it at
                first login.
              </p>
            </span>
          }
        />
      )}

      {toggling && (
        <ConfirmDialog
          title={toggling.enabled ? 'Deactivate account' : 'Reactivate account'}
          message={
            toggling.enabled
              ? `Deactivate ${toggling.username}? They will no longer be able to sign in.`
              : `Reactivate ${toggling.username}? They will be able to sign in again.`
          }
          confirmLabel={toggling.enabled ? 'Deactivate' : 'Reactivate'}
          danger={toggling.enabled}
          onConfirm={onToggle}
          onCancel={() => {
            setToggling(null);
            setStatus.reset();
          }}
          pending={setStatus.pending}
          error={setStatus.error}
        />
      )}
    </>
  );
}
