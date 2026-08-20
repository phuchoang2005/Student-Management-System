import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { books } from '../../api/endpoints.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { can } from '../../auth/permissions.js';
import usePagedResource from '../../hooks/usePagedResource.js';
import useAsyncAction from '../../hooks/useAsyncAction.js';
import DataTable from '../../components/DataTable.jsx';
import Pagination from '../../components/Pagination.jsx';
import ErrorBanner from '../../components/ErrorBanner.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import ConfirmDialog from '../../components/ConfirmDialog.jsx';
import Modal from '../../components/Modal.jsx';
import Field from '../../components/Field.jsx';
import Badge from '../../components/Badge.jsx';
import BookFormModal from './BookFormModal.jsx';
import { useToast } from '../../components/Toast.jsx';

/** UC-14 (search), UC-4/5/6/7 (add, assign, unassign, remove) for a Librarian. */
export default function BookListPage() {
  const { session } = useAuth();
  const toast = useToast();
  const writable = can(session.role, 'books:write');

  const fetcher = useCallback((query, page, size) => books.search(query, page, size), []);
  const list = usePagedResource(fetcher);

  const [creating, setCreating] = useState(false);
  const [assigning, setAssigning] = useState(null);
  const [studentId, setStudentId] = useState('');
  const [deleting, setDeleting] = useState(null);

  const assign = useAsyncAction(books.assignOwner);
  const unassign = useAsyncAction(books.clearOwner);
  const remove = useAsyncAction(books.remove);

  const onAssign = async (e) => {
    e.preventDefault();
    try {
      await assign.run(assigning.isbn, Number(studentId));
      toast.show(`Assigned ${assigning.isbn}.`);
      setAssigning(null);
      setStudentId('');
      list.refetch();
    } catch {
      // assign.error renders in the modal.
    }
  };

  const onUnassign = async (book) => {
    try {
      await unassign.run(book.isbn);
      toast.show(`Ownership cleared for ${book.isbn}.`);
      list.refetch();
    } catch (err) {
      toast.show(err.message, 'error');
    }
  };

  const onDelete = async () => {
    try {
      await remove.run(deleting.isbn);
      toast.show(`Removed ${deleting.isbn}.`);
      setDeleting(null);
      list.refetch();
    } catch {
      // remove.error renders in the dialog.
    }
  };

  const columns = [
    {
      key: 'isbn',
      header: 'ISBN',
      className: 'mono',
      render: (b) => <Link to={`/books/${encodeURIComponent(b.isbn)}`}>{b.isbn}</Link>,
    },
    { key: 'title', header: 'Title' },
    { key: 'author', header: 'Author' },
    {
      key: 'ownerId',
      header: 'Owner',
      render: (b) =>
        b.ownerId ? (
          <Badge variant="success">student #{b.ownerId}</Badge>
        ) : (
          <Badge variant="neutral">unowned</Badge>
        ),
    },
    ...(writable
      ? [
          {
            key: 'actions',
            header: '',
            className: 'is-actions',
            render: (b) => (
              <div className="btn-row btn-row--end">
                <button
                  type="button"
                  className="btn btn--sm"
                  onClick={() => {
                    setAssigning(b);
                    setStudentId(b.ownerId ? String(b.ownerId) : '');
                    assign.reset();
                  }}
                >
                  Assign
                </button>
                <button
                  type="button"
                  className="btn btn--sm"
                  onClick={() => onUnassign(b)}
                  disabled={!b.ownerId || unassign.pending}
                >
                  Unassign
                </button>
                <button type="button" className="btn btn--sm btn--danger" onClick={() => setDeleting(b)}>
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
          <h1 className="page-title">Books</h1>
          <p className="page-subtitle">
            {session.role === 'STUDENT'
              ? 'Scoped server-side to the books you own.'
              : 'Search by ISBN, title, or author.'}
          </p>
        </div>
        {writable && (
          <button type="button" className="btn btn--primary" onClick={() => setCreating(true)}>
            Add book
          </button>
        )}
      </div>

      <div className="card">
        <input
          className="search-input"
          placeholder="Search books…"
          value={list.query}
          onChange={(e) => list.setQuery(e.target.value)}
          style={{ marginBottom: 'var(--s-4)' }}
        />

        <ErrorBanner error={list.error} />

        <DataTable
          columns={columns}
          rows={list.content}
          rowKey={(b) => b.id}
          loading={list.loading}
          empty={<EmptyState title="No books found" description="Try a different search." />}
        />

        <Pagination
          page={list.page}
          totalPages={list.totalPages}
          totalElements={list.totalElements}
          onPageChange={list.setPage}
        />
      </div>

      {creating && (
        <BookFormModal
          onClose={() => setCreating(false)}
          onCreated={(book) => {
            setCreating(false);
            toast.show(`Added ${book.isbn}.`);
            list.refetch();
          }}
        />
      )}

      {assigning && (
        <Modal
          title={`Assign ${assigning.isbn}`}
          onClose={() => setAssigning(null)}
          footer={
            <>
              <button type="button" className="btn" onClick={() => setAssigning(null)}>
                Cancel
              </button>
              <button
                type="submit"
                form="assign-form"
                className="btn btn--primary"
                disabled={assign.pending}
              >
                {assign.pending ? 'Assigning…' : 'Assign'}
              </button>
            </>
          }
        >
          <form id="assign-form" onSubmit={onAssign}>
            <ErrorBanner error={assign.error} />
            <Field
              label="Student id"
              name="studentId"
              type="number"
              value={studentId}
              onChange={setStudentId}
              error={assign.error?.fieldError?.('studentId')}
              hint="A numeric student id — find it on the student's detail page."
              autoFocus
              required
            />
          </form>
        </Modal>
      )}

      {deleting && (
        <ConfirmDialog
          title="Remove book"
          message={`Remove ${deleting.isbn} — ${deleting.title}?`}
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
