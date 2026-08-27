#!/usr/bin/env node
// bench/seed/generate.js — deterministic dataset generator (04-workload-data-preparation.md).
//
// Bulk INSERT directly against an already-Flyway-migrated schema (V1->V4), never through the
// API and never any DDL. Usage:
//   node seed/generate.js --scale=S2 --seed=my-seed-value [--force]
//   node seed/generate.js --scale=S4 --seed=x --students=200000 --courses=2000 --books=300000 --enrollments=1500000

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import bcrypt from 'bcryptjs';

import { SCALES, BATCH_SIZE, VOCABULARY, VOCABULARY_WEIGHTS } from './scales.js';
import { createRng } from './rng.js';
import {
  buildZipfWeights,
  sampleEnrollmentCount,
  sampleWeightedDistinct,
  sampleWeightedIndex,
  fisherYatesShuffle,
  pickWeightedToken,
} from './sampling.js';
import {
  createConnection,
  refuseOrTruncate,
  disableChecks,
  restoreChecks,
  insertBatched,
  verifyCounts,
  verifyUniqueConstraints,
  observeSearchHitCount,
} from './db.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.join(__dirname, '..', 'out');

const STAFF_USERNAMES = {
  REGISTRAR: 'bench.registrar',
  LIBRARIAN: 'bench.librarian',
  COURSE_ADMINISTRATOR: 'bench.course_administrator',
  SYSTEM_ADMINISTRATOR: 'bench.system_administrator',
};

function parseArgs(argv) {
  const args = { scale: null, seed: null, force: false };
  for (const arg of argv) {
    if (arg === '--force') args.force = true;
    else if (arg.startsWith('--scale=')) args.scale = arg.slice('--scale='.length);
    else if (arg.startsWith('--seed=')) args.seed = arg.slice('--seed='.length);
    else if (arg.startsWith('--students=')) args.students = Number(arg.slice('--students='.length));
    else if (arg.startsWith('--courses=')) args.courses = Number(arg.slice('--courses='.length));
    else if (arg.startsWith('--books=')) args.books = Number(arg.slice('--books='.length));
    else if (arg.startsWith('--enrollments=')) args.enrollments = Number(arg.slice('--enrollments='.length));
  }
  if (!args.scale) throw new Error('Missing --scale=S1|S2|S3|S4');
  if (!args.seed) {
    throw new Error(
      'Missing --seed=<value>. Every dataset must come from a recorded seed (04-workload-data-preparation.md §3).',
    );
  }
  return args;
}

function resolveScale(args) {
  if (args.scale === 'S4') {
    for (const key of ['students', 'courses', 'books', 'enrollments']) {
      if (!args[key]) {
        throw new Error(`S4 is open-ended (04-workload-data-preparation.md §1) -- pass --${key}=<count> explicitly.`);
      }
    }
    return {
      label: 'S4 — Probe',
      students: args.students,
      courses: args.courses,
      books: args.books,
      enrollments: args.enrollments,
      cohortSize: Math.min(300, args.students),
      zipfExponent: 1.1,
      enrollmentMixture: { tailProbability: 0.1, lowRange: [2, 7], highRange: [15, 20] },
      bookOwnerNullRate: 0.25,
    };
  }
  const scale = SCALES[args.scale];
  if (!scale) {
    throw new Error(`Unknown scale "${args.scale}". Valid: ${Object.keys(SCALES).join(', ')}, S4`);
  }
  return scale;
}

function slugify(text) {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, '');
}

function randomDateString(rng, startStr, endStr) {
  const start = new Date(startStr).getTime();
  const end = new Date(endStr).getTime();
  return new Date(start + rng() * (end - start)).toISOString().slice(0, 10);
}

function flattenTiers(tieredPool) {
  return Object.values(tieredPool).flat();
}

