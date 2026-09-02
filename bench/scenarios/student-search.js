// bench/scenarios/student-search.js — BM-STU-001..005 (03-benchmark-scenarios.md §2).
//
// H1 (unindexable LIKE search, scanned twice per request) and H3 (deep OFFSET paging), plus the
// BM-STU-001 no-filter/page-1 floor and the BM-STU-005 by-code control. Role: REGISTRAR.
//
// Run: k6 run --env BASE_URL=... --env SCALE=S2 bench/scenarios/student-search.js
// (normally invoked via `make bench SCENARIO=student-search SCALE=S2` -- see PM-032.)

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SCALE } from '../lib/config.js';
import { buildOptions } from '../lib/runner.js';
import { ensureLoggedIn } from '../lib/vuSession.js';
import { loadSearchTerms, termsWithHits, pickRandom } from '../lib/manifest.js';
import { SCALES } from '../seed/scales.js';

const searchTerms = termsWithHits(loadSearchTerms().searchTermTable.student);

export const options = buildOptions('warmup', [
  { id: 'BM_STU_001', exec: 'bmStu001' },
  { id: 'BM_STU_002', exec: 'bmStu002' },
  { id: 'BM_STU_003', exec: 'bmStu003' },
  { id: 'BM_STU_004', exec: 'bmStu004' },
  { id: 'BM_STU_005', exec: 'bmStu005' },
]);

// Deep-page discovery (BM-STU-004: "last decile of available pages") happens once per VU, during
// warm-up, so it never counts toward a measured scenario's metrics (02-benchmark-plan.md §2.1 --
// warm-up is discarded by design). Cached module-scope, which is per-VU state in k6.
//
// Sprint 9 (IP-05) replaced page/totalPages with cursor/nextCursor (CursorPage) -- there is no
// longer a server-reported total to compute "90% of pages" from. Approximate it client-side from
// the seeded row count for this SCALE (bench/seed/scales.js, the same source of truth the seed
// generator itself uses) and walk `cursor` forward that many pages during warm-up. Capped at
// MAX_WALK_STEPS so a large scale can't blow the 15s warm-up window -- at S3 this deliberately
// undershoots the true 90th-percentile depth; fine for S1/S2, a known limitation if this file is
// ever run at S3.
const PAGE_SIZE = 20;
const MAX_WALK_STEPS = 300;
const targetDepth = Math.min(
  Math.max(0, Math.floor(Math.ceil((SCALES[SCALE]?.students || 0) / PAGE_SIZE) * 0.9)),
  MAX_WALK_STEPS,
);

let deepCursor = null;
let deepCursorReady = false;
let sampleCodes = [];

function parsePage(res) {
  const body = JSON.parse(res.body);
  return body;
}

export function warmup() {
  ensureLoggedIn('REGISTRAR');
  const res = http.get(`${BASE_URL}/api/v1/students?page=0&size=20`, { tags: { name: 'warmup' } });
  if (res.status === 200) {
    const body = parsePage(res);
    sampleCodes = (body.content || []).map((s) => s.studentCode);
  }
  if (!deepCursorReady) {
    let cursor = null;
    for (let i = 0; i < targetDepth; i++) {
      const url = cursor
        ? `${BASE_URL}/api/v1/students?cursor=${encodeURIComponent(cursor)}&size=${PAGE_SIZE}`
        : `${BASE_URL}/api/v1/students?size=${PAGE_SIZE}`;
      const walkRes = http.get(url, { tags: { name: 'warmup' } });
      if (walkRes.status !== 200) break;
      const nextCursor = parsePage(walkRes).nextCursor;
      if (!nextCursor) break;
      cursor = nextCursor;
    }
    deepCursor = cursor;
    deepCursorReady = true;
  }
  http.get(`${BASE_URL}/api/v1/students?query=${encodeURIComponent(pickRandom(searchTerms))}&page=0&size=20`, {
    tags: { name: 'warmup' },
  });
}

export function bmStu001() {
  ensureLoggedIn('REGISTRAR');
  const res = http.get(`${BASE_URL}/api/v1/students?page=0&size=20`, { tags: { name: 'BM_STU_001' } });
  check(res, {
    'BM-STU-001 status 200': (r) => r.status === 200,
    'BM-STU-001 has content array': (r) => Array.isArray(JSON.parse(r.body).content),
  });
}

export function bmStu002() {
  ensureLoggedIn('REGISTRAR');
  const term = pickRandom(searchTerms);
  const res = http.get(`${BASE_URL}/api/v1/students?query=${encodeURIComponent(term)}&page=0&size=20`, {
    tags: { name: 'BM_STU_002' },
  });
  check(res, {
    'BM-STU-002 status 200': (r) => r.status === 200,
    'BM-STU-002 has content array': (r) => Array.isArray(JSON.parse(r.body).content),
  });
}

export function bmStu003() {
  ensureLoggedIn('REGISTRAR');
  const term = pickRandom(searchTerms);
  const res = http.get(`${BASE_URL}/api/v1/students?query=${encodeURIComponent(term)}&page=0&size=100`, {
    tags: { name: 'BM_STU_003' },
  });
  check(res, {
    'BM-STU-003 status 200': (r) => r.status === 200,
    'BM-STU-003 has content array': (r) => Array.isArray(JSON.parse(r.body).content),
  });
}

export function bmStu004() {
  ensureLoggedIn('REGISTRAR');
  const url = deepCursor
    ? `${BASE_URL}/api/v1/students?cursor=${encodeURIComponent(deepCursor)}&size=20`
    : `${BASE_URL}/api/v1/students?size=20`;
  const res = http.get(url, { tags: { name: 'BM_STU_004' } });
  check(res, {
    'BM-STU-004 status 200': (r) => r.status === 200,
    'BM-STU-004 has content array': (r) => Array.isArray(JSON.parse(r.body).content),
  });
}

export function bmStu005() {
  ensureLoggedIn('REGISTRAR');
  const code = sampleCodes.length > 0 ? pickRandom(sampleCodes) : null;
  if (code === null) return;
  const res = http.get(`${BASE_URL}/api/v1/students/${code}`, { tags: { name: 'BM_STU_005' } });
  check(res, {
    'BM-STU-005 status 200': (r) => r.status === 200,
    'BM-STU-005 has studentCode': (r) => JSON.parse(r.body).studentCode === code,
    'BM-STU-005 has dateOfBirth': (r) => JSON.parse(r.body).dateOfBirth !== undefined,
  });
}
