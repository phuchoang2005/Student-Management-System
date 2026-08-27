// bench/seed/scales.js — S1-S4 row counts and distribution parameters
// (docs/benchmark-strategy/04-workload-data-preparation.md §1-2).
//
// Cohort size deliberately follows §4.2's prose ("a few hundred students... plus one staff
// account per role") rather than the summary table's rough `users` column (~55/~5,010/~50,010),
// which is only approximate and does not itself specify a cohort size. A cohort of "a few hundred"
// students that can actually log in is what the login/`/me/*` scenarios (BM-ME-*, BM-IDN-*) need;
// a cohort sized to the table's approximation (~10 students at S2/S3) would be too small to be a
// representative sample of "a typical student" as §4.2 asks for.

export const SCALES = {
  // Each scale's enrollmentMixture.lowRange is tuned so the expected total lands near that
  // scale's declared enrollment count (04-workload-data-preparation.md §1), while tailProbability
  // and highRange stay fixed at the shape §2 specifies (a ~10% tail carrying 15-20 courses).
  // Expected mean = 0.9*mean(lowRange) + 0.1*mean(highRange); highRange mean is 17.5 throughout.
  S1: {
    label: 'S1 — Demo',
    students: 50,
    courses: 20,
    books: 100,
    enrollments: 150,
    cohortSize: 20,
    zipfExponent: 1.1,
    // expected mean ≈ 0.9*1.5 + 0.1*17.5 = 3.1 → 50 students × 3.1 ≈ 155, close to the declared 150.
    enrollmentMixture: { tailProbability: 0.1, lowRange: [1, 2], highRange: [15, 20] },
    bookOwnerNullRate: 0.25,
  },
  S2: {
    label: 'S2 — Institution',
    students: 5000,
    courses: 300,
    books: 8000,
    enrollments: 30000,
    cohortSize: 300,
    zipfExponent: 1.1,
    // expected mean ≈ 0.9*4.5 + 0.1*17.5 = 5.8, matching §2's "mean ~6" verbatim -- S2 is the
    // scale the SLOs (and this prose) are written for.
    enrollmentMixture: { tailProbability: 0.1, lowRange: [2, 7], highRange: [15, 20] },
    bookOwnerNullRate: 0.25,
  },
  S3: {
    label: 'S3 — Stress',
    students: 50000,
    courses: 1000,
    books: 80000,
    enrollments: 400000,
    cohortSize: 300,
    zipfExponent: 1.1,
    // expected mean ≈ 0.9*7 + 0.1*17.5 = 8.05 → 50000 × 8.05 ≈ 402,500, close to the declared 400,000.
    enrollmentMixture: { tailProbability: 0.1, lowRange: [4, 10], highRange: [15, 20] },
    bookOwnerNullRate: 0.25,
  },
  // S4 is deliberately open-ended (04-workload-data-preparation.md §1) -- run it only when a
  // specific question needs it, by passing --students/--courses/--books overrides on the CLI
  // rather than picking fixed numbers here.
};

// Insert batch sizes (bench/seed/db.js) -- narrow tables (enrollments, users) can afford more rows
// per multi-row INSERT than wide ones (students, courses, books) before approaching
// max_allowed_packet.
export const BATCH_SIZE = {
  students: 500,
  courses: 500,
  books: 500,
  enrollments: 1000,
  users: 1000,
};

// Search vocabulary: tiered by selection *weight*, not by a predetermined exact count. True
// per-term hit counts are OBSERVED after loading, via the same LIKE-based query shape the real
// repositories use (SpringDataStudentRepository/SpringDataBookRepository/SpringDataCourseRepository),
// and written to bench/out/<scale>-search-terms.json. This sidesteps any mismatch between a
// JS-predicted count and MySQL's actual (case/accent-insensitive, utf8mb4_0900_ai_ci) collation.
export const VOCABULARY = {
  firstNames: {
    common: ['Alex', 'Taylor', 'Morgan', 'Jordan'],
    medium: ['Harper', 'Riley', 'Casey', 'Quinn', 'Reese', 'Avery', 'Rowan'],
    rare: ['Zephyrine', 'Wrenlow', 'Osgood', 'Fenwick', 'Marigold'],
  },
  lastNames: {
    common: ['Smith', 'Johnson', 'Nguyen', 'Garcia'],
    medium: ['Patel', 'Kim', 'Rossi', 'Dubois', 'Andersen', 'Okafor'],
    rare: ['Quintrell', 'Ashgrove', 'Larkspur', 'Thornbury', 'Vesparelli'],
  },
  courseWords: {
    common: ['Introduction', 'Fundamentals'],
    medium: ['Advanced', 'Applied', 'Seminar', 'Workshop'],
    rare: ['Interdisciplinary', 'Praxis'],
  },
  subjects: {
    common: ['Biology', 'Mathematics'],
    medium: ['Chemistry', 'Literature', 'Economics', 'Physics', 'History'],
    rare: ['Cryptography', 'Ethnomusicology'],
  },
  bookWords: {
    common: ['Guide', 'Handbook'],
    medium: ['Principles', 'Essentials', 'Companion', 'Survey'],
    rare: ['Compendium', 'Almanac'],
  },
  // Never inserted anywhere -- guaranteed zero-hit search terms, recorded as such rather than
  // assumed (a term that matches nothing is still worth confirming empirically).
  neverUsed: ['Quetzalcoatl', 'Xylophonique', 'Zzyzxville'],
};

// Relative selection weight per tier -- common terms should dominate the generated text, rare
// terms should surface in only a handful of rows.
export const VOCABULARY_WEIGHTS = { common: 8, medium: 2, rare: 1 };
