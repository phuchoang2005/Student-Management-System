// bench/lib/runner.js — shared k6 `options` builder for the PM-033 scenario files.
//
// Two things every scenario file needs and would otherwise duplicate five times:
//
// 1. One shared warm-up scenario, run once per file rather than once per BM-* id (the protocol's
//    warm-up/steady/cool-down phases in 02-benchmark-plan.md §2.1 describe a *run*, not every
//    individual scenario inside it -- paying a full warm-up per id would multiply a file's runtime
//    for no benefit the first one didn't already buy).
// 2. Every other BM-* id's measurement window run sequentially, not concurrently, via `startTime`
//    offsets -- different endpoints sharing the Hikari pool and host CPU at the same moment would
//    confound each other's numbers, and each is a separate, independently-judged measurement.
//
// k6 auto-tags every request from a named `options.scenarios` entry with `scenario: <name>`, which
// is exactly the tag bench/lib/slo.js's sloThresholds() already keys its thresholds on -- naming a
// scenario `BM_STU_001` is enough to wire up its threshold with no manual per-request tagging.
//
// `noCookiesReset: true` is load-bearing, not cosmetic: k6 clears each VU's cookie jar between
// *iterations* by default (only within one iteration is it retained), so without this flag the
// JSESSIONID from login() would vanish on the very next iteration and every "session still live"
// check in bench/lib/session.js would 403 -- confirmed empirically while validating PM-033 (every
// request past the first login failed until this was set). This is exactly what makes "log in once
// per VU, reuse for the whole run" (02-benchmark-plan.md §1.1) actually work.

import { VUS, DURATION, WARMUP_DURATION, COOLDOWN_DURATION } from './config.js';
import { SCENARIOS } from './scenarios.js';
import { sloThresholds, mergeThresholds } from './slo.js';

function toSeconds(durationStr) {
  const m = String(durationStr).match(/^(\d+(?:\.\d+)?)(s|m|h)$/);
  if (!m) throw new Error(`Unsupported duration format: "${durationStr}" (expected e.g. "300s", "5m")`);
  const n = Number(m[1]);
  return m[2] === 'h' ? n * 3600 : m[2] === 'm' ? n * 60 : n;
}

/**
 * Build `{ scenarios, thresholds }` for a scenario file.
 *
 * @param warmupExec  name of the exported exec function for the shared warm-up scenario, or null
 *                     to skip warm-up entirely (not expected to be used by any PM-033 file).
 * @param entries      array of { id: 'BM_STU_001', exec: 'bmStu001', vus?: number }, in the order
 *                     they should run. `id` must be a key in bench/lib/scenarios.js's SCENARIOS.
 *
 * Honors __ENV.BM_ONLY (comma-separated BM-* ids, e.g. "BM_STU_002,BM_STU_004") to restrict which
 * entries actually run -- this is how the P0-only subset at S3 is expressed
 * (02-benchmark-plan.md §3 step 6) without a separate Makefile target or scenario file.
 */
export function buildOptions(warmupExec, entries) {
  const only = (__ENV.BM_ONLY || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const active = only.length > 0 ? entries.filter((e) => only.includes(e.id)) : entries;

  const scenarios = {};
  let cursor = 0;

  if (warmupExec && active.length > 0) {
    scenarios.warmup = {
      executor: 'constant-vus',
      vus: VUS,
      duration: WARMUP_DURATION,
      exec: warmupExec,
      startTime: '0s',
      gracefulStop: '5s',
    };
    cursor += toSeconds(WARMUP_DURATION) + 5;
  }

  const thresholdParts = [];
  for (const entry of active) {
    const meta = SCENARIOS[entry.id];
    if (!meta) {
      throw new Error(`Unknown BM-* id "${entry.id}" -- not present in bench/lib/scenarios.js`);
    }
    scenarios[entry.id] = {
      executor: 'constant-vus',
      vus: entry.vus || VUS,
      duration: DURATION,
      exec: entry.exec,
      startTime: `${cursor}s`,
      gracefulStop: COOLDOWN_DURATION,
    };
    thresholdParts.push(sloThresholds(entry.id, meta.sloClass));
    // k6 only tracks a tag-filtered submetric (and includes it in --summary-export) if something
    // references it -- sloThresholds() covers duration/error-rate, but bench/report.js also needs
    // throughput, so this permissive (always-true) threshold is what makes `http_reqs{scenario:X}`
    // exist in the export at all.
    thresholdParts.push({ [`http_reqs{scenario:${entry.id}}`]: ['count>=0'] });
    cursor += toSeconds(DURATION) + toSeconds(COOLDOWN_DURATION);
  }

  if (Object.keys(scenarios).length === 0) {
    throw new Error('BM_ONLY matched none of this file\'s scenarios -- nothing would run.');
  }

  return { scenarios, thresholds: mergeThresholds(...thresholdParts), noCookiesReset: true };
}
