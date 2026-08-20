/** `page` is 0-based, matching the backend's PageResponse. */
export default function Pagination({ page, totalPages, totalElements, onPageChange }) {
  if (!totalPages || totalPages <= 1) {
    return totalElements > 0 ? (
      <div className="pagination">
        <span>
          {totalElements} {totalElements === 1 ? 'result' : 'results'}
        </span>
      </div>
    ) : null;
  }

  return (
    <div className="pagination">
      <span>
        Page {page + 1} of {totalPages} · {totalElements} results
      </span>
      <div className="btn-row">
        <button
          type="button"
          className="btn btn--sm"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
        >
          Previous
        </button>
        <button
          type="button"
          className="btn btn--sm"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
        >
          Next
        </button>
      </div>
    </div>
  );
}