function generateCourses(count, rng) {
  const courses = [];
  for (let i = 0; i < count; i++) {
    const id = i + 1;
    const word = pickWeightedToken(VOCABULARY.courseWords, VOCABULARY_WEIGHTS, rng);
    const subject = pickWeightedToken(VOCABULARY.subjects, VOCABULARY_WEIGHTS, rng);
    courses.push({
      id,
      code: `CRS${String(id).padStart(5, '0')}`,
      name: `${word} to ${subject}`.slice(0, 150),
      credits: 1 + Math.floor(rng() * 6),
    });
  }
  return courses;
}

function generateStudents(count, rng, shuffleRng) {
  const students = [];
  for (let i = 0; i < count; i++) {
    const id = i + 1;
    const firstName = pickWeightedToken(VOCABULARY.firstNames, VOCABULARY_WEIGHTS, rng);
    const lastName = pickWeightedToken(VOCABULARY.lastNames, VOCABULARY_WEIGHTS, rng);
    const email = `${slugify(firstName)}.${slugify(lastName)}.${id}@example.test`;
    students.push({
      id,
      firstName,
      lastName,
      email,
      dob: randomDateString(rng, '1998-01-01', '2008-12-31'),
    });
  }

  // Insertion order must not match student_code sort order (04-workload-data-preparation.md §2):
  // generate codes in sorted order, shuffle the pool, then assign shuffled[i] to the student
  // inserted at id = i+1 -- the physical/insertion order stays id order, but that no longer
  // matches the code's own sort order.
  const codePool = students.map((s) => `STU${String(s.id).padStart(6, '0')}`);
  fisherYatesShuffle(codePool, shuffleRng);
  students.forEach((s, idx) => {
    s.studentCode = codePool[idx];
  });

  return students;
}

function generateBooks(count, students, ownerNullRate, ownerWeights, rng) {
  const books = [];
  for (let i = 0; i < count; i++) {
    const titleWord = pickWeightedToken(VOCABULARY.bookWords, VOCABULARY_WEIGHTS, rng);
    const subject = pickWeightedToken(VOCABULARY.subjects, VOCABULARY_WEIGHTS, rng);
    const authorFirst = pickWeightedToken(VOCABULARY.firstNames, VOCABULARY_WEIGHTS, rng);
    const authorLast = pickWeightedToken(VOCABULARY.lastNames, VOCABULARY_WEIGHTS, rng);

    let ownerId = null;
    if (rng() >= ownerNullRate) {
      const idx = sampleWeightedIndex(ownerWeights.cumulative, ownerWeights.total, rng);
      ownerId = students[idx].id;
    }

    books.push({
      isbn: `BK${String(i + 1).padStart(12, '0')}`,
      title: `${titleWord} of ${subject}`.slice(0, 255),
      author: `${authorFirst} ${authorLast}`.slice(0, 255),
      publishedDate: randomDateString(rng, '1980-01-01', '2024-12-31'),
      ownerId,
    });
  }
  return books;
}

function generateEnrollments(students, courses, mixture, courseWeights, rng) {
  const rows = [];
  const countByStudent = new Map();

  for (const student of students) {
    const k = sampleEnrollmentCount(rng, mixture);
    const courseIndices = sampleWeightedDistinct(k, courses.length, courseWeights.cumulative, courseWeights.total, rng);
    countByStudent.set(student.id, courseIndices.length);
    for (const idx of courseIndices) {
      rows.push({ studentId: student.id, courseId: courses[idx].id });
    }
  }

  // Defense-in-depth (04-workload-data-preparation.md §1.1): structurally impossible to collide
  // given distinct-per-student sampling above, but checked anyway rather than assumed.
  const seen = new Set();
  for (const row of rows) {
    const key = `${row.studentId}-${row.courseId}`;
    if (seen.has(key)) {
      throw new Error(`Duplicate enrollment pair generated: student ${row.studentId}, course ${row.courseId}`);
    }
    seen.add(key);
  }

  return { rows, countByStudent };
}

