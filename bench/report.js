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
// The metric-extraction/median/verdict logic lives in bench/lib/reportStats.js (PM-036) so
// bench/scale-sweep.js and bench/xc003-report.js can reuse it without re-deriving the same
// hard-won k6 export parsing.
//
// Usage: node report.js --scale=S2 [--reps=1]

import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { SCENARIOS, SCENARIO_FILES, officialId } from './lib/scenarios.js';
import { latestExports, median, extractStats, verdict, fmtMs } from './lib/reportStats.js';

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

function main() {
  const { scale, reps } = parseArgs(process.argv.slice(2));
  const rows = [];

  for (const [scenarioFile, bmIds] of Object.entries(SCENARIO_FILES)) {
    const exports = latestExports(OUT_DIR, scenarioFile, scale, reps);
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
    // SCENARIOS[bmId].vus is the VU count 03-benchmark-scenarios.md specifies for that id -- Sprint
    // 8 added entries with counts other than the PM-033 read-path default of 20 (BM-STU-006's 5,
    // BM-IDN-001's 1/10/25/50/100 ramp, ...), so this can no longer be a hardcoded literal.
    const vus = SCENARIOS[row.bmId].vus ?? 20;
    if (!row.stats) {
      console.log(`| ${id} | ${vus} | -- | -- | -- | -- | -- | ${row.sloClass} | **${row.verdict}** | 0 |`);
      continue;
    }
    const errPct = (row.stats.errRate * 100).toFixed(2);
    const verdictStr = row.verdict === 'PASS' ? row.verdict : `**${row.verdict}**`;
    console.log(
      `| ${id} | ${vus} | ${fmtMs(row.stats.p50)} | ${fmtMs(row.stats.p95)} | ${fmtMs(row.stats.p99)} | ` +
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
