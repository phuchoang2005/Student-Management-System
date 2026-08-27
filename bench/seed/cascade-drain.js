#!/usr/bin/env node
// bench/seed/cascade-drain.js — BM-XC-001 companion (03-benchmark-scenarios.md §7, PM-036).
//
// k6 cannot query MySQL (bench/lib/ is deliberately npm-free), so the wall-clock half of H6 --
// how long after the HTTP 204s does StudentDeleted's cascade actually finish -- is measured here
// instead, against EVENT_PUBLICATION (V4__add_event_publication_table.sql; uppercase and
// case-sensitive on MySQL/Linux -- see that migration's own comment). Run this right after
// bench/scenarios/cascade-delete.js's k6 run finishes, passing --since as that run's start time.
//
// LISTENER_ID is Spring Modulith's fully-qualified `<class>.<method>(...)` identifier for each
// @ApplicationModuleListener; a %-wrapped substring match against the class simple name + method
// name (the same pattern EventPublicationRegistryIntegrationTest.java's own assertions use) is
// what's actually load-bearing, not an exact-equality match against the full string.
//
// Usage: node seed/cascade-drain.js --since=2026-08-27T10:00:00.000Z --scale=S2 [--expected=200]
//   [--timeout=60] [--poll=1]

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createConnection } from './db.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.join(__dirname, '..', 'out');

// Two listeners StudentDeleted dispatches to -- book/application/BookService.onStudentDeleted and
// enrollment/application/EnrollmentService.onStudentDeleted -- both async on AsyncConfig's
// core-2/max-4/queue-50 taskExecutor. Every deleted student publishes one EVENT_PUBLICATION row
// per listener, so N deletes should produce 2N rows here.
const LISTENER_PATTERNS = ['%BookService%onStudentDeleted%', '%EnrollmentService%onStudentDeleted%'];

function parseArgs(argv) {
  const args = { timeoutSeconds: 60, pollSeconds: 1 };
  for (const arg of argv) {
    if (arg.startsWith('--since=')) args.since = arg.slice('--since='.length);
    else if (arg.startsWith('--scale=')) args.scale = arg.slice('--scale='.length);
    else if (arg.startsWith('--expected=')) args.expected = Number(arg.slice('--expected='.length));
    else if (arg.startsWith('--timeout=')) args.timeoutSeconds = Number(arg.slice('--timeout='.length));
    else if (arg.startsWith('--poll=')) args.pollSeconds = Number(arg.slice('--poll='.length));
  }
  if (!args.since) throw new Error("Missing --since=<ISO timestamp> (the k6 run's start time)");
  if (!args.scale) throw new Error('Missing --scale=S1|S2|S3|S4');
  return args;
}

// mysql2 serializes a JS `Date` object using the *local system* timezone, not UTC (confirmed while
// validating this script: on a UTC+7 host, `new Date('...T02:33:03.000Z')` was bound as
// '2026-08-27 09:33:03', seven hours later than intended) -- silently wrong on any host not
// already in UTC, since MySQL's TIMESTAMP columns here are UTC (confirmed server-side: `NOW()` ==
// `UTC_TIMESTAMP()`). Build the comparison value as an explicit UTC string instead of handing
// mysql2 a Date object to convert.
function toMysqlUtc(isoString) {
  return new Date(isoString).toISOString().slice(0, 23).replace('T', ' ');
}

async function fetchRows(connection, since) {
  const sinceUtc = toMysqlUtc(since);
  const [rows] = await connection.query(
    `SELECT ID, LISTENER_ID, PUBLICATION_DATE, COMPLETION_DATE FROM EVENT_PUBLICATION
     WHERE PUBLICATION_DATE >= ? AND (LISTENER_ID LIKE ? OR LISTENER_ID LIKE ?)`,
    [sinceUtc, LISTENER_PATTERNS[0], LISTENER_PATTERNS[1]],
  );
  return rows;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
  const { since, scale, expected, timeoutSeconds, pollSeconds } = parseArgs(process.argv.slice(2));
  const connection = await createConnection();
  const startedPolling = Date.now();

  try {
    let rows = await fetchRows(connection, since);
    if (rows.length === 0) {
      console.warn(
        `No EVENT_PUBLICATION rows found with PUBLICATION_DATE >= ${since}. Either the cascade-delete ` +
          "run produced none (nothing deleted?) or --since is wrong -- pass the k6 run's actual start time.",
      );
    }
    if (expected !== undefined && rows.length !== expected * 2) {
      console.warn(
        `Expected ${expected * 2} rows (2 listeners x ${expected} deletes), found ${rows.length} so far -- ` +
          "still polling; a short gap can just mean some publications haven't been written yet.",
      );
    }

    let pending = rows.filter((r) => r.COMPLETION_DATE === null);
    while (pending.length > 0 && (Date.now() - startedPolling) / 1000 < timeoutSeconds) {
      await sleep(pollSeconds * 1000);
      rows = await fetchRows(connection, since);
      pending = rows.filter((r) => r.COMPLETION_DATE === null);
    }

    const elapsedSeconds = (Date.now() - startedPolling) / 1000;
    const result = {
      scale,
      since,
      polledAt: new Date().toISOString(),
      totalRows: rows.length,
      completedRows: rows.length - pending.length,
      pendingAtTimeout: pending.length,
      wallClockSecondsToDrain: pending.length === 0 ? elapsedSeconds : null,
      // AsyncConfig's taskExecutor: core=2, max=4, queue=50 -- a row still pending well past a
      // generous timeout, with no sign of finishing, is consistent with a rejected task (the queue
      // filled and ThreadPoolTaskExecutor's default AbortPolicy dropped it) rather than a slow one.
      likelyRejected: pending.map((r) => ({ id: r.ID, listenerId: r.LISTENER_ID })),
    };

    fs.mkdirSync(OUT_DIR, { recursive: true });
    const outFile = path.join(OUT_DIR, `cascade-drain-${scale}-${Date.now()}.json`);
    fs.writeFileSync(outFile, JSON.stringify(result, null, 2));

    if (pending.length === 0) {
      console.log(
        `All ${rows.length} EVENT_PUBLICATION rows drained in ~${elapsedSeconds.toFixed(1)}s of polling.`,
      );
    } else {
      console.warn(
        `${pending.length}/${rows.length} rows still incomplete after ${timeoutSeconds}s -- ` +
          `treat as likely-rejected against AsyncConfig's core-2/max-4/queue-50 pool. See ${outFile}.`,
      );
    }
    console.log(`Written to ${outFile}`);
  } finally {
    await connection.end();
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
