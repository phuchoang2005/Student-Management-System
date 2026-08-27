#!/usr/bin/env node
// bench/report.js — bench-report (PM-032): renders the median of N repetitions' raw k6
// --summary-export JSON per BM-* id, against the SLO classes in bench/lib/scenarios.js, into the
// Results table shape from 05-baseline-and-reporting.md §3.
//
// Default reps=1, matching the current protocol (02-benchmark-plan.md §2.2 -- cut down from an
// earlier 3-repetition/median design). Pass --reps=N to render a median across N runs if you've
// deliberately re-run a scenario more than once (e.g. to double-check a suspicious number).
//
// --baseline=<path> (PM-039, Sprint 8) compares against a prior *committed* run-record file under
// docs/benchmark-strategy/result/ -- not raw bench/out/ exports, which are gitignored and not
// guaranteed to survive to the next run (result/README.md); the committed run-record file is what
// 05-baseline-and-reporting.md §1 defines as "the baseline." The trailing column renders the
// per-scenario Δp95-vs-baseline regression verdict from 05 §2's band table (parsing/verdict logic
// in bench/lib/baseline.js). Without --baseline, the trailing column falls back to the scenario's
// hazard id (bench/lib/scenarios.js) -- the shape every accepted Sprint 7 baseline file already
// uses for a first run, which has nothing yet to regress against.
//
// The metric-extraction/median/verdict logic lives in bench/lib/reportStats.js (PM-036) so
// bench/scale-sweep.js and bench/xc003-report.js can reuse it without re-deriving the same
// hard-won k6 export parsing.
//
// Usage: node report.js --scale=S2 [--reps=1] [--baseline=docs/benchmark-strategy/result/<file>.md]

import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { SCENARIOS, SCENARIO_FILES, officialId } from './lib/scenarios.js';
import { latestExports, median, extractStats, verdict, fmtMs } from './lib/reportStats.js';
import { loadBaseline, loadBaselineScale, regressionVerdict } from './lib/baseline.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.join(__dirname, 'out');

function parseArgs(argv) {
  const args = { reps: 1 };
  for (const arg of argv) {
    if (arg.startsWith('--scale=')) args.scale = arg.slice('--scale='.length);
    else if (arg.startsWith('--reps=')) args.reps = Number(arg.slice('--reps='.length));
    else if (arg.startsWith('--baseline=')) args.baseline = arg.slice('--baseline='.length);
  }
  if (!args.scale) throw new Error('Missing --scale=S1|S2|S3');
  return args;
}

function main() {
  const { scale, reps, baseline: baselinePath } = parseArgs(process.argv.slice(2));
  const rows = [];

  let baseline = null;
  if (baselinePath) {
    const baselineScale = loadBaselineScale(baselinePath);
    if (baselineScale && baselineScale !== scale) {
      console.warn(
        `--baseline=${baselinePath} was recorded at ${baselineScale}, not ${scale} -- ` +
          '05-baseline-and-reporting.md §1: results at different scales are never comparable. ' +
          'Every Δ below is meaningless; pass a baseline file recorded at the same scale.',
      );
    }
    baseline = loadBaseline(baselinePath);
  }

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

  const lastColumnHeader = baseline ? 'Δ p95 vs. baseline' : 'Hazard';
  console.log(`\n| BM ID | VUs | p50 | p95 | p99 | req/s | err % | SLO class | SLO | ${lastColumnHeader} |`);
  console.log(`| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |`);
  for (const row of rows) {
    const id = officialId(row.bmId);
    // SCENARIOS[bmId].vus is the VU count 03-benchmark-scenarios.md specifies for that id -- Sprint
    // 8 added entries with counts other than the PM-033 read-path default of 20 (BM-STU-006's 5,
    // BM-IDN-001's 1/10/25/50/100 ramp, ...), so this can no longer be a hardcoded literal.
    const vus = SCENARIOS[row.bmId].vus ?? 20;
    const lastCell = baseline ? deltaCell(baseline[row.bmId], row.stats) : SCENARIOS[row.bmId].hazard;
    if (!row.stats) {
      console.log(`| ${id} | ${vus} | -- | -- | -- | -- | -- | ${row.sloClass} | **${row.verdict}** | ${lastCell} |`);
      continue;
    }
    const errPct = (row.stats.errRate * 100).toFixed(2);
    const verdictStr = row.verdict === 'PASS' ? row.verdict : `**${row.verdict}**`;
    console.log(
      `| ${id} | ${vus} | ${fmtMs(row.stats.p50)} | ${fmtMs(row.stats.p95)} | ${fmtMs(row.stats.p99)} | ` +
        `${row.stats.reqPerSec !== null ? row.stats.reqPerSec.toFixed(1) : '--'} | ${errPct} | ` +
        `${row.sloClass} | ${verdictStr} | ${lastCell} |`,
    );
  }
  console.log(
    `\n(Paste into docs/benchmark-strategy/result/YYYY-MM-DD-${scale}-<short-sha>.md's Results ` +
      'table per 05-baseline-and-reporting.md §3. SLO verdict and regression verdict are carried ' +
      'separately per 05 §2.1 -- neither substitutes for the other. Saturation/Findings/Actions ' +
      'still need filling in by hand.)',
  );
}

// Renders the SLO-verdict-independent regression verdict for one scenario's Δp95 vs. baseline
// (05-baseline-and-reporting.md §2). '--' when there's no baseline entry for this id (e.g. a
// Sprint 8 id absent from a Sprint 7 baseline file) or no observed data to compare.
function deltaCell(baselineStats, observedStats) {
  if (!baselineStats || baselineStats.p95 == null || !observedStats || observedStats.p95 == null) {
    return '--';
  }
  const deltaPct = ((observedStats.p95 - baselineStats.p95) / baselineStats.p95) * 100;
  const verdict = regressionVerdict(deltaPct, observedStats.errRate);
  const sign = deltaPct >= 0 ? '+' : '';
  const text = `${sign}${deltaPct.toFixed(0)}%`;
  return verdict === 'No change' || verdict === 'Improvement' ? text : `**${text} (${verdict})**`;
}

main();
