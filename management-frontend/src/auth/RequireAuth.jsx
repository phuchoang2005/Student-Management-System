import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext.jsx';
import { can } from './permissions.js';
import ForbiddenPage from '../pages/ForbiddenPage.jsx';

/**
 * Three rules, applied in order:
 *   1. no session                        -> /login
 *   2. mustChangePassword, off that page -> /change-password
 *   3. capability not held               -> ForbiddenPage
 *
 * Rule 2 mirrors MustChangePasswordFilter, which 403s every URI except /api/v1/auth/password. It
 * exists so the forced-change flow feels like a flow rather than a wall of failed requests; the
 * server enforcement is still the real guarantee.
 */
export default function RequireAuth({ capability, children }) {
  const { session } = useAuth();
  const location = useLocation();

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (session.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }

  if (capability && !can(session.role, capability)) {
    return <ForbiddenPage />;
  }

  return children;
}
