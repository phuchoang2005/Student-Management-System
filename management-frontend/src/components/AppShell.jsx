import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { NAV_ITEMS, can } from '../auth/permissions.js';
import { RoleBadge } from './Badge.jsx';

export default function AppShell() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  // Role-filtered from the capability map, so the nav is itself a view of the RBAC rules --
  // a sysadmin sees exactly two items, which is the allow-list made visible.
  const items = NAV_ITEMS.filter((item) => can(session?.role, item.capability));

  const onLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar__brand">Student Management</div>
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => `sidebar__link${isActive ? ' is-active' : ''}`}
          >
            {item.label}
          </NavLink>
        ))}
      </aside>

      <div className="main">
        <header className="topbar">
          {session?.username && <span className="topbar__user">{session.username}</span>}
          {session?.role && <RoleBadge role={session.role} />}
          <button type="button" className="btn btn--sm" onClick={onLogout}>
            Log out
          </button>
        </header>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
