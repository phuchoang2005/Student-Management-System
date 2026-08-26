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

import { login, loginAs, assertLive } from './session.js';

let session = null;

/**
 * Log in as `role` on this VU's first call (using the fixed per-role credentials from
 * bench/lib/config.js); every later call -- any iteration, any BM-* scenario in the same file --
 * reuses the same session and only re-asserts liveness.
 */
export function ensureLoggedIn(role) {
  if (session === null) {
    session = login(role);
  } else {
    assertLive(session);
  }
  return session;
}

/**
 * Same guarantee as ensureLoggedIn(), but for an explicit account rather than a role's fixed
 * credentials -- BM-ME-* needs each VU to log in as a *distinct* cohort student
 * (bench/lib/manifest.js supplies the cohort list), cycling through it by VU id.
 */
export function ensureLoggedInAs(role, username, password) {
  if (session === null) {
    const { username: loggedInAs } = loginAs(username, password);
    session = { role, username: loggedInAs };
  } else {
    assertLive(session);
  }
  return session;
}
