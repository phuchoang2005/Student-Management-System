// bench/lib/baseline.js — PM-039: parses a prior run record's `## Results` table
// (docs/benchmark-strategy/result/*.md) as the durable baseline artifact, and applies the
// regression bands from 05-baseline-and-reporting.md §2.
//
// bench/out/*.json (raw k6 --summary-export) is gitignored and not guaranteed to survive to the
// next run (result/README.md) -- it cannot be "the" baseline per 05-baseline-and-reporting.md §1.
// The committed run-record Markdown file is what that section defines as the baseline, so this
// parses that file's own rendered table rather than re-deriving numbers from raw exports.

import fs from 'node:fs';

// Table shape (05-baseline-and-reporting.md §3 / bench/report.js's own render):
// | BM ID | VUs | p50 | p95 | p99 | req/s | err % | SLO class | SLO | Hazard |
//    0      1     2     3     4      5       6         7        8      9
const P95_COLUMN = 3;
const P99_COLUMN = 4;
const ERR_PCT_COLUMN = 6;

export function parseBaselineTable(markdown) {
  const lines = markdown.split('\n');
  const start = lines.findIndex((l) => l.trim() === '## Results');
  if (start === -1) throw new Error('No "## Results" section found in baseline file');
  const byId = {};
  for (let i = start + 1; i < lines.length; i++) {
    const line = lines[i];
    if (line.startsWith('## ')) break;
    const match = /^\|\s*(BM-[A-Za-z0-9-]+)\s*\|/.exec(line);
    if (!match) continue;
    const cells = line.split('|').slice(1, -1).map((c) => c.trim());
    byId[match[1].replace(/-/g, '_')] = {
      p95: parseMs(cells[P95_COLUMN]),
      p99: parseMs(cells[P99_COLUMN]),
      errRate: parsePercent(cells[ERR_PCT_COLUMN]),
    };
  }
  return byId;
}

// The header table's own `Dataset scale` row -- used to guard against comparing across scales,
// which 05-baseline-and-reporting.md §1 explicitly says are never comparable to each other. The
// cell isn't always a bare "S2": e.g. "S2 — Institution (5,000 students / ...)", so this matches
// the leading S[1-4] token rather than the whole cell.
export function parseBaselineScale(markdown) {
  const match = /\|\s*Dataset scale\s*\|\s*(S[1-4])\b/.exec(markdown);
  return match ? match[1] : null;
}

function parseMs(cell) {
  const match = /^([\d.]+)\s*ms$/.exec(cell ?? '');
  return match ? Number(match[1]) : null;
}

function parsePercent(cell) {
  const value = Number(cell);
  return Number.isFinite(value) ? value / 100 : null;
}

export function loadBaseline(path) {
  return parseBaselineTable(fs.readFileSync(path, 'utf8'));
}

export function loadBaselineScale(path) {
  return parseBaselineScale(fs.readFileSync(path, 'utf8'));
}

// The band table, applied per scenario (05-baseline-and-reporting.md §2): thresholds are deltas
// against the baseline p95 at the same scale/concurrency, never absolute latencies. Error rate
// blocks regardless of latency, and is checked first.
const MAX_ERROR_RATE = 0.001; // 0.1%, matching 05-baseline-and-reporting.md §2's "≥0.1%" row.

export function regressionVerdict(deltaPct, errRate) {
  if (errRate !== null && errRate !== undefined && errRate >= MAX_ERROR_RATE) return 'Block (errors)';
  if (deltaPct === null || deltaPct === undefined) return 'NO BASELINE';
  if (deltaPct < -10) return 'Improvement';
  if (deltaPct <= 20) return 'No change';
  if (deltaPct <= 50) return 'Investigate';
  return 'Block';
}
