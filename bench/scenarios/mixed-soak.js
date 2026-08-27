// bench/scenarios/mixed-soak.js — BM-XC-002 + BM-IDN-004 (03-benchmark-scenarios.md §7-§6, PM-036).
//
// 30 minutes (SOAK_DURATION, default '30m') of moderate, constant, role-partitioned mixed traffic:
// each of REGISTRAR/LIBRARIAN/COURSE_ADMINISTRATOR/STUDENT gets its own executor, and every VU in
// that executor only ever touches that one role -- a VU whose cookie jar switched roles mid-run is
// exactly the bug fixed in bench/lib/vuSession.js, and role-partitioning sidesteps it entirely
// rather than relying on that fix under sustained concurrent load. REGISTRAR/LIBRARIAN mix ~80%
// reads / ~20% writes internally; COURSE_ADMINISTRATOR/STUDENT are read-only (no write endpoint
// available to either that's safe to hammer for 30 minutes -- course delete is destructive,
// students have none). A dedicated BM_XC_002_LOGINS executor supplies the remaining ~10% of
// traffic as repeated logins; overall the mix lands close to the spec's ~70/20/10 split. A
// dedicated BM-IDN-004 executor (GET /api/v1/sessions as SYSTEM_ADMINISTRATOR) runs the whole time
// too -- that scenario's entire point is running *during* this soak, while many sessions are live.
//
// The read paths are reused directly from their own PM-033 files by import (bmStu002/bmBk001/
// bmCrs001/bmMe002) -- safe because each only depends on module-top-level state populated at
// import (search terms, manifest data), not on that file's own warmup() being called. The write
// paths are NOT reused from writes.js the same way: BM-STU-007/BM-BK-005's exec functions there
// depend on target lists populated inside writes.js's own warmup(), so importing just the exec
// function without also driving that warmup is fragile -- this file instead primes its own tiny
// per-VU target (one student code / one ISBN, discovered on first use) and tags its calls with the
// same BM-STU-007/BM-BK-005 ids so the numbers stay comparable.
//
// No shared warm-up scenario (would eat into the 30-minute window); every executor primes
// whatever it needs on its own first iteration instead.
//
// Reused-function requests are tagged `scenario:<executor>` (k6's auto-tag) but keep their own
// `name:BM_STU_002` etc. tag from the original file -- bench/lib/slo.js's `dimension` parameter is
// what lets this file's thresholds key on `name:` instead of `scenario:` for those ids.
// bench-report's one-row-per-(file,id) model doesn't fit a 30-minute soak, so this scenario isn't
// listed in bench/lib/scenarios.js's SCENARIO_FILES -- read its numbers from the raw
// --summary-export JSON, and heap-bytes-per-session (H7, the actual BM-XC-002 deliverable) from
// bench/monitor-soak.js's separate output, run alongside this file.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, credentialsFor } from '../lib/config.js';
import { ensureLoggedIn } from '../lib/vuSession.js';
import { mergeThresholds, sloThresholds } from '../lib/slo.js';
import { bmStu002 } from './student-search.js';
import { bmBk001 } from './book-search.js';
import { bmCrs001 } from './course-list.js';
import { bmMe002 } from './me-reads.js';

const SOAK_DURATION = __ENV.SOAK_DURATION || '30m';
const WRITE_SHARE = 0.2;

export const options = {
  scenarios: {
    registrar_mix: {
      executor: 'constant-vus',
      vus: 8,
      duration: SOAK_DURATION,
      exec: 'registrarExec',
      startTime: '0s',
    },
    librarian_mix: {
      executor: 'constant-vus',
      vus: 6,
      duration: SOAK_DURATION,
      exec: 'librarianExec',
      startTime: '0s',
    },
    course_admin_mix: {
      executor: 'constant-vus',
      vus: 4,
      duration: SOAK_DURATION,
      exec: 'courseAdminExec',
      startTime: '0s',
    },
    student_mix: {
      executor: 'constant-vus',
      vus: 12,
      duration: SOAK_DURATION,
      exec: 'studentExec',
      startTime: '0s',
    },
    BM_IDN_004: {
      executor: 'constant-vus',
      vus: 10,
      duration: SOAK_DURATION,
      exec: 'bmIdn004',
      startTime: '0s',
    },
    BM_XC_002_LOGINS: {
      executor: 'constant-vus',
      vus: 5,
      duration: SOAK_DURATION,
      exec: 'loginsExec',
      startTime: '0s',
    },
  },
  thresholds: mergeThresholds(
    sloThresholds('BM_STU_002', 'READ_LIST', 'name'),
    sloThresholds('BM_STU_007', 'WRITE_SIMPLE', 'name'),
    sloThresholds('BM_BK_001', 'READ_LIST', 'name'),
    sloThresholds('BM_BK_005', 'WRITE_SIMPLE', 'name'),
    sloThresholds('BM_CRS_001', 'READ_LIST', 'name'),
    sloThresholds('BM_ME_002', 'READ_LIST', 'name'),
    sloThresholds('BM_IDN_004', 'READ_LIST'),
    sloThresholds('BM_XC_002_LOGINS', 'LOGIN'),
  ),
  noCookiesReset: true,
};

