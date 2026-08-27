// bench/lib/vuSession.js — the login-once-per-VU guard (02-benchmark-plan.md §1.1).
//
// k6's setup() runs in its own isolated VU whose cookies are never shared with the load VUs that
// run exec functions -- logging in inside setup() would leave every load VU anonymous, silently.
// A k6 scenario file's module-level code instead runs once per VU (not once per iteration), so a
// module-scoped variable here is effectively per-VU state -- this is what session.js's own doc
// comment means by "from setup() or a guarded init block."
//
// One instance of this module's state exists per VU (k6 gives each VU its own copy of every
// imported module), so a single top-level `let session` below is safe without any VU-id keying.
// buildOptions() runs a file's BM-* entries sequentially, and k6 recycles the same VU slots (and
// therefore the same module state) across those entries rather than starting fresh ones -- which
// is exactly what makes "login once, reuse for the whole run" work across an entire file, not just
// within one entry's iterations.
//
// The one thing that model doesn't handle for free: a VU's cookie jar can hold exactly one
// JSESSIONID at a time, so a *role switch* within the same file (e.g. writes.js: REGISTRAR for
// BM-STU-006/007, then LIBRARIAN for BM-BK-005, then COURSE_ADMINISTRATOR for BM-CRS-004) requires
// a real re-login, not just a liveness check against the stale cached role -- a liveness check
// alone would keep passing (the old session is still live) while every subsequent request under
// the new role silently 403s. `ensureLoggedIn`/`ensureLoggedInAs` compare the requested identity
// against what's cached and only skip the login round trip when they actually match.

import { login, loginAs, assertLive } from './session.js';

let session = null; // { role, username } | null

/**
 * Log in as `role` the first time it's requested, or whenever the requested role differs from
 * whichever identity this VU is currently holding; otherwise just re-asserts liveness.
 */
export function ensureLoggedIn(role) {
  if (session !== null && session.role === role) {
    assertLive(session);
    return session;
  }
  session = login(role);
  return session;
}

/**
 * Same guarantee as ensureLoggedIn(), but for an explicit account rather than a role's fixed
 * credentials -- BM-ME-* needs each VU to log in as a *distinct* cohort student
 * (bench/lib/manifest.js supplies the cohort list), cycling through it by VU id.
 */
export function ensureLoggedInAs(role, username, password) {
  if (session !== null && session.role === role && session.username === username) {
    assertLive(session);
    return session;
  }
  const { username: loggedInAs } = loginAs(username, password);
  session = { role, username: loggedInAs };
  return session;
}
