#!/usr/bin/env node
// bench/scale-sweep.js — BM-XC-004 (03-benchmark-scenarios.md §7, PM-036).
//
// "The single most valuable artifact this set produces" (01-benchmark-strategy.md §9): not a
// latency number but a curve -- does each P0 scenario's cost grow flat, linear, or worse than
// linear as the dataset scales from S1 to S3? Per the Sprint 8 scope decision, this reuses Sprint
// 7's existing baseline exports (bench/out/*-S{1,2,3}-*.json, still on disk from PM-034) rather
// than re-running k6 -- those runs are the ones flagged with real gaps (no app restart between
// scales, host CPU not captured, confounded with the Hikari pool-saturation issue BM-XC-003 exists
// to isolate), so the classification below is a first read, not a clean isolated result -- see the
// caveat this script prints.
//
// Usage: node scale-sweep.js

import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { P0_SCENARIOS, SCENARIO_FILES, officialId } from './lib/scenarios.js';
import { SCALES } from './seed/scales.js';
import { latestExports, extractStats, fmtMs } from './lib/reportStats.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.join(__dirname, 'out');
const SCALE_ORDER = ['S1', 'S2', 'S3'];

// Which table's row count each P0 scenario's cost should track, for the growth-vs-data-volume
// comparison -- not every scenario scans the same table (BM-ENR-002/BM-ME-002 both cost against
// `enrollments`; the other four each against their own listing's table).
const SIZE_TABLE = {
  BM_STU_002: 'students',
  BM_STU_004: 'students',
  BM_BK_001: 'books',
  BM_CRS_001: 'courses',
  BM_ENR_002: 'enrollments',
  BM_ME_002: 'enrollments',
};

function fileFor(bmId) {
  for (const [file, ids] of Object.entries(SCENARIO_FILES)) {
    if (ids.includes(bmId)) return file;
  }
  return null;
}

function classify(exponent) {
  if (exponent === null) return 'insufficient data';
  if (exponent < 0.3) return 'flat';
  if (exponent <= 1.3) return 'linear';
  return 'worse than linear';
}

function main() {
  const rows = [];

  for (const bmId of P0_SCENARIOS) {
    const file = fileFor(bmId);
    const p95ByScale = {};
    for (const scale of SCALE_ORDER) {
      const exports = latestExports(OUT_DIR, file, scale, 1);
      const stats = exports.length > 0 ? extractStats(exports[0], bmId) : null;
      p95ByScale[scale] = stats ? stats.p95 : null;
    }

    const p95S1 = p95ByScale.S1;
    const p95S3 = p95ByScale.S3;
    const rowsS1 = SCALES.S1[SIZE_TABLE[bmId]];
    const rowsS3 = SCALES.S3[SIZE_TABLE[bmId]];

    let exponent = null;
    if (p95S1 && p95S3 && p95S1 > 0 && rowsS3 > rowsS1) {
      exponent = Math.log(p95S3 / p95S1) / Math.log(rowsS3 / rowsS1);
    }

    rows.push({
      bmId,
      table: SIZE_TABLE[bmId],
      p95ByScale,
      rowsS1,
      rowsS3,
      exponent,
      classification: classify(exponent),
    });
  }

  console.log(
    `\n| BM ID | table (S1 -> S3 rows) | p95 S1 | p95 S2 | p95 S3 | growth exponent | classification |`,
  );
  console.log(`| --- | --- | --- | --- | --- | --- | --- |`);
  for (const row of rows) {
    console.log(
      `| ${officialId(row.bmId)} | ${row.table} (${row.rowsS1} -> ${row.rowsS3}) | ` +
        `${fmtMs(row.p95ByScale.S1)} | ${fmtMs(row.p95ByScale.S2)} | ${fmtMs(row.p95ByScale.S3)} | ` +
        `${row.exponent !== null ? row.exponent.toFixed(2) : '--'} | ${row.classification} |`,
    );
  }

  console.log(
    "\nExponent reading: ln(p95 S3 / p95 S1) / ln(rows S3 / rows S1) -- ~0 is flat (cost " +
      'independent of data volume), ~1 is linear (proportional), notably above 1 is worse than ' +
      "linear. <0.3 / 0.3-1.3 / >1.3 are this script's flat/linear/worse cutoffs, not a cited SLO.",
  );
  console.log(
    "Caveat: built from the 2026-08-26 Sprint 7 baseline exports (PM-034), which that sprint's own " +
      "findings flag as confounded with 20 VUs queuing against Hikari's untuned default-10 pool -- " +
      'read this as a first-pass curve, not an isolated result. BM-XC-003 (bench-xc-003) exists to ' +
      'separate that confound out; a follow-up clean re-run of this sweep is the natural next step.',
  );
}

main();