let registrarTarget = null;
let librarianIsbn = null;
let librarianOwnerCode = null;

function primeRegistrarTarget() {
  if (registrarTarget !== null) return;
  const res = http.get(`${BASE_URL}/api/v1/students?page=0&size=20`, {
    tags: { name: 'mixed-soak-setup' },
  });
  if (res.status === 200) {
    const content = JSON.parse(res.body).content || [];
    if (content.length > 0) registrarTarget = content[0].studentCode;
  }
}

function registrarWrite() {
  ensureLoggedIn('REGISTRAR');
  primeRegistrarTarget();
  if (registrarTarget === null) return;
  const payload = JSON.stringify({
    firstName: 'Bench',
    lastName: 'Soak',
    email: `soak-${registrarTarget}@bench.invalid`,
    dateOfBirth: '2000-01-01',
  });
  const res = http.put(`${BASE_URL}/api/v1/students/${registrarTarget}`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'BM_STU_007' },
  });
  check(res, { 'soak BM-STU-007 status 200': (r) => r.status === 200 });
}

export function registrarExec() {
  if (Math.random() < WRITE_SHARE) {
    registrarWrite();
  } else {
    bmStu002();
  }
}

function primeLibrarianTarget() {
  if (librarianIsbn !== null) return;
  ensureLoggedIn('LIBRARIAN');
  const booksRes = http.get(`${BASE_URL}/api/v1/books?page=0&size=20`, {
    tags: { name: 'mixed-soak-setup' },
  });
  if (booksRes.status === 200) {
    const content = JSON.parse(booksRes.body).content || [];
    if (content.length > 0) librarianIsbn = content[0].isbn;
  }
  // GET /api/v1/students is also LIBRARIAN-readable (SecurityConfig) -- used only to find a valid
  // owner target, not as a benchmark measurement, so it's tagged the same 'mixed-soak-setup' name.
  const studentsRes = http.get(`${BASE_URL}/api/v1/students?page=0&size=1`, {
    tags: { name: 'mixed-soak-setup' },
  });
  if (studentsRes.status === 200) {
    const content = JSON.parse(studentsRes.body).content || [];
    if (content.length > 0) librarianOwnerCode = content[0].studentCode;
  }
}

function librarianWrite() {
  ensureLoggedIn('LIBRARIAN');
  primeLibrarianTarget();
  if (librarianIsbn === null || librarianOwnerCode === null) return;
  const payload = JSON.stringify({ studentCode: librarianOwnerCode });
  const res = http.patch(`${BASE_URL}/api/v1/books/${librarianIsbn}/owner`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'BM_BK_005' },
  });
  check(res, { 'soak BM-BK-005 status 200': (r) => r.status === 200 });
}

export function librarianExec() {
  if (Math.random() < WRITE_SHARE) {
    librarianWrite();
  } else {
    bmBk001();
  }
}

export function courseAdminExec() {
  bmCrs001();
}

export function studentExec() {
  bmMe002();
}

export function bmIdn004() {
  ensureLoggedIn('SYSTEM_ADMINISTRATOR');
  const res = http.get(`${BASE_URL}/api/v1/sessions`, { tags: { name: 'BM_IDN_004' } });
  check(res, {
    'BM-IDN-004 status 200': (r) => r.status === 200,
    'BM-IDN-004 is array': (r) => Array.isArray(JSON.parse(r.body)),
  });
}

const LOGIN_ROLES = ['REGISTRAR', 'LIBRARIAN', 'COURSE_ADMINISTRATOR', 'SYSTEM_ADMINISTRATOR'];

export function loginsExec() {
  const role = LOGIN_ROLES[__ITER % LOGIN_ROLES.length];
  const { username, password } = credentialsFor(role);
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ username, password }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'BM_XC_002_LOGINS' },
  });
  check(res, { 'soak login status 200': (r) => r.status === 200 });
}
