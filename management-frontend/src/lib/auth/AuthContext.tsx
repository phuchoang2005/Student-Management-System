'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import { onSessionExpired } from '@/lib/api/client';
import { auth, logoutEndpoint } from '@/lib/api/endpoints';
import type { Role } from '@/lib/api/types';

/**
 * Client-side auth state is the source of truth for *rendering* decisions.
 *
 * The backend has no session-probe endpoint, and an unauthenticated request returns a bare 403
 * rather than a 401, so the client cannot ask "who am I?". What it can do is remember the login
 * response and mirror it to sessionStorage so a refresh survives.
 *
 * When the server session goes away — it expired, or a System Administrator ended it — the next
 * request answers 401 and `client.ts` calls back into here to clear the mirror, which drops the user
 * to /login rather than leaving them on a screen whose every request now fails.
 */

const STORAGE_KEY = 'management.session';

export interface Session {
  role: Role;
  username: string;
  mustChangePassword: boolean;
}

interface AuthValue {
  session: Session | null;
  /** False until the stored session has been read — the first render happens on the server. */
  ready: boolean;
  login: (username: string, password: string) => Promise<Session>;
  logout: () => Promise<void>;
  clearMustChange: () => void;
  clearSession: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

function readStoredSession(): Session | null {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

function writeStoredSession(session: Session | null): void {
  try {
    if (session) window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // A demo running with storage disabled still works; it just won't survive a refresh.
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // Starts empty on both sides. Next renders this tree on the server first, so `ready` stays false
  // until the client has actually looked at sessionStorage.
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);

  // Deliberately an effect and not a read during render. `sessionStorage` only exists in the
  // browser, so reading it while rendering makes the client's first committed tree differ from the
  // server's (which always has `ready = false`) and hydration fails. The pre-hydration frame is the
  // spinner `RequireAuth` renders whenever `!ready` -- the same markup the server sent -- and its
  // own redirect effect is gated on `ready`, so nobody sees a flash of the login screen.
  useEffect(() => {
    setSession(readStoredSession());
    setReady(true);
  }, []);

  const persist = useCallback((next: Session | null) => {
    writeStoredSession(next);
    setSession(next);
  }, []);

  // Registered once, for the lifetime of the provider. `client.ts` owns no auth state of its own,
  // so it calls back here instead of importing this module (which would be a cycle).
  useEffect(() => {
    onSessionExpired(() => persist(null));
    return () => onSessionExpired(null);
  }, [persist]);

  const login = useCallback(
    async (username: string, password: string) => {
      const res = await auth.login(username, password);
      const next: Session = {
        role: res.role,
        mustChangePassword: res.mustChangePassword,
        username,
      };
      persist(next);
      return next;
    },
    [persist],
  );

  /**
   * Fire-and-forget. `POST /logout` is not part of the API contract, and the must-change-password
   * gate 403s it for a user who hasn't changed their password — so its result is ignored and the
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
   * Called after a successful `POST /auth/password`. The backend clears the flag on the live
   * session at the same moment (`AuthController` rewrites the session principal), so neither side
   * needs a re-login.
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

  const value = useMemo<AuthValue>(
    () => ({ session, ready, login, logout, clearMustChange, clearSession }),
    [session, ready, login, logout, clearMustChange, clearSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside an AuthProvider');
  return ctx;
}
