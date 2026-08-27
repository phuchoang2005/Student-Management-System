// bench/scenarios/cascade-delete.js — BM-XC-001 (03-benchmark-scenarios.md §7, PM-036).
//
// Bulk student deletion at N = 10, 50, 200, "as fast as the API accepts them" -- there is no bulk-
// delete endpoint, so this fires N individual DELETE /api/v1/students/{code} calls per stage. This
// file only measures the HTTP-visible half of H6 (the 204 returns before StudentService's
// StudentDeleted listeners run -- BookService.onStudentDeleted, EnrollmentService.onStudentDeleted
// -- on AsyncConfig's core-2/max-4/queue-50 pool). The other half -- wall-clock until every
// resulting event_publication row's COMPLETION_DATE is set, and whether the queue rejects anything
// -- is what bench/seed/cascade-drain.js measures afterward; k6 itself cannot query MySQL.
//
// Hand-builds `options.scenarios` instead of using buildOptions(): each stage is a fixed burst
// (`shared-iterations`, N iterations total, not a steady-state window), which buildOptions()'s
// constant-vus/duration model doesn't express. Stages are sequential (N10 -> N50 -> N200) and
// self-limiting: N=50/200 need at least that many students to exist, so a run at S1 (50 students
// total) will exhaust its N=50/200 pools and no-op the remainder rather than error -- a meaningful
// sample needs S2/S3. This scenario is destructive: a full dataset restore is mandatory afterward
// (04-workload-data-preparation.md §5).

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../lib/config.js';
import { ensureLoggedIn } from '../lib/vuSession.js';
import { shardFor } from '../lib/vuShard.js';
import { mergeThresholds, sloThresholds } from '../lib/slo.js';

const MAX_SHARD_VUS = 20;
const WARMUP_SECONDS = 10;
// Generous fixed spacing between stages so a real S2/S3 run has room for one stage's cascade to
// mostly settle before the next burst starts skewing host CPU/pool contention; irrelevant to a
// short smoke test since a no-op stage (see file header) finishes almost instantly regardless.
const STAGE_GAP_SECONDS = Number(__ENV.CASCADE_STAGE_GAP_SECONDS || 90);
const STAGE_MAX_DURATION = __ENV.CASCADE_STAGE_MAX_DURATION || '3m';

function stageStart(index) {
  return `${WARMUP_SECONDS + 5 + index * STAGE_GAP_SECONDS}s`;
}

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: MAX_SHARD_VUS,
      duration: `${WARMUP_SECONDS}s`,
      exec: 'warmup',
      startTime: '0s',
      gracefulStop: '5s',
    },
    BM_XC_001_N10: {
      executor: 'shared-iterations',
      vus: 10,
      iterations: 10,
      maxDuration: STAGE_MAX_DURATION,
      exec: 'bmXc001N10',
      startTime: stageStart(0),
    },
    BM_XC_001_N50: {
      executor: 'shared-iterations',
      vus: 20,
      iterations: 50,
      maxDuration: STAGE_MAX_DURATION,
      exec: 'bmXc001N50',
      startTime: stageStart(1),
    },
    BM_XC_001_N200: {
      executor: 'shared-iterations',
      vus: 20,
      iterations: 200,
      maxDuration: STAGE_MAX_DURATION,
      exec: 'bmXc001N200',
      startTime: stageStart(2),
    },
  },
  thresholds: mergeThresholds(
    sloThresholds('BM_XC_001_N10', 'WRITE_SIMPLE'),
    sloThresholds('BM_XC_001_N50', 'WRITE_SIMPLE'),
    sloThresholds('BM_XC_001_N200', 'WRITE_SIMPLE'),
    { 'http_reqs{scenario:BM_XC_001_N10}': ['count>=0'] },
    { 'http_reqs{scenario:BM_XC_001_N50}': ['count>=0'] },
    { 'http_reqs{scenario:BM_XC_001_N200}': ['count>=0'] },
  ),
  noCookiesReset: true,
};

let pool10 = [];
let pool50 = [];
let pool200 = [];
let cursor10 = 0;
let cursor50 = 0;
let cursor200 = 0;

export function warmup() {
  ensureLoggedIn('REGISTRAR');
  // Up to 3 pages of 100 -- enough headroom for 10+50+200=260 distinct codes at S2/S3; at S1 (50
  // students total) this simply comes back short, and the later stages no-op as documented above.
  let codes = [];
  for (let page = 0; page < 3; page++) {
    const res = http.get(`${BASE_URL}/api/v1/students?page=${page}&size=100`, {
      tags: { name: 'warmup' },
    });
    if (res.status !== 200) break;
    const content = JSON.parse(res.body).content || [];
    codes = codes.concat(content.map((s) => s.studentCode));
    if (content.length < 100) break;
  }
  pool10 = shardFor(codes.slice(0, 10), __VU, MAX_SHARD_VUS);
  pool50 = shardFor(codes.slice(10, 60), __VU, MAX_SHARD_VUS);
  pool200 = shardFor(codes.slice(60, 260), __VU, MAX_SHARD_VUS);
}

function deleteNext(pool, cursorGetter, cursorSetter, tagName) {
  ensureLoggedIn('REGISTRAR');
  const cursor = cursorGetter();
  if (cursor >= pool.length) return;
  const code = pool[cursor];
  cursorSetter(cursor + 1);
  const res = http.del(`${BASE_URL}/api/v1/students/${code}`, null, { tags: { name: tagName } });
  check(res, { [`${tagName} status 204`]: (r) => r.status === 204 });
}

export function bmXc001N10() {
  deleteNext(
    pool10,
    () => cursor10,
    (v) => (cursor10 = v),
    'BM_XC_001_N10',
  );
}

export function bmXc001N50() {
  deleteNext(
    pool50,
    () => cursor50,
    (v) => (cursor50 = v),
    'BM_XC_001_N50',
  );
}

export function bmXc001N200() {
  deleteNext(
    pool200,
    () => cursor200,
    (v) => (cursor200 = v),
    'BM_XC_001_N200',
  );
}
