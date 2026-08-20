export default function EmptyState({ title = 'Nothing here', description, action }) {
  return (
    <div className="empty-state">
      <div className="empty-state__title">{title}</div>
      {description && <p>{description}</p>}
      {action && <div style={{ marginTop: 'var(--s-4)' }}>{action}</div>}
    </div>
  );
}
