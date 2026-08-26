// bench/scenarios/me-reads.js — BM-ME-001..003 (03-benchmark-scenarios.md §7).
//
// The student-facing side of H2: EnrollmentService.findByStudent backs /me/courses with the same
// per-row course lookup as the staff enrollment listing. BM-ME-002 matters more than BM-ENR-001 in
// one respect -- staff endpoints are used by tens of people, this one by every student, so its
// concurrency ceiling is the population, not the payroll. BM-ME-003 (owner-filtered, memoized) is
// the clearest single before/after illustration of what fixing H2 would buy.
//
// Role: STUDENT, one *distinct* cohort account per VU (bench/seed/manifest.js's login cohort) --
// not the single fixed STUDENT_USERNAME bench/lib/config.js's credentialsFor() expects, since the
// workload shape explicitly calls for "20 VUs, distinct student accounts."

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../lib/config.js';
import { buildOptions } from '../lib/runner.js';
import { ensureLoggedInAs } from '../lib/vuSession.js';
import { loadManifest } from '../lib/manifest.js';

const cohort = loadManifest().cohort;

export const options = buildOptions('warmup', [
  { id: 'BM_ME_001', exec: 'bmMe001' },
  { id: 'BM_ME_002', exec: 'bmMe002' },
  { id: 'BM_ME_003', exec: 'bmMe003' },
]);

function loginAsThisVu() {
  // __VU is 1-indexed and stable for the life of the VU -- cycling through the cohort by __VU
  // gives each VU a distinct, deterministic account without needing per-VU state beyond this.
  const email = cohort.studentEmails[(__VU - 1) % cohort.studentEmails.length];
  return ensureLoggedInAs('STUDENT', email, cohort.password);
}

export function warmup() {
  loginAsThisVu();
  http.get(`${BASE_URL}/api/v1/me/profile`, { tags: { name: 'warmup' } });
}

export function bmMe001() {
  loginAsThisVu();
  const res = http.get(`${BASE_URL}/api/v1/me/profile`, { tags: { name: 'BM_ME_001' } });
  check(res, {
    'BM-ME-001 status 200': (r) => r.status === 200,
    'BM-ME-001 has studentCode': (r) => JSON.parse(r.body).studentCode !== undefined,
  });
}

export function bmMe002() {
  loginAsThisVu();
  const res = http.get(`${BASE_URL}/api/v1/me/courses?size=20`, { tags: { name: 'BM_ME_002' } });
  check(res, {
    'BM-ME-002 status 200': (r) => r.status === 200,
    'BM-ME-002 rows have courseCode, no enrolledCount': (r) => {
      const content = JSON.parse(r.body).content || [];
      return content.length === 0 || (content[0].courseCode !== undefined && content[0].enrolledCount === undefined);
    },
  });
}

export function bmMe003() {
  loginAsThisVu();
  const res = http.get(`${BASE_URL}/api/v1/me/books?size=20`, { tags: { name: 'BM_ME_003' } });
  check(res, {
    'BM-ME-003 status 200': (r) => r.status === 200,
    'BM-ME-003 rows have isbn, no owner field': (r) => {
      const content = JSON.parse(r.body).content || [];
      return content.length === 0 || (content[0].isbn !== undefined && content[0].owner === undefined);
    },
  });
}
