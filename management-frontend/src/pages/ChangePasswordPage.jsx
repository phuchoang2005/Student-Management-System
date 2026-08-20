import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { auth } from '../api/endpoints.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { landingRoute } from '../auth/permissions.js';
import useAsyncAction from '../hooks/useAsyncAction.js';
import Field from '../components/Field.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import { useToast } from '../components/Toast.jsx';

/**
 * UC-22, and the client half of the forced-change gate.
 *
 * Worth knowing: a wrong `currentPassword` comes back as 401, not 400 -- the only use of 401
 * outside login. It must be rendered inline here. Treating it as an expired session and redirecting
 * to /login would trap a first-login user in a loop, since this is the one page they can reach.
 */
export default function ChangePasswordPage() {
  const { session, clearMustChange } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [retypeNewPassword, setRetypeNewPassword] = useState('');

  const submit = useAsyncAction(auth.changePassword);
  const forced = Boolean(session?.mustChangePassword);

  const onSubmit = async (e) => {
    e.preventDefault();
    try {
      await submit.run(currentPassword, newPassword, retypeNewPassword);
      // The backend clears the flag on the live session at the same moment, so no re-login.
      clearMustChange();
      toast.show('Password changed.');
      navigate(landingRoute(session.role), { replace: true });
    } catch {
      // submit.error is rendered below.
    }
  };

  const err = submit.error;
  const wrongCurrent = err?.status === 401;

  return (
    <div className="card" style={{ maxWidth: 520, margin: '0 auto' }}>
      <h1 className="page-title">Change password</h1>
      {forced && (
        <div className="banner banner--warning" style={{ marginTop: 'var(--s-4)' }}>
          Your account still uses its initial password. You must change it before anything else
          becomes available.
        </div>
      )}

      <form onSubmit={onSubmit} style={{ marginTop: 'var(--s-4)' }}>
        {/* A 401 here means "wrong current password", so it's shown on the field, not as a banner. */}
        {!wrongCurrent && <ErrorBanner error={err} />}

        <Field
          label="Current password"
          name="currentPassword"
          type="password"
          value={currentPassword}
          onChange={setCurrentPassword}
          error={wrongCurrent ? 'That is not your current password.' : err?.fieldError?.('currentPassword')}
          autoFocus
          required
        />
        <Field
          label="New password"
          name="newPassword"
          type="password"
          value={newPassword}
          onChange={setNewPassword}
          error={err?.fieldError?.('newPassword')}
          hint="8–72 characters, and different from the current one."
          required
        />
        <Field
          label="Retype new password"
          name="retypeNewPassword"
          type="password"
          value={retypeNewPassword}
          onChange={setRetypeNewPassword}
          error={err?.fieldError?.('retypeNewPassword')}
          required
        />

        <button type="submit" className="btn btn--primary" disabled={submit.pending}>
          {submit.pending ? 'Saving…' : 'Change password'}
        </button>
      </form>
    </div>
  );
}
