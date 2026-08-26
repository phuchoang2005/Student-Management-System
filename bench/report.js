#!/usr/bin/env node
// bench/report.js — bench-report (PM-032): renders the median of N repetitions' raw k6
// --summary-export JSON per BM-* id, against the SLO classes in bench/lib/scenarios.js, into the
// Results table shape from 05-baseline-and-reporting.md §3.
//
// Deliberately stops at rendering: it does NOT compare against a prior baseline or apply the
// regression bands (-10%/+20%/+50%) from 05 §2 -- that comparison logic is PM-039 (Sprint 8).
// This only computes a PASS/BREACH verdict against the SLO class target, which is all PM-034's
// first baselines need (there is nothing yet to regress against).
//
// Default reps=1, matching the current protocol (02-benchmark-plan.md §2.2 -- cut down from an
// earlier 3-repetition/median design). Pass --reps=N to render a median across N runs if you've
// deliberately re-run a scenario more than once (e.g. to double-check a suspicious number).
//
// Usage: node report.js --scale=S2 [--reps=1]

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { SCENARIOS, SCENARIO_FILES, officialId } from './lib/scenarios.js';
import { SLO_CLASSES, MAX_ERROR_RATE } from './lib/slo.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.join(__dirname, 'out');

function parseArgs(argv) {
  const args = { reps: 1 };
  for (const arg of argv) {
    if (arg.startsWith('--scale=')) args.scale = arg.slice('--scale='.length);
    else if (arg.startsWith('--reps=')) args.reps = Number(arg.slice('--reps='.length));
  }
  if (!args.scale) throw new Error('Missing --scale=S1|S2|S3');
  return args;
}

// Summary-export files are named `<scenarioFile>-<scale>-<timestamp>.json` (see the `bench`
// Makefile target) -- lexicographic sort on that timestamp suffix is also chronological.
function latestExports(scenarioFile, scale, reps) {
  const prefix = `${scenarioFile}-${scale}-`;
  if (!fs.existsSync(OUT_DIR)) return [];
  return fs
    .readdirSync(OUT_DIR)
    .filter((f) => f.startsWith(prefix) && f.endsWith('.json'))
    .sort()
    .reverse()
    .slice(0, reps)
    .map((f) => JSON.parse(fs.readFileSync(path.join(OUT_DIR, f), 'utf8')));
}

function median(numbers) {
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
function extractStats(summaryJson, bmId) {
  const duration = summaryJson.metrics?.[`http_req_duration{scenario:${bmId}}`];
  const reqs = summaryJson.metrics?.[`http_reqs{scenario:${bmId}}`];
  const failed = summaryJson.metrics?.[`http_req_failed{scenario:${bmId}}`];
  if (!duration) return null;

  return {
    p50: duration['med'],
    p95: duration['p(95)'],
    p99: duration['p(99)'],
    reqPerSec: reqs ? reqs['rate'] : null,
    errRate: failed ? failed['value'] : 0,
  };
}

function verdict(stats, sloClass) {
  if (!stats || stats.p95 === undefined || stats.p95 === null) return 'NO DATA';
  if (stats.errRate > MAX_ERROR_RATE) return 'BREACH (errors)';
  const slo = SLO_CLASSES[sloClass];
  if (stats.p95 > slo.p95 || (stats.p99 !== null && stats.p99 > slo.p99)) return 'BREACH';
  return 'PASS';
}

function fmtMs(n) {
  return n === null || n === undefined ? '--' : `${n.toFixed(0)} ms`;
}

function main() {
  const { scale, reps } = parseArgs(process.argv.slice(2));
  const rows = [];

  for (const [scenarioFile, bmIds] of Object.entries(SCENARIO_FILES)) {
    const exports = latestExports(scenarioFile, scale, reps);
    if (exports.length === 0) {
      console.warn(`No exports found for ${scenarioFile} at ${scale} in ${OUT_DIR} -- skipping.`);
      continue;
    }
    if (exports.length < reps) {
      console.warn(
        `Only ${exports.length}/${reps} repetitions found for ${scenarioFile} at ${scale} -- ` +
          'median is less robust than the protocol calls for (05-baseline-and-reporting.md §1).',
      );
    }

    for (const bmId of bmIds) {
      const perRep = exports.map((exp) => extractStats(exp, bmId)).filter(Boolean);
      if (perRep.length === 0) {
        rows.push({ bmId, sloClass: SCENARIOS[bmId].sloClass, stats: null, verdict: 'NO DATA', reps: 0 });
        continue;
      }
      const stats = {
        p50: median(perRep.map((s) => s.p50)),
        p95: median(perRep.map((s) => s.p95)),
        p99: median(perRep.map((s) => s.p99)),
        reqPerSec: median(perRep.map((s) => s.reqPerSec)),
        errRate: Math.max(...perRep.map((s) => s.errRate)),
      };
      rows.push({
        bmId,
        sloClass: SCENARIOS[bmId].sloClass,
        stats,
        verdict: verdict(stats, SCENARIOS[bmId].sloClass),
        reps: perRep.length,
      });
    }
  }

  console.log(`\n| BM ID | VUs | p50 | p95 | p99 | req/s | err % | SLO class | SLO | reps |`);
  console.log(`| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |`);
  for (const row of rows) {
    const id = officialId(row.bmId);
    if (!row.stats) {
      console.log(`| ${id} | 20 | -- | -- | -- | -- | -- | ${row.sloClass} | **${row.verdict}** | 0 |`);
      continue;
    }
    const errPct = (row.stats.errRate * 100).toFixed(2);
    const verdictStr = row.verdict === 'PASS' ? row.verdict : `**${row.verdict}**`;
    console.log(
      `| ${id} | 20 | ${fmtMs(row.stats.p50)} | ${fmtMs(row.stats.p95)} | ${fmtMs(row.stats.p99)} | ` +
        `${row.stats.reqPerSec !== null ? row.stats.reqPerSec.toFixed(1) : '--'} | ${errPct} | ` +
        `${row.sloClass} | ${verdictStr} | ${row.reps} |`,
    );
  }
  console.log(
    `\n(Paste into docs/benchmark-strategy/result/YYYY-MM-DD-${scale}-<short-sha>.md's Results ` +
      'table per 05-baseline-and-reporting.md §3. No Δ-vs-baseline column: PM-039 [Sprint 8] adds ' +
      'the regression comparison. Saturation/Findings/Actions still need filling in by hand.)',
  );
}

main();
