// bench/lib/session.js — the one piece the plan calls not optional (02-benchmark-plan.md §1.1).
//
// Authentication here is a server-side session keyed by a JSESSIONID cookie, not a bearer token
// (04-authentication-authorization.md). That has one consequence this module exists to enforce:
// a scenario MUST call login() once per virtual user — from setup() or a guarded VU-init block —
// and never inside the iteration loop. A login costs a real BCrypt verification (hazard H5); a
// scenario that re-authenticates every iteration is measuring H5 no matter which endpoint it
// names, and its numbers are meaningless for anything else. This module cannot enforce that from
// the outside, only document it — the discipline belongs to the scenario file that calls it.
//
// k6 maintains a per-VU cookie jar automatically, so once login() succeeds, JSESSIONID rides along
// on every subsequent http.* call made by that VU without any extra code. assertLive() exists
// because that session can still expire mid-run, and a silently-expired session turns a latency
// benchmark into a 401 benchmark — the SLOs' error-rate threshold only catches that if something
// actually checks and fails loudly.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, credentialsFor } from './config.js';

// One cheap, already-authorized GET per role (SecurityConfig's RBAC matcher list), used purely to
// confirm a session is still live -- not a benchmark scenario in its own right.
const LIVENESS_PATH = {
  STUDENT: '/api/v1/me/profile',
  REGISTRAR: '/api/v1/students',
  LIBRARIAN: '/api/v1/books',
  COURSE_ADMINISTRATOR: '/api/v1/courses',
  SYSTEM_ADMINISTRATOR: '/api/v1/staff-accounts',
};

/**
 * Log in with an explicit username/password and return `{ username }`. Factored out of login()
 * for scenarios that need a specific account rather than "the" account for a role -- BM-ME-*
 * needs 20 *distinct* STUDENT logins (one per VU) drawn from the seeded account cohort
 * (bench/seed/manifest.js), which credentialsFor()'s single-username-per-role model can't express.
 */
export function loginAs(username, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  );

  const ok = check(res, { 'login succeeded': (r) => r.status === 200 });
  if (!ok) {
    throw new Error(`login as ${username} failed: HTTP ${res.status} ${res.body}`);
  }

  return { username };
}

/**
 * Log in as `role` and return a session handle to pass to assertLive(). Call once per VU.
 */
export function login(role) {
  const { username, password } = credentialsFor(role);
  const { username: loggedInAs } = loginAs(username, password);
  return { role, username: loggedInAs };
}

/**
 * Confirm the session from login() is still authenticated. Fails loudly (throws) rather than
 * letting a scenario silently keep hitting an endpoint as an anonymous/expired caller.
 */
export function assertLive(session) {
  const path = LIVENESS_PATH[session.role];
  const res = http.get(`${BASE_URL}${path}`, { tags: { name: 'liveness' } });

  const ok = check(res, { 'session still authenticated': (r) => r.status === 200 });
  if (!ok) {
    throw new Error(
      `session for ${session.username} (${session.role}) is no longer live: ` +
        `HTTP ${res.status} on ${path}.`,
    );
  }
}
