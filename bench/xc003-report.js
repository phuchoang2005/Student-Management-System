#!/usr/bin/env node
// bench/xc003-report.js — BM-XC-003 (03-benchmark-scenarios.md §7, PM-036).
//
// BM-XC-003 needs no new scenario file -- it's bench/scenarios/enrollment-list.js's existing
// BM-ENR-002 (the H2 headline) run at VU counts spanning the default Hikari pool of 10: 5, 10, 20,
// 40 (`make bench-xc-003` loops those via BM_ONLY=BM_ENR_002, naming each export
// enrollment-list-<scale>-vu<N>-<timestamp>.json so the four runs don't conflate under
// bench/report.js's plain <file>-<scale>-<timestamp> convention). report.js's model is one row per
// (BM-id, scale); this is one row per (BM-id, VUS) at a single scale instead, which is why it's a
// separate small script rather than a report.js mode.
//
// Usage: node xc003-report.js --scale=S2

import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { extractStats, fmtMs } from './lib/reportStats.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.join(__dirname, 'out');
const VUS_STAGES = [5, 10, 20, 40];

function parseArgs(argv) {
  const args = {};
  for (const arg of argv) {
    if (arg.startsWith('--scale=')) args.scale = arg.slice('--scale='.length);
  }
  if (!args.scale) throw new Error('Missing --scale=S1|S2|S3');
  return args;
}

function latestForVus(scale, vus) {
  const prefix = `enrollment-list-${scale}-vu${vus}-`;
  if (!fs.existsSync(OUT_DIR)) return null;
  const matches = fs
    .readdirSync(OUT_DIR)
    .filter((f) => f.startsWith(prefix) && f.endsWith('.json'))
    .sort()
    .reverse();
  return matches.length > 0 ? JSON.parse(fs.readFileSync(path.join(OUT_DIR, matches[0]), 'utf8')) : null;
}

function main() {
  const { scale } = parseArgs(process.argv.slice(2));

  console.log(`\n| VUs | p50 | p95 | p99 | req/s | err % |`);
  console.log(`| --- | --- | --- | --- | --- | --- |`);
  for (const vus of VUS_STAGES) {
    const summary = latestForVus(scale, vus);
    if (!summary) {
      console.log(`| ${vus} | -- | -- | -- | -- | -- | (no export found) |`);
      continue;
    }
    const stats = extractStats(summary, 'BM_ENR_002');
    if (!stats) {
      console.log(`| ${vus} | -- | -- | -- | -- | -- | (BM_ENR_002 not in export) |`);
      continue;
    }
    console.log(
      `| ${vus} | ${fmtMs(stats.p50)} | ${fmtMs(stats.p95)} | ${fmtMs(stats.p99)} | ` +
        `${stats.reqPerSec !== null ? stats.reqPerSec.toFixed(1) : '--'} | ${(stats.errRate * 100).toFixed(2)} |`,
    );
  }
  console.log(
    "\nWhere p95 starts climbing sharply relative to the 5-VU baseline (spanning the default " +
      "Hikari pool of 10, untuned anywhere in this codebase) is the saturation point -- H2's own " +
      'per-request query count (BM-ENR-002 holds a connection open across ~101 statements) is ' +
      'expected to push that point lower than a single-query endpoint would see.',
  );
}

main();
