// bench/lib/reportStats.js — shared k6 --summary-export parsing (PM-032, extracted here in
// Sprint 8/PM-036 so bench/scale-sweep.js and bench/xc003-report.js can reuse it instead of
// re-deriving the same hard-won parsing report.js already got right -- see extractStats()'s own
// comment for the metric-shape pitfalls a naive reimplementation would hit again).

import fs from 'node:fs';
import path from 'node:path';
import { SLO_CLASSES, MAX_ERROR_RATE } from './slo.js';

// Summary-export files are named `<scenarioFile>-<scale>-<timestamp>.json` (see the `bench`
// Makefile target) -- lexicographic sort on that timestamp suffix is also chronological.
//
// A plain prefix match on `<scenarioFile>-<scale>-` also matches `bench-xc-003`'s
// `enrollment-list-S2-vu5-<timestamp>.json`-shaped siblings (BM_ONLY-pinned, single-scenario
// sweep exports), and since 'v' sorts after every timestamp's leading digit, .sort().reverse()
// picks the sweep file over the real bench-all run whenever both exist -- silently substituting
// a different VU count's numbers under the id they came in as. Requiring the byte right after the
// prefix to be a timestamp's leading digit excludes those siblings.
export function latestExports(outDir, scenarioFile, scale, reps) {
  const prefix = `${scenarioFile}-${scale}-`;
  if (!fs.existsSync(outDir)) return [];
  return fs
    .readdirSync(outDir)
    .filter((f) => f.startsWith(prefix) && /^\d/.test(f.slice(prefix.length)) && f.endsWith('.json'))
    .sort()
    .reverse()
    .slice(0, reps)
    .map((f) => JSON.parse(fs.readFileSync(path.join(outDir, f), 'utf8')));
}

export function median(numbers) {
  const clean = numbers.filter((n) => n !== null && n !== undefined);
  if (clean.length === 0) return null;
  const sorted = [...clean].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
}

// k6's --summary-export JSON keys every metric (including tag-filtered submetrics) directly under
// `metrics` -- each entry's stats are flat fields on the metric object itself, not nested under a
// `.values` key (confirmed against a real export while validating PM-033; the k6 docs' example
// layout is easy to misread as nested). --summary-trend-stats (set by the `bench` Makefile target)
// guarantees med/p(95)/p(99) are present on trend metrics like http_req_duration regardless of
// whether a threshold happens to reference that percentile.
//
// http_req_failed is a Rate metric: `value` IS the error rate (0..1) -- `passes`/`fails` are
// generic true/false sample counts for the underlying boolean (true = request counted as failed),
// not literal "test passed/failed". http_reqs is a Counter: `count` and `rate` (already per-second,
// computed by k6 from the run's actual elapsed time) are both present once something references
// the tag-filtered submetric, which bench/lib/runner.js does via a permissive threshold.
//
// `dimension` tries 'scenario' first (every buildOptions()-based file), then falls back to 'name'
// -- mixed-soak.js (Sprint 8) reuses other files' exec functions inside its own role-partitioned
// executors, so the requests it cares about are tagged `scenario:<executor>` but keep their
// original `name:BM_STU_002`-shaped tag from the file they came from. This changes nothing for any
// file that only ever used the 'scenario' tag, since that lookup is tried first and always wins.
export function extractStats(summaryJson, bmId) {
  for (const dimension of ['scenario', 'name']) {
    const duration = summaryJson.metrics?.[`http_req_duration{${dimension}:${bmId}}`];
    if (!duration) continue;
    const reqs = summaryJson.metrics?.[`http_reqs{${dimension}:${bmId}}`];
    const failed = summaryJson.metrics?.[`http_req_failed{${dimension}:${bmId}}`];
    return {
      p50: duration['med'],
      p95: duration['p(95)'],
      p99: duration['p(99)'],
      reqPerSec: reqs ? reqs['rate'] : null,
      errRate: failed ? failed['value'] : 0,
    };
  }
  return null;
}

export function verdict(stats, sloClass) {
  if (!stats || stats.p95 === undefined || stats.p95 === null) return 'NO DATA';
  if (stats.errRate > MAX_ERROR_RATE) return 'BREACH (errors)';
  const slo = SLO_CLASSES[sloClass];
  if (stats.p95 > slo.p95 || (stats.p99 !== null && stats.p99 > slo.p99)) return 'BREACH';
  return 'PASS';
}

export function fmtMs(n) {
  return n === null || n === undefined ? '--' : `${n.toFixed(0)} ms`;
}
