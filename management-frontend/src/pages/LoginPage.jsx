import { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { auth } from '../api/endpoints.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { landingRoute } from '../auth/permissions.js';
import useAsyncAction from '../hooks/useAsyncAction.js';
import Field from '../components/Field.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';

/** UC-21. The only screen that sees a 401 as a normal outcome. */
export default function LoginPage() {
  const { session, login } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [demoAccounts, setDemoAccounts] = useState([]);

  const submit = useAsyncAction(login);

  // PM-017 -- the seeded accounts are listed live rather than hardcoded here. The endpoint is
  // conditional on app.demo-accounts.enabled and 404s in the `prod` profile, so failure is silent.
  useEffect(() => {
    auth
      .demoAccounts()
      .then(setDemoAccounts)
      .catch(() => setDemoAccounts([]));
  }, []);

  if (session) {
    return (
      <Navigate
        to={session.mustChangePassword ? '/change-password' : landingRoute(session.role)}
        replace
      />
    );
  }

  const onSubmit = async (e) => {
    e.preventDefault();
    try {
      const next = await submit.run(username, password);
      navigate(next.mustChangePassword ? '/change-password' : landingRoute(next.role), {
        replace: true,
      });
    } catch {
      // submit.error is already set and rendered below.
    }
  };

  const useDemoAccount = (account) => {
    setUsername(account.username);
    setPassword(account.password);
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-card__form">
          <h1 className="page-title">Sign in</h1>
          <p className="page-subtitle" style={{ marginBottom: 'var(--s-5)' }}>
            Student Management demo
          </p>

          <form onSubmit={onSubmit}>
            <ErrorBanner error={submit.error} />

            <Field
              label="Username"
              name="username"
              value={username}
              onChange={setUsername}
              autoFocus
              required
            />
            <Field
              label="Password"
              name="password"
              type="password"
              value={password}
              onChange={setPassword}
              required
            />

            <button type="submit" className="btn btn--primary" disabled={submit.pending}>
              {submit.pending ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        </div>

        <aside className="login-card__demo">
          <div className="section-title">Demo accounts</div>
          {demoAccounts.length === 0 && (
            <p className="muted" style={{ fontSize: 'var(--t-sm)' }}>
              Not available. The backend exposes these only when
              <code> app.demo-accounts.enabled</code> is true.
            </p>
          )}

          {demoAccounts.map((account) => {
            // The STUDENT demo account is advertised by the endpoint but skipped by the seeder --
            // a student-role user needs a real students row to satisfy the FK co-invariant, so
            // logging in as it fails until a student is registered by hand.
            const unseeded = account.role === 'STUDENT';
            return (
              <button
                key={account.username}
                type="button"
                className="demo-chip"
                onClick={() => useDemoAccount(account)}
                disabled={unseeded}
                title={unseeded ? 'Listed by the API but never seeded' : 'Fill the form'}
              >
                <strong>{account.role}</strong>
                <span className="demo-chip__user">{account.username}</span>
                {unseeded && (
                  <span className="demo-chip__note">not seeded — register a student instead</span>
                )}
              </button>
            );
          })}
        </aside>
      </div>
    </div>
  );
}
