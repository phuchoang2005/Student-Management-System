// bench/scenarios/ci-smoke.js — the benchmark-smoke CI job's own scenario file (PM-038,
// 02-benchmark-plan.md §5).
//
// Reuses BM-STU-002 (H1) and BM-ENR-002 (H2) directly from their own PM-033 files, the same way
// mixed-soak.js (PM-036) reuses read-path exec functions elsewhere -- safe because bmStu002/
// bmEnr002 only depend on module-top-level state populated at import (search terms, manifest
// data), not on their own file's warmup() having run first.
//
// Deliberately does NOT use bench/lib/runner.js's buildOptions() or the real SLO_CLASSES
// thresholds from bench/lib/slo.js: a shared GitHub-hosted runner cannot produce a latency number
// worth comparing to 01-benchmark-strategy.md §4.2's real SLOs, so this job exists only to catch
// catastrophic breakage (a full-scan-in-a-loop, a missing index) via a deliberately generous
// ceiling (~10x the Read-list SLO) and a loose error-rate check. A smoke alarm, not a thermometer
// -- this job's output is advisory and never produces a verdict from the regression table
// (05-baseline-and-reporting.md §2).
//
// 15s warm-up discarded / 30s steady state per scenario, matching the standard protocol
// (02-benchmark-plan.md §2.1 / §5) rather than the sprint-backlog task table's "60s/60s" figure.

import { bmStu002 } from './student-search.js';
import { bmEnr002 } from './enrollment-list.js';
import { mergeThresholds } from '../lib/slo.js';

const SMOKE_P95_MS = 1500; // ~10x READ_LIST's 150ms p95 (01-benchmark-strategy.md §4.2)
const SMOKE_MAX_ERROR_RATE = 0.01; // 1% (02-benchmark-plan.md §5) -- looser than the real 0.1%

// bmStu002/bmEnr002 tag their own requests `name:BM_STU_002`/`name:BM_ENR_002`, not `scenario:...`
// (that auto-tag here would be `student_search_smoke`/`enrollment_list_smoke` instead) -- so
// thresholds key on the `name` dimension, the same reuse pattern mixed-soak.js already uses.
function smokeThresholds(tag) {
  return {
    [`http_req_duration{name:${tag}}`]: [`p(95)<${SMOKE_P95_MS}`],
    [`http_req_failed{name:${tag}}`]: [`rate<${SMOKE_MAX_ERROR_RATE}`],
    // Forces k6 to track the tag-filtered http_reqs submetric so it lands in --summary-export.
    [`http_reqs{name:${tag}}`]: ['count>=0'],
  };
}

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: 5,
      duration: '15s',
      exec: 'smokeWarmup',
      startTime: '0s',
      gracefulStop: '5s',
    },
    student_search_smoke: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
      exec: 'studentSearchSmoke',
      startTime: '20s',
      gracefulStop: '5s',
    },
    enrollment_list_smoke: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
      exec: 'enrollmentListSmoke',
      startTime: '55s',
      gracefulStop: '5s',
    },
  },
  thresholds: mergeThresholds(smokeThresholds('BM_STU_002'), smokeThresholds('BM_ENR_002')),
  noCookiesReset: true,
};

export function smokeWarmup() {
  bmStu002();
  bmEnr002();
}

export function studentSearchSmoke() {
  bmStu002();
}

export function enrollmentListSmoke() {
  bmEnr002();
}
