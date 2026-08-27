// bench/scenarios/enrollment-batch.js — BM-ENR-005/006/007/008 (03-benchmark-scenarios.md §5,
// PM-035).
//
// H4: EnrollmentBatchService commits each course in a batch as its own transaction, so a request
// costs ~N transactions, not one (api-specification.md §5 decision #12 -- deliberate, for partial-
// success durability). BM-ENR-005 (single POST /enrollments) is the unit BM-ENR-006/007/008
// (POST /enrollments/batch at 1/10/50 courses) are multiples of. Sequential, in that order.
//
// Every iteration registers a brand-new, zero-enrollment student (tagged 'enrollment-batch-setup',
// excluded from every BM-* threshold/report below) so every enrollment call is a genuine INSERT
// rather than degrading into an ALREADY_ENROLLED short-circuit once a fixed course pool gets
// reused across iterations -- that would undercount H4's real per-course cost. Registration's own
// BCrypt cost (H5) is real but irrelevant to this file's measurement, which is exactly why it's
// excluded from the measured tag rather than folded into BM-ENR-005's numbers.
//
// BM-ENR-008 needs >=50 distinct courses for one call; S1 (20 courses total) can't supply that --
// the exec no-ops rather than sending a request for fewer than the cap.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../lib/config.js';
import { buildOptions } from '../lib/runner.js';
import { ensureLoggedIn } from '../lib/vuSession.js';
import { uniqueCode } from '../lib/vuShard.js';

export const options = buildOptions('warmup', [
  { id: 'BM_ENR_005', exec: 'bmEnr005', vus: 10 },
  { id: 'BM_ENR_006', exec: 'bmEnr006', vus: 5 },
  { id: 'BM_ENR_007', exec: 'bmEnr007', vus: 5 },
  { id: 'BM_ENR_008', exec: 'bmEnr008', vus: 5 },
]);

let coursePool = [];

export function warmup() {
  ensureLoggedIn('REGISTRAR');
  const res = http.get(`${BASE_URL}/api/v1/courses?page=0&size=100`, { tags: { name: 'warmup' } });
  coursePool = res.status === 200 ? (JSON.parse(res.body).content || []).map((c) => c.courseCode) : [];
}

function registerFreshStudent(prefix) {
  const code = uniqueCode(prefix);
  const payload = JSON.stringify({
    studentCode: code,
    firstName: 'Bench',
    lastName: `Enr${code}`,
    email: `${code}@bench.invalid`,
    dateOfBirth: '2000-01-01',
  });
  const res = http.post(`${BASE_URL}/api/v1/students`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'enrollment-batch-setup' },
  });
  return res.status === 201 ? code : null;
}

export function bmEnr005() {
  ensureLoggedIn('REGISTRAR');
  if (coursePool.length === 0) return;
  const studentCode = registerFreshStudent('EB');
  if (studentCode === null) return;
  const payload = JSON.stringify({ studentCode, courseCode: coursePool[0] });
  const res = http.post(`${BASE_URL}/api/v1/enrollments`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'BM_ENR_005' },
  });
  check(res, {
    'BM-ENR-005 status 201': (r) => r.status === 201,
    'BM-ENR-005 has enrolledAt': (r) => JSON.parse(r.body).enrolledAt !== undefined,
  });
}

function runBatch(officialId, tagName, courseCount) {
  if (coursePool.length < courseCount) return;
  const studentCode = registerFreshStudent('EB');
  if (studentCode === null) return;
  const courseCodes = coursePool.slice(0, courseCount);
  const payload = JSON.stringify({ studentCode, courseCodes });
  const res = http.post(`${BASE_URL}/api/v1/enrollments/batch`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: tagName },
  });
  check(res, {
    [`${officialId} status 200`]: (r) => r.status === 200,
    [`${officialId} all enrolled`]: (r) => {
      const body = JSON.parse(r.body);
      return body.enrolled === courseCount && body.failed === 0;
    },
  });
}

export function bmEnr006() {
  ensureLoggedIn('REGISTRAR');
  runBatch('BM-ENR-006', 'BM_ENR_006', 1);
}

export function bmEnr007() {
  ensureLoggedIn('REGISTRAR');
  runBatch('BM-ENR-007', 'BM_ENR_007', 10);
}

export function bmEnr008() {
  ensureLoggedIn('REGISTRAR');
  runBatch('BM-ENR-008', 'BM_ENR_008', 50);
}
