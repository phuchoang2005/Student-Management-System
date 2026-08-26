// bench/scenarios/course-list.js — BM-CRS-001..003 (03-benchmark-scenarios.md §4).
//
// H1 on the smallest of the three searched tables (BM-CRS-001), the unfiltered list carrying a
// grouped LEFT JOIN enrolled-count per row as the reference point for what "H2 fixed" looks like
// (BM-CRS-002), and a detail read whose COUNT(*) is deliberately run against the most-enrolled
// course (BM-CRS-003). Role: COURSE_ADMINISTRATOR.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../lib/config.js';
import { buildOptions } from '../lib/runner.js';
import { ensureLoggedIn } from '../lib/vuSession.js';
import { loadSearchTerms, loadManifest, termsWithHits, pickRandom } from '../lib/manifest.js';

const searchTerms = termsWithHits(loadSearchTerms().searchTermTable.course);
const heavyCourses = loadManifest().coursesByEnrollmentCount.heavy;

export const options = buildOptions('warmup', [
  { id: 'BM_CRS_001', exec: 'bmCrs001' },
  { id: 'BM_CRS_002', exec: 'bmCrs002' },
  { id: 'BM_CRS_003', exec: 'bmCrs003' },
]);

export function warmup() {
  ensureLoggedIn('COURSE_ADMINISTRATOR');
  http.get(`${BASE_URL}/api/v1/courses?page=0&size=20`, { tags: { name: 'warmup' } });
  http.get(`${BASE_URL}/api/v1/courses?query=${encodeURIComponent(pickRandom(searchTerms))}&page=0&size=20`, {
    tags: { name: 'warmup' },
  });
}

export function bmCrs001() {
  ensureLoggedIn('COURSE_ADMINISTRATOR');
  const term = pickRandom(searchTerms);
  const res = http.get(`${BASE_URL}/api/v1/courses?query=${encodeURIComponent(term)}&page=0&size=20`, {
    tags: { name: 'BM_CRS_001' },
  });
  check(res, {
    'BM-CRS-001 status 200': (r) => r.status === 200,
    'BM-CRS-001 has content array': (r) => Array.isArray(JSON.parse(r.body).content),
  });
}

export function bmCrs002() {
  ensureLoggedIn('COURSE_ADMINISTRATOR');
  const res = http.get(`${BASE_URL}/api/v1/courses?page=0&size=20`, { tags: { name: 'BM_CRS_002' } });
  check(res, {
    'BM-CRS-002 status 200': (r) => r.status === 200,
    'BM-CRS-002 rows have enrolledCount': (r) => {
      const content = JSON.parse(r.body).content || [];
      return content.length === 0 || content.every((c) => typeof c.enrolledCount === 'number');
    },
  });
}

export function bmCrs003() {
  ensureLoggedIn('COURSE_ADMINISTRATOR');
  if (heavyCourses.length === 0) return;
  const code = pickRandom(heavyCourses);
  const res = http.get(`${BASE_URL}/api/v1/courses/${code}`, { tags: { name: 'BM_CRS_003' } });
  check(res, {
    'BM-CRS-003 status 200': (r) => r.status === 200,
    'BM-CRS-003 has courseCode': (r) => JSON.parse(r.body).courseCode === code,
    'BM-CRS-003 has enrolledCount': (r) => typeof JSON.parse(r.body).enrolledCount === 'number',
  });
}