function selectCohort(students, countByStudent, cohortSize) {
  const sorted = [...students].sort((a, b) => countByStudent.get(a.id) - countByStudent.get(b.id));
  const size = Math.min(cohortSize, sorted.length);
  const start = Math.max(0, Math.floor(sorted.length / 2 - size / 2));
  return sorted.slice(start, start + size);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const scale = resolveScale(args);
  const startedAt = Date.now();

  console.log(`Seeding ${args.scale} (${scale.label}) with seed "${args.seed}"...`);

  // Shared bcrypt hash for the whole account cohort -- generated once and reused, which is safe
  // because BCrypt salts are per-hash (04-workload-data-preparation.md §4.2). Confirmed
  // interoperable with Spring Security's BCryptPasswordEncoder (its $2a$ hashes verify directly
  // against bcryptjs-generated hashes and vice versa) -- not merely assumed.
  const cohortPassword = process.env.COHORT_PASSWORD || 'Benchmark123!';
  const passwordHash = bcrypt.hashSync(cohortPassword, 10);
  if (!bcrypt.compareSync(cohortPassword, passwordHash)) {
    throw new Error('bcryptjs self-check failed: generated hash does not verify against its own plaintext.');
  }

  const connection = await createConnection();
  try {
    await refuseOrTruncate(connection, { force: args.force });
    await disableChecks(connection);

    const courseRng = createRng(args.seed, 'courses');
    const courses = generateCourses(scale.courses, courseRng);
    await insertBatched(
      connection,
      'courses',
      ['id', 'course_code', 'name', 'description', 'credits', 'version'],
      // version=1, not 0: StudentRow.java's @Version comment explains why -- Spring Data JDBC's
      // default IsNewStrategy for a primitive `long version` treats 0 as "never persisted," which
      // makes the app's first UPDATE/DELETE against a bulk-seeded row issue an INSERT instead and
      // collide on the row's own PK (discovered running BM-CRS-004/BM-STU-007/BM-BK-005 for the
      // first time). 1 matches what the app itself sets after a real first insert.
      courses.map((c) => [c.id, c.code, c.name, null, c.credits, 1]),
      BATCH_SIZE.courses,
    );
    console.log(`  courses: ${courses.length} inserted`);

    const studentRng = createRng(args.seed, 'students');
    const shuffleRng = createRng(args.seed, 'shuffle');
    const students = generateStudents(scale.students, studentRng, shuffleRng);
    await insertBatched(
      connection,
      'students',
      ['id', 'student_code', 'first_name', 'last_name', 'email', 'date_of_birth', 'version'],
      // version=1 -- see the comment on the courses insert above.
      students.map((s) => [s.id, s.studentCode, s.firstName, s.lastName, s.email, s.dob, 1]),
      BATCH_SIZE.students,
    );
    console.log(`  students: ${students.length} inserted`);

    const bookRng = createRng(args.seed, 'books');
    const ownerWeights = buildZipfWeights(students.length, scale.zipfExponent);
    const books = generateBooks(scale.books, students, scale.bookOwnerNullRate, ownerWeights, bookRng);
    await insertBatched(
      connection,
      'books',
      ['isbn', 'title', 'author', 'published_date', 'owner_id', 'version'],
      // version=1 -- see the comment on the courses insert above.
      books.map((b) => [b.isbn, b.title, b.author, b.publishedDate, b.ownerId, 1]),
      BATCH_SIZE.books,
    );
    console.log(`  books: ${books.length} inserted`);

    const enrollmentRng = createRng(args.seed, 'enrollments');
    const courseWeights = buildZipfWeights(courses.length, scale.zipfExponent);
    const { rows: enrollmentRows, countByStudent } = generateEnrollments(
      students,
      courses,
      scale.enrollmentMixture,
      courseWeights,
      enrollmentRng,
    );
    await insertBatched(
      connection,
      'enrollments',
      ['student_id', 'course_id'],
      enrollmentRows.map((r) => [r.studentId, r.courseId]),
      BATCH_SIZE.enrollments,
    );
    console.log(`  enrollments: ${enrollmentRows.length} inserted`);

    const cohort = selectCohort(students, countByStudent, scale.cohortSize);
    const userRows = [
      // version=1 -- see the comment on the courses insert above (identity's UserRow uses the same
      // primitive `long version` convention).
      ...cohort.map((s) => [s.email, passwordHash, null, 'STUDENT', s.id, false, true, 1]),
      ...Object.entries(STAFF_USERNAMES).map(([role, username]) => [
        username,
        passwordHash,
        null,
        role,
        null,
        false,
        true,
        1,
      ]),
    ];
    await insertBatched(
      connection,
      'users',
      ['username', 'password_hash', 'initial_password_encrypted', 'role', 'student_id', 'must_change_password', 'enabled', 'version'],
      userRows,
      BATCH_SIZE.users,
    );
    console.log(`  users: ${userRows.length} inserted (${cohort.length} student cohort + ${Object.keys(STAFF_USERNAMES).length} staff)`);

    await restoreChecks(connection);

    const counts = await verifyCounts(connection, {
      courses: courses.length,
      students: students.length,
      books: books.length,
      enrollments: enrollmentRows.length,
      users: userRows.length,
    });
    const duplicates = await verifyUniqueConstraints(connection);

    console.log('\nVerification:');
    for (const [table, { expected, actual }] of Object.entries(counts)) {
      const flag = actual === expected ? 'OK' : 'MISMATCH';
      console.log(`  ${table}: expected ~${expected}, actual ${actual} [${flag}]`);
    }
    const duplicateProblems = Object.entries(duplicates).filter(([, count]) => count > 0);
    if (duplicateProblems.length > 0) {
      console.error('  Duplicate groups found (should be none):', duplicateProblems);
      throw new Error('Uniqueness verification failed -- see duplicate groups above.');
    }
    console.log('  no duplicate groups on any unique constraint [OK]');

    // Observe real search-term hit counts (matching each repository's exact LIKE shape) rather
    // than predicting them in JS (04-workload-data-preparation.md §2).
    const studentTerms = [...flattenTiers(VOCABULARY.firstNames), ...flattenTiers(VOCABULARY.lastNames), ...VOCABULARY.neverUsed];
    const bookTerms = [
      ...flattenTiers(VOCABULARY.bookWords),
      ...flattenTiers(VOCABULARY.subjects),
      ...flattenTiers(VOCABULARY.firstNames),
      ...flattenTiers(VOCABULARY.lastNames),
      ...VOCABULARY.neverUsed,
    ];
    const courseTerms = [...flattenTiers(VOCABULARY.courseWords), ...flattenTiers(VOCABULARY.subjects), ...VOCABULARY.neverUsed];

    const searchTermTable = { student: {}, book: {}, course: {} };
    for (const term of new Set(studentTerms)) {
      searchTermTable.student[term] = await observeSearchHitCount(connection, 'student', term);
    }
    for (const term of new Set(bookTerms)) {
      searchTermTable.book[term] = await observeSearchHitCount(connection, 'book', term);
    }
    for (const term of new Set(courseTerms)) {
      searchTermTable.course[term] = await observeSearchHitCount(connection, 'course', term);
    }

    fs.mkdirSync(OUT_DIR, { recursive: true });
    const outFile = path.join(OUT_DIR, `${args.scale}-search-terms.json`);
    fs.writeFileSync(
      outFile,
      JSON.stringify({ scale: args.scale, seed: args.seed, generatedAt: new Date().toISOString(), searchTermTable }, null, 2),
    );
    console.log(`  search-term hit-count table written to ${outFile}`);

    const cohortSample = cohort.slice(0, 3).map((s) => s.email);
    const elapsedSeconds = ((Date.now() - startedAt) / 1000).toFixed(1);
    console.log(`\nDone in ${elapsedSeconds}s. Seed: ${args.seed}`);
    console.log(`Cohort password: ${cohortPassword}`);
    console.log(`Sample cohort logins (STUDENT_USERNAME): ${cohortSample.join(', ')}`);
    console.log(`Staff logins: ${Object.values(STAFF_USERNAMES).join(', ')}`);
  } finally {
    await connection.end();
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
