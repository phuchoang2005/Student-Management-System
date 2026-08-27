// bench/scenarios/writes.js — BM-STU-006/007, BM-BK-005, BM-CRS-004 (03-benchmark-scenarios.md
// §§2-4, PM-035).
//
// Four single-aggregate writes, three roles, sequential in exactly this order -- BM-CRS-004 last
// because deleting a course destroys enrollment data every other scenario (and a re-run of this
// file) still needs; reseed after running this file (docs/PM-docs/04-sprint-backlog.md §11).
// BM-STU-006 also carries a hidden BCrypt+AES-GCM cost: StudentService.register provisions an
// identity account synchronously in the same request (H5) -- deliberately low VU count (5) so the
// write isn't also competing with its own registrations for CPU.
//
// Spanning three roles in one file is what exposed a latent bug in bench/lib/vuSession.js (fixed
// alongside this file, see its header comment): a role switch needs a real re-login, not just a
// liveness check against whichever role happened to log in first.
//
// S1 (20 courses total) exhausts BM-CRS-004's shard of deletable courses quickly at 5 VUs -- the
// exec no-ops once its shard is empty rather than erroring; a meaningful sample needs S2/S3.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../lib/config.js';
import { buildOptions } from '../lib/runner.js';
import { ensureLoggedIn } from '../lib/vuSession.js';
import { shardFor, uniqueCode } from '../lib/vuShard.js';

// Matches the largest `vus` any entry below specifies -- shardFor()'s only correctness requirement
// is that this exceeds the concurrent VU count actually in play, so distinct VU numbers land in
// distinct remainders (bench/lib/vuShard.js).
const MAX_SHARD_VUS = 20;

export const options = buildOptions('warmup', [
  { id: 'BM_STU_006', exec: 'bmStu006', vus: 5 },
  { id: 'BM_STU_007', exec: 'bmStu007', vus: 10 },
  { id: 'BM_BK_005', exec: 'bmBk005', vus: 10 },
  { id: 'BM_CRS_004', exec: 'bmCrs004', vus: 5 },
]);

// Discovered once per VU during warm-up (never counted in a measured scenario's metrics), then
// sharded so no two VUs ever touch the same row.
let myStudentCodes = [];
let myIsbns = [];
let myCourseCodes = [];
let ownerTargetCode = null;
let deleteCursor = 0;

export function warmup() {
  ensureLoggedIn('REGISTRAR');
  const studentsRes = http.get(`${BASE_URL}/api/v1/students?page=0&size=100`, {
    tags: { name: 'warmup' },
  });
  const studentCodes =
    studentsRes.status === 200
      ? (JSON.parse(studentsRes.body).content || []).map((s) => s.studentCode)
      : [];
  myStudentCodes = shardFor(studentCodes, __VU, MAX_SHARD_VUS);
  ownerTargetCode = studentCodes.length > 0 ? studentCodes[0] : null;

  ensureLoggedIn('LIBRARIAN');
  const booksRes = http.get(`${BASE_URL}/api/v1/books?page=0&size=100`, { tags: { name: 'warmup' } });
  const isbns =
    booksRes.status === 200 ? (JSON.parse(booksRes.body).content || []).map((b) => b.isbn) : [];
  myIsbns = shardFor(isbns, __VU, MAX_SHARD_VUS);

  ensureLoggedIn('COURSE_ADMINISTRATOR');
  const coursesRes = http.get(`${BASE_URL}/api/v1/courses?page=0&size=100`, {
    tags: { name: 'warmup' },
  });
  const courseCodes =
    coursesRes.status === 200
      ? (JSON.parse(coursesRes.body).content || []).map((c) => c.courseCode)
      : [];
  myCourseCodes = shardFor(courseCodes, __VU, MAX_SHARD_VUS);
}

export function bmStu006() {
  ensureLoggedIn('REGISTRAR');
  // A brand-new student every iteration -- registration can't reuse a code, unlike BM-STU-007's
  // update. uniqueCode() bounds this to well under the 20-char StudentCode/email-local-part limits.
  const code = uniqueCode('BS');
  const payload = JSON.stringify({
    studentCode: code,
    firstName: 'Bench',
    lastName: `Gen${code}`,
    email: `${code}@bench.invalid`,
    dateOfBirth: '2000-01-01',
  });
  const res = http.post(`${BASE_URL}/api/v1/students`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'BM_STU_006' },
  });
  check(res, {
    'BM-STU-006 status 201': (r) => r.status === 201,
    'BM-STU-006 has initialPassword': (r) => JSON.parse(r.body).initialPassword !== undefined,
  });
}

export function bmStu007() {
  ensureLoggedIn('REGISTRAR');
  if (myStudentCodes.length === 0) return;
  // Cycling this VU's own shard is fine (repeated updates to the same student, over time, don't
  // manufacture contention) -- only *cross-VU* collisions would.
  const code = myStudentCodes[__ITER % myStudentCodes.length];
  const payload = JSON.stringify({
    firstName: 'Bench',
    lastName: 'Updated',
    email: `updated-${code}@bench.invalid`,
    dateOfBirth: '2000-01-01',
  });
  const res = http.put(`${BASE_URL}/api/v1/students/${code}`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'BM_STU_007' },
  });
  check(res, {
    'BM-STU-007 status 200': (r) => r.status === 200,
    'BM-STU-007 reflects update': (r) => JSON.parse(r.body).lastName === 'Updated',
  });
}

export function bmBk005() {
  ensureLoggedIn('LIBRARIAN');
  if (myIsbns.length === 0 || ownerTargetCode === null) return;
  const isbn = myIsbns[__ITER % myIsbns.length];
  const payload = JSON.stringify({ studentCode: ownerTargetCode });
  const res = http.patch(`${BASE_URL}/api/v1/books/${isbn}/owner`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'BM_BK_005' },
  });
  check(res, {
    'BM-BK-005 status 200': (r) => r.status === 200,
    'BM-BK-005 owner set': (r) => JSON.parse(r.body).ownerStudentCode === ownerTargetCode,
  });
}

export function bmCrs004() {
  ensureLoggedIn('COURSE_ADMINISTRATOR');
  // Destructive and one-shot per code -- unlike BM-STU-007/BM-BK-005, this can't cycle its shard;
  // it advances a cursor and no-ops once exhausted (03-benchmark-scenarios.md's S1-scale limit).
  if (deleteCursor >= myCourseCodes.length) return;
  const code = myCourseCodes[deleteCursor];
  deleteCursor += 1;
  const res = http.del(`${BASE_URL}/api/v1/courses/${code}`, null, { tags: { name: 'BM_CRS_004' } });
  check(res, { 'BM-CRS-004 status 204': (r) => r.status === 204 });
}
