// bench/scenarios/auth-login.js — BM-IDN-001/002/003 (03-benchmark-scenarios.md §6, PM-035).
//
// BM-IDN-001 is a ramp -- 1, 10, 25, 50, 100 VUs, held at each step -- expressed as five distinct
// k6 scenario entries (BM_IDN_001_VU01..VU100) rather than one k6 `ramping-vus` executor, so
// bench/report.js can render each step's own p95/p99 instead of one number for the whole climb;
// the deliverable is the knee of the curve, not a single percentile. **This whole file's ramp
// entries must run alone** -- nothing else on the host, nothing else in the same k6 invocation --
// since the point is finding where login concurrency itself saturates, and anything sharing the
// run measures contention instead. `make bench-auth-ramp` pins that via BM_ONLY; a plain
// `make bench SCENARIO=auth-login` runs everything in this file (ramp + BM-IDN-002 + BM-IDN-003)
// for convenience, which is fine for a dev smoke test but not for a number anyone should trust.
//
// The ramp entries and BM-IDN-002 call POST /auth/login directly, every iteration -- the one
// deliberate exception to "login once per VU, never per iteration" (bench/lib/session.js's header
// comment): the login call itself is what's under test here, not an endpoint behind it. BM-IDN-003
// is an ordinary authenticated read and uses the normal vuSession guard.
//
// No shared warm-up: nothing here needs pre-discovered data, and the ramp entries especially must
// start cold.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, credentialsFor } from '../lib/config.js';
import { buildOptions } from '../lib/runner.js';
import { ensureLoggedIn } from '../lib/vuSession.js';
import { mergeThresholds, sloThresholds } from '../lib/slo.js';
import { uniqueCode } from '../lib/vuShard.js';

export const options = buildOptions(null, [
  { id: 'BM_IDN_001_VU01', exec: 'bmIdn001Vu01', vus: 1 },
  { id: 'BM_IDN_001_VU10', exec: 'bmIdn001Vu10', vus: 10 },
  { id: 'BM_IDN_001_VU25', exec: 'bmIdn001Vu25', vus: 25 },
  { id: 'BM_IDN_001_VU50', exec: 'bmIdn001Vu50', vus: 50 },
  { id: 'BM_IDN_001_VU100', exec: 'bmIdn001Vu100', vus: 100 },
  { id: 'BM_IDN_002', exec: 'bmIdn002', vus: 20 },
  { id: 'BM_IDN_003', exec: 'bmIdn003', vus: 10 },
]);

// BM-IDN-002 also fires a companion call tagged `name:BM_IDN_002_UNKNOWN_USER` for the known-vs-
// unknown-username timing comparison (03-benchmark-scenarios.md §6: a measurable delta there is a
// user-enumeration finding for the security channel, not this one -- read manually off the two
// numbers in this file's export, there's no automated threshold for a timing *delta*). This merges
// in the same permissive-submetric trick bench/lib/runner.js uses so the tag exists in the export
// at all.
options.thresholds = mergeThresholds(
  options.thresholds,
  sloThresholds('BM_IDN_002_UNKNOWN_USER', 'LOGIN', 'name'),
);
// Every request BM-IDN-002 makes is *supposed* to come back 401 -- a wrong password being
// rejected is success for this scenario, not a failure the LOGIN class' error-rate threshold
// (inherited from buildOptions()/the merge above) should judge it against. Caught by a smoke
// test: without this override both the main and unknown-user thresholds breach on every run,
// 100% of the time, regardless of anything actually being wrong. Keep the duration half of the
// threshold -- rejection latency is exactly the H5 number this scenario measures -- and only
// neutralize the error-rate half.
options.thresholds['http_req_failed{scenario:BM_IDN_002}'] = ['rate>=0'];
options.thresholds['http_req_failed{name:BM_IDN_002_UNKNOWN_USER}'] = ['rate>=0'];

function loginOnce(tagName) {
  const { username, password } = credentialsFor('REGISTRAR');
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ username, password }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: tagName },
  });
  check(res, { [`${tagName} status 200`]: (r) => r.status === 200 });
}

export function bmIdn001Vu01() {
  loginOnce('BM_IDN_001_VU01');
}
export function bmIdn001Vu10() {
  loginOnce('BM_IDN_001_VU10');
}
export function bmIdn001Vu25() {
  loginOnce('BM_IDN_001_VU25');
}
export function bmIdn001Vu50() {
  loginOnce('BM_IDN_001_VU50');
}
export function bmIdn001Vu100() {
  loginOnce('BM_IDN_001_VU100');
}

export function bmIdn002() {
  const { username } = credentialsFor('REGISTRAR');
  const wrongRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username, password: 'DefinitelyWrongPassword!' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'BM_IDN_002' } },
  );
  check(wrongRes, { 'BM-IDN-002 status 401': (r) => r.status === 401 });

  const unknownRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: `nobody-${uniqueCode('U')}`, password: 'DefinitelyWrongPassword!' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'BM_IDN_002_UNKNOWN_USER' } },
  );
  check(unknownRes, { 'BM-IDN-002 unknown-user status 401': (r) => r.status === 401 });
}

export function bmIdn003() {
  ensureLoggedIn('SYSTEM_ADMINISTRATOR');
  const res = http.get(`${BASE_URL}/api/v1/staff-accounts?page=0&size=20`, {
    tags: { name: 'BM_IDN_003' },
  });
  check(res, {
    'BM-IDN-003 status 200': (r) => r.status === 200,
    'BM-IDN-003 has content array': (r) => Array.isArray(JSON.parse(r.body).content),
  });
}
