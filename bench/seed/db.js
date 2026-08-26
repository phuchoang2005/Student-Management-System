// bench/seed/db.js — thin mysql2 wrapper: one connection (nothing here runs concurrently, so a
// pool buys nothing), truncate-or-refuse, batched multi-row insert, and post-load verification.
// Never any DDL -- the generator runs against a schema Flyway has already fully migrated
// (04-workload-data-preparation.md §4.1).

import mysql from 'mysql2/promise';

export async function createConnection() {
  return mysql.createConnection({
    host: process.env.DB_HOST || 'localhost',
    port: Number(process.env.DB_PORT || 3306),
    database: process.env.MYSQL_DATABASE || 'management',
    user: process.env.MYSQL_USER || 'management',
    password: process.env.MYSQL_PASSWORD || 'management',
  });
}

// Leaves before roots -- enrollments/users/books have no children of their own; students/courses
// do. Order among the leaves (or the two roots) doesn't matter, but FOREIGN_KEY_CHECKS is
// disabled around the whole sequence anyway so this is belt-and-suspenders, not load-bearing.
const TRUNCATE_ORDER = ['enrollments', 'users', 'books', 'students', 'courses'];

/**
 * Refuse to run against a non-empty dataset unless --force was passed (safe-by-default, matching
 * this repo's app.demo-accounts.enabled convention). With --force: truncate every table (which
 * also resets AUTO_INCREMENT, making explicit id assignment in generate.js correct and two
 * same-seed runs byte-identical).
 */
export async function refuseOrTruncate(connection, { force }) {
  const [rows] = await connection.query(
    `SELECT ${TRUNCATE_ORDER.map((t) => `(SELECT COUNT(*) FROM ${t}) AS ${t}`).join(', ')}`,
  );
  const counts = rows[0];
  const nonEmpty = TRUNCATE_ORDER.filter((t) => Number(counts[t]) > 0);

  if (nonEmpty.length === 0) {
    return;
  }
  if (!force) {
    const detail = nonEmpty.map((t) => `${t}=${counts[t]}`).join(', ');
    throw new Error(
      `Refusing to seed: ${detail} already has rows. Pass --force to truncate and regenerate.`,
    );
  }

  await connection.query('SET FOREIGN_KEY_CHECKS = 0');
  for (const table of TRUNCATE_ORDER) {
    await connection.query(`TRUNCATE TABLE ${table}`);
  }
  await connection.query('SET FOREIGN_KEY_CHECKS = 1');
}

/** Disable FK/unique checks for the bulk load itself -- safe only because the generator assigns
 * every foreign key from an id it just generated and knows exists, and dedups every unique key in
 * JS before insert (see generate.js). Always paired with restoreChecks() in a finally. */
export async function disableChecks(connection) {
  await connection.query('SET SESSION foreign_key_checks = 0');
  await connection.query('SET SESSION unique_checks = 0');
}

export async function restoreChecks(connection) {
  await connection.query('SET SESSION foreign_key_checks = 1');
  await connection.query('SET SESSION unique_checks = 1');
}

/**
 * Insert `rows` (array of value-arrays, one per row) into `table` in batches of `batchSize`,
 * using mysql2's `VALUES ?` array-expansion (parameterized -- no manual escaping). The whole
 * table's insert runs in one transaction, bounding fsync cost to once per table rather than once
 * per batch.
 */
export async function insertBatched(connection, table, columns, rows, batchSize) {
  if (rows.length === 0) return;
  const sql = `INSERT INTO ${table} (${columns.join(', ')}) VALUES ?`;
  await connection.query('START TRANSACTION');
  try {
    for (let i = 0; i < rows.length; i += batchSize) {
      const batch = rows.slice(i, i + batchSize);
      await connection.query(sql, [batch]);
    }
    await connection.query('COMMIT');
  } catch (err) {
    await connection.query('ROLLBACK');
    throw err;
  }
}

/** SELECT COUNT(*) per table against the scale's declared counts. */
export async function verifyCounts(connection, expectedCounts) {
  const results = {};
  for (const [table, expected] of Object.entries(expectedCounts)) {
    const [rows] = await connection.query(`SELECT COUNT(*) AS c FROM \`${table}\``);
    results[table] = { expected, actual: Number(rows[0].c) };
  }
  return results;
}

// Defense-in-depth per 04-workload-data-preparation.md §1.1: the generation algorithm makes
// duplicates structurally impossible, but this still runs so a future change that breaks that
// invariant is caught rather than silently shipped.
const UNIQUE_CHECKS = [
  { name: 'uq_students_student_code', sql: 'SELECT student_code FROM students GROUP BY student_code HAVING COUNT(*) > 1' },
  { name: 'uq_students_email', sql: 'SELECT email FROM students GROUP BY email HAVING COUNT(*) > 1' },
  { name: 'uq_courses_course_code', sql: 'SELECT course_code FROM courses GROUP BY course_code HAVING COUNT(*) > 1' },
  { name: 'uq_books_isbn', sql: 'SELECT isbn FROM books GROUP BY isbn HAVING COUNT(*) > 1' },
  {
    name: 'uq_enrollments_student_course',
    sql: 'SELECT student_id, course_id FROM enrollments GROUP BY student_id, course_id HAVING COUNT(*) > 1',
  },
  { name: 'uq_users_username', sql: 'SELECT username FROM users GROUP BY username HAVING COUNT(*) > 1' },
  {
    name: 'uq_users_student_id',
    sql: 'SELECT student_id FROM users WHERE student_id IS NOT NULL GROUP BY student_id HAVING COUNT(*) > 1',
  },
];

export async function verifyUniqueConstraints(connection) {
  const results = {};
  for (const check of UNIQUE_CHECKS) {
    const [rows] = await connection.query(check.sql);
    results[check.name] = rows.length;
  }
  return results;
}

/**
 * Observe the real hit-count for a search term, using the exact LIKE shape the corresponding
 * repository uses (SpringDataStudentRepository/SpringDataBookRepository/SpringDataCourseRepository)
 * -- not a JS-side prediction, since MySQL's utf8mb4_0900_ai_ci collation is case/accent-insensitive
 * in a way a naive JS string match would not replicate faithfully.
 */
export async function observeSearchHitCount(connection, entity, term) {
  const queries = {
    student: `SELECT COUNT(*) AS c FROM students WHERE
        student_code LIKE CONCAT('%', ?, '%')
        OR first_name LIKE CONCAT('%', ?, '%')
        OR last_name LIKE CONCAT('%', ?, '%')
        OR email LIKE CONCAT('%', ?, '%')`,
    book: `SELECT COUNT(*) AS c FROM books WHERE
        isbn LIKE CONCAT('%', ?, '%')
        OR title LIKE CONCAT('%', ?, '%')
        OR author LIKE CONCAT('%', ?, '%')`,
    course: `SELECT COUNT(*) AS c FROM courses WHERE
        course_code LIKE CONCAT('%', ?, '%')
        OR name LIKE CONCAT('%', ?, '%')`,
  };
  const sql = queries[entity];
  if (!sql) throw new Error(`Unknown entity for search observation: ${entity}`);
  const paramCount = (sql.match(/\?/g) || []).length;
  const [rows] = await connection.query(sql, Array(paramCount).fill(term));
  return Number(rows[0].c);
}
