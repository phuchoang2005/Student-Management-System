import EmptyState from './EmptyState.jsx';

/**
 * `columns` is `[{ key, header, render?, className? }]`.
 *
 * No sortable headers by design: every list endpoint accepts a `sort` param but ignores it -- each
 * repository query has a fixed ORDER BY -- so a sort control would be a dead one.
 */
export default function DataTable({ columns, rows, rowKey, loading, empty }) {
  if (loading) {
    return <div className="spinner-text">Loading…</div>;
  }

  if (!rows || rows.length === 0) {
    return empty ?? <EmptyState title="Nothing to show" />;
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key} className={col.className}>
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((col) => (
                <td key={col.key} className={col.className}>
                  {col.render ? col.render(row) : row[col.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
