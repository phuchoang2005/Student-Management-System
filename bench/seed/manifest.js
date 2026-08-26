#!/usr/bin/env node
// bench/seed/manifest.js — runtime sample data for the PM-033 k6 scenarios, queried directly from
// an already-seeded database. Run right after bench/seed/generate.js (make bench-seed does this
// automatically).
//
// Deliberately a *separate* script, not an addition to generate.js: generate.js (PM-031) is done
// and its determinism is extensively verified against its own in-memory generation state; this
// script instead queries the ground truth back out of the database after the fact, which is
// simpler, cannot drift from what was actually inserted, and touches none of PM-031's files.
//
// Two things scenario scripts cannot cheaply discover live against the running API:
//   - which students sit in the enrollment-count *tail* (BM-ENR-002 needs a student with >20
//     enrollments for its size=100 page to actually span multiple pages worth of rows -- the
//     whole point of "the headline H2 measurement"; StudentSummaryDto exposes no enrolled-count
//     field to search by)
//   - the full seeded login cohort (BM-ME-* needs one *distinct* STUDENT login per VU; the
//     generator only prints 3 sample emails to stdout, not a machine-readable full list)
// A handful of enrollment-heavy course codes are included for the same reason on the course side
// (BM-ENR-003, BM-CRS-003).
//
// Usage: node seed/manifest.js --scale=S2

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createConnection } from './db.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.join(__dirname, '..', 'out');

const SAMPLE_SIZE = 50;
const COHORT_PASSWORD = process.env.COHORT_PASSWORD || 'Benchmark123!';

function parseArgs(argv) {
  const args = {};
  for (const arg of argv) {
    if (arg.startsWith('--scale=')) args.scale = arg.slice('--scale='.length);
  }
  if (!args.scale) throw new Error('Missing --scale=S1|S2|S3|S4');
  return args;
}

async function main() {
  const { scale } = parseArgs(process.argv.slice(2));
  const connection = await createConnection();

  try {
    // The full login cohort -- every STUDENT user's username IS their email (generate.js inserts
    // `s.email` as `username` for cohort rows). No staff usernames here; scenario files that need
    // staff roles already have them fixed via bench/lib/config.js's DEFAULT_STAFF_USERNAMES.
    const [cohortRows] = await connection.query(
      "SELECT username FROM users WHERE role = 'STUDENT' ORDER BY username",
    );
    const cohortEmails = cohortRows.map((r) => r.username);
    if (cohortEmails.length === 0) {
      throw new Error('No STUDENT users found -- was the account cohort seeded? (bench/seed/generate.js)');
    }

    // Students bucketed by enrollment count: low end (1-3) and the tail (highest counts) --
    // 04-workload-data-preparation.md §2 skews per-student enrollments with a mean ~6 and a
    // 15-20 tail; only the tail reliably has enough rows to page through at size=100.
    const [lowRows] = await connection.query(
      `SELECT s.student_code AS code, COUNT(e.course_id) AS c
       FROM students s LEFT JOIN enrollments e ON e.student_id = s.id
       GROUP BY s.id
       HAVING c BETWEEN 1 AND 3
       ORDER BY c ASC
       LIMIT ?`,
      [SAMPLE_SIZE],
    );
    const [highRows] = await connection.query(
      `SELECT s.student_code AS code, COUNT(e.course_id) AS c
       FROM students s LEFT JOIN enrollments e ON e.student_id = s.id
       GROUP BY s.id
       ORDER BY c DESC
       LIMIT ?`,
      [SAMPLE_SIZE],
    );

    // Courses bucketed by enrollment count, heaviest first (Zipf-skewed per 04 §2 -- a handful of
    // large mandatory courses, a long tail). BM-CRS-003/BM-ENR-003 deliberately include the most
    // enrolled course.
    const [heavyCourseRows] = await connection.query(
      `SELECT c.course_code AS code, COUNT(e.student_id) AS c
       FROM courses c LEFT JOIN enrollments e ON e.course_id = c.id
       GROUP BY c.id
       ORDER BY c DESC
       LIMIT ?`,
      [SAMPLE_SIZE],
    );

    if (highRows.length === 0 || heavyCourseRows.length === 0) {
      throw new Error('No enrollments found -- was the dataset seeded with rows in `enrollments`?');
    }

    const manifest = {
      scale,
      generatedAt: new Date().toISOString(),
      cohort: {
        studentEmails: cohortEmails,
        password: COHORT_PASSWORD,
      },
      studentsByEnrollmentCount: {
        low: lowRows.map((r) => r.code),
        high: highRows.map((r) => r.code),
      },
      coursesByEnrollmentCount: {
        heavy: heavyCourseRows.map((r) => r.code),
      },
      mostEnrolledCourseCode: heavyCourseRows[0].code,
    };

    fs.mkdirSync(OUT_DIR, { recursive: true });
    const outFile = path.join(OUT_DIR, `${scale}-manifest.json`);
    fs.writeFileSync(outFile, JSON.stringify(manifest, null, 2));

    console.log(`Manifest written to ${outFile}`);
    console.log(`  cohort: ${cohortEmails.length} students`);
    console.log(`  enrollment-count tail sample: ${highRows.length} students (top count ${highRows[0].c})`);
    console.log(`  enrollment-count low sample: ${lowRows.length} students`);
    console.log(`  most-enrolled course: ${manifest.mostEnrolledCourseCode} (${heavyCourseRows[0].c} enrollments)`);
  } finally {
    await connection.end();
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
