import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { auth, logoutEndpoint } from '../api/endpoints.js';

/**
 * Client-side auth state is the source of truth for *rendering* decisions.
 *
 * The backend has no session-probe endpoint, and an unauthenticated request returns a bare 403
 * rather than a 401, so the client cannot ask "who am I?". What it can do is remember the login
 * response and mirror it to sessionStorage so a refresh survives.
 *
 * Known limit, accepted for a demo: if the server session expires while sessionStorage still holds
 * state, the first 403 clears it and drops the user back to /login.
 */

const STORAGE_KEY = 'management.session';

const AuthContext = createContext(null);

function readStoredSession() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function writeStoredSession(session) {
  try {
    if (session) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // A demo running with storage disabled still works; it just won't survive a refresh.
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(readStoredSession);

  const persist = useCallback((next) => {
    writeStoredSession(next);
    setSession(next);
  }, []);

  const login = useCallback(
    async (username, password) => {
      const res = await auth.login(username, password);
      const next = { role: res.role, mustChangePassword: res.mustChangePassword, username };
      persist(next);
      return next;
    },
    [persist],
  );

  /**
   * Fire-and-forget. POST /logout is not part of the API contract, and the must-change-password
   * gate 403s it for a user who hasn't changed their password -- so its result is ignored and the
   * local state is cleared regardless. Client state is authoritative here.
   */
  const logout = useCallback(async () => {
    try {
      await logoutEndpoint();
    } catch {
      // Intentionally ignored.
    }
    persist(null);
  }, [persist]);

  /**
   * Called after a successful POST /auth/password. The backend clears the flag on the live session
   * at the same moment (AuthController rewrites the session principal), so neither side needs a
   * re-login.
   */
  const clearMustChange = useCallback(() => {
    setSession((current) => {
      if (!current) return current;
      const next = { ...current, mustChangePassword: false };
      writeStoredSession(next);
      return next;
    });
  }, []);

  /** Used when a 403 reveals the stored session is stale. */
  const clearSession = useCallback(() => persist(null), [persist]);

  const value = useMemo(
    () => ({ session, login, logout, clearMustChange, clearSession }),
    [session, login, logout, clearMustChange, clearSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside an AuthProvider');
  return ctx;
}
