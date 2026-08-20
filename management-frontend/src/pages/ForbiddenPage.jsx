import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { landingRoute } from '../auth/permissions.js';

export default function ForbiddenPage({ message }) {
  const { session } = useAuth();

  return (
    <div className="card">
      <h1 className="page-title">Not permitted</h1>
      <p className="page-subtitle">
        {message ?? "You don't have permission for this action."}
      </p>
      {session && (
        <p style={{ marginTop: 'var(--s-4)' }}>
          <Link to={landingRoute(session.role)}>Back to your home page</Link>
        </p>
      )}
    </div>
  );
}
