// bench/lib/slo.js — the proposed Service Level Objectives (01-benchmark-strategy.md §4.2) as
// reusable k6 threshold objects. These are proposals, not requirements (no NFR exists anywhere in
// BA-docs/SA-docs) -- revising them is a documentation change, not a failure. What matters is that
// every scenario asserts against the *same* named classes, so a verdict means the same thing
// wherever it's read.

export const SLO_CLASSES = {
  // GET by business key: /students/{code}, /books/{isbn}, /courses/{code}
  READ_SINGLE: { p95: 50, p99: 120 },
  // GET search, page 1, size 20
  READ_LIST: { p95: 150, p99: 300 },
  // POST/PUT/PATCH/DELETE on one aggregate
  WRITE_SIMPLE: { p95: 200, p99: 400 },
  // POST /api/v1/auth/login -- its own class because BCrypt's cost (H5) is a deliberate security
  // property, not a defect a Write-simple target would be fair to hold it to.
  LOGIN: { p95: 400, p99: 800 },
  // POST /api/v1/enrollments/batch, 50 courses -- its own class for the same reason (H4).
  BATCH_50: { p95: 2000, p99: 4000 },
};

// Error rate < 0.1% in any scenario, at any scale (01-benchmark-strategy.md §4.2). Errors under
// load are a correctness finding, not a performance one.
export const MAX_ERROR_RATE = 0.001;

/**
 * Build the k6 `thresholds` entries for one scenario tag against one SLO class, e.g.:
 *   sloThresholds('student_search', 'READ_LIST')
 * A scenario file merges these into its own `options.thresholds` (see mergeThresholds below)
 * rather than hardcoding percentile targets inline.
 *
 * `dimension` defaults to 'scenario' (k6's auto-tag from an `options.scenarios` key, what every
 * PM-033 file keys its thresholds on via buildOptions()). mixed-soak.js (Sprint 8) needs 'name'
 * instead: it reuses other files' exec functions inside its own role-partitioned executors, so
 * every request there is auto-tagged `scenario:<executor>` (e.g. `registrar_mix`), not
 * `scenario:BM_STU_002` -- only the reused function's own `tags: { name: 'BM_STU_002' }` still
 * identifies which endpoint shape a request belongs to.
 */
export function sloThresholds(tag, className, dimension = 'scenario') {
  const slo = SLO_CLASSES[className];
  if (!slo) {
    throw new Error(`Unknown SLO class: ${className}. Valid classes: ${Object.keys(SLO_CLASSES)}`);
  }
  return {
    [`http_req_duration{${dimension}:${tag}}`]: [`p(95)<${slo.p95}`, `p(99)<${slo.p99}`],
    [`http_req_failed{${dimension}:${tag}}`]: [`rate<${MAX_ERROR_RATE}`],
  };
}

/**
 * Combine several sloThresholds(...) results (or any threshold objects) into one, for scenario
 * files that assert more than one tagged request shape.
 */
export function mergeThresholds(...thresholdObjects) {
  return Object.assign({}, ...thresholdObjects);
}
