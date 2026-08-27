// bench/scenarios/enrollment-list.js — BM-ENR-001..004 (03-benchmark-scenarios.md §5).
//
// The N+1 hazard itself: EnrollmentService.search resolves each row's course through
// CourseLookup.summaryOf inside the map, so a page costs ~(page size + 1) queries. BM-ENR-002 at
// size=100 is the headline H2 measurement -- and BM-ENR-001/002 deliberately draw from the *same*
// enrollment-count tail so the size=20 -> size=100 comparison isolates page-size cost rather than
// mixing in a different student's row count. Role: REGISTRAR.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../lib/config.js';
import { buildOptions } from '../lib/runner.js';
import { ensureLoggedIn } from '../lib/vuSession.js';
import { loadManifest, pickRandom } from '../lib/manifest.js';

const manifest = loadManifest();
const tailStudents = manifest.studentsByEnrollmentCount.high;
const heavyCourses = manifest.coursesByEnrollmentCount.heavy;

export const options = buildOptions('warmup', [
  { id: 'BM_ENR_001', exec: 'bmEnr001' },
  { id: 'BM_ENR_002', exec: 'bmEnr002' },
  { id: 'BM_ENR_003', exec: 'bmEnr003' },
  { id: 'BM_ENR_004', exec: 'bmEnr004' },
]);

// A known-existing (studentCode, courseCode) pair for the BM-ENR-004 control, discovered once per
// VU during warm-up (never counted in a measured scenario's metrics).
let knownPair = null;

export function warmup() {
  ensureLoggedIn('REGISTRAR');
  if (tailStudents.length > 0) {
    const code = pickRandom(tailStudents);
    const res = http.get(`${BASE_URL}/api/v1/enrollments?studentCode=${code}&size=20`, {
      tags: { name: 'warmup' },
    });
    if (res.status === 200) {
      const content = JSON.parse(res.body).content || [];
      if (content.length > 0) {
        knownPair = { studentCode: code, courseCode: content[0].course.courseCode };
      }
    }
  }
}

export function bmEnr001() {
  ensureLoggedIn('REGISTRAR');
  if (tailStudents.length === 0) return;
  const code = pickRandom(tailStudents);
  const res = http.get(`${BASE_URL}/api/v1/enrollments?studentCode=${code}&size=20`, {
    tags: { name: 'BM_ENR_001' },
  });
  check(res, {
    'BM-ENR-001 status 200': (r) => r.status === 200,
    'BM-ENR-001 rows have nested student/course': (r) => {
      const content = JSON.parse(r.body).content || [];
      return content.length === 0 || (content[0].student.studentCode && content[0].course.courseCode);
    },
  });
}

export function bmEnr002() {
  ensureLoggedIn('REGISTRAR');
  if (tailStudents.length === 0) return;
  const code = pickRandom(tailStudents);
  const res = http.get(`${BASE_URL}/api/v1/enrollments?studentCode=${code}&size=100`, {
    tags: { name: 'BM_ENR_002' },
  });
  check(res, {
    'BM-ENR-002 status 200': (r) => r.status === 200,
    'BM-ENR-002 rows have nested student/course': (r) => {
      const content = JSON.parse(r.body).content || [];
      return content.length === 0 || (content[0].student.studentCode && content[0].course.courseCode);
    },
  });
}

export function bmEnr003() {
  ensureLoggedIn('REGISTRAR');
  if (heavyCourses.length === 0) return;
  const code = pickRandom(heavyCourses);
  const res = http.get(`${BASE_URL}/api/v1/enrollments?courseCode=${code}&size=100`, {
    tags: { name: 'BM_ENR_003' },
  });
  check(res, {
    'BM-ENR-003 status 200': (r) => r.status === 200,
    'BM-ENR-003 rows have nested student/course': (r) => {
      const content = JSON.parse(r.body).content || [];
      return content.length === 0 || (content[0].student.studentCode && content[0].course.courseCode);
    },
  });
}

export function bmEnr004() {
  ensureLoggedIn('REGISTRAR');
  if (knownPair === null) return;
  const res = http.get(
    `${BASE_URL}/api/v1/enrollments/${knownPair.studentCode}/${knownPair.courseCode}`,
    { tags: { name: 'BM_ENR_004' } },
  );
  check(res, {
    'BM-ENR-004 status 200': (r) => r.status === 200,
    'BM-ENR-004 has enrolledAt': (r) => JSON.parse(r.body).enrolledAt !== undefined,
    'BM-ENR-004 matches requested pair': (r) => {
      const body = JSON.parse(r.body);
      return (
        body.student.studentCode === knownPair.studentCode && body.course.courseCode === knownPair.courseCode
      );
    },
  });
}
