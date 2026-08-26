// bench/lib/scenarios.js — BM-* -> SLO class metadata (03-benchmark-scenarios.md), shared by the
// scenario files (to build thresholds via slo.js) and bench/report.js (to render verdicts).
//
// Read-path catalog only (PM-033, Sprint 7). Write/auth/cross-cutting BM-* ids
// (BM-STU-006/007, BM-BK-005, BM-CRS-004, BM-ENR-005-008, BM-IDN-*, BM-XC-*, BM-JMH-*) are
// PM-035/036/037, Sprint 8, and deliberately not listed here.
//
// Keys use underscores (BM_STU_001) so they work unquoted as k6 `options.scenarios` keys and as
// JS identifiers; `officialId(key)` recovers the hyphenated `BM-STU-001` form docs/run-records use.

export const SCENARIOS = {
  BM_STU_001: { sloClass: 'READ_LIST', hazard: 'H3 (control floor)' },
  BM_STU_002: { sloClass: 'READ_LIST', hazard: 'H1' },
  BM_STU_003: { sloClass: 'READ_LIST', hazard: 'H1' },
  BM_STU_004: { sloClass: 'READ_LIST', hazard: 'H3' },
  BM_STU_005: { sloClass: 'READ_SINGLE', hazard: '-- (control)' },

  BM_BK_001: { sloClass: 'READ_LIST', hazard: 'H1' },
  BM_BK_002: { sloClass: 'READ_LIST', hazard: '-- (control)' },
  BM_BK_003: { sloClass: 'READ_LIST', hazard: '-- (non-hazard check)' },
  BM_BK_004: { sloClass: 'READ_SINGLE', hazard: '-- (control)' },

  BM_CRS_001: { sloClass: 'READ_LIST', hazard: 'H1' },
  BM_CRS_002: { sloClass: 'READ_LIST', hazard: '-- (reference point)' },
  BM_CRS_003: { sloClass: 'READ_SINGLE', hazard: '-- (control)' },

  BM_ENR_001: { sloClass: 'READ_LIST', hazard: 'H2' },
  BM_ENR_002: { sloClass: 'READ_LIST', hazard: 'H2 (headline)' },
  BM_ENR_003: { sloClass: 'READ_LIST', hazard: 'H2' },
  BM_ENR_004: { sloClass: 'READ_SINGLE', hazard: '-- (control)' },

  BM_ME_001: { sloClass: 'READ_SINGLE', hazard: '-- (control)' },
  BM_ME_002: { sloClass: 'READ_LIST', hazard: 'H2' },
  BM_ME_003: { sloClass: 'READ_LIST', hazard: '-- (control)' },
};

export const SCENARIO_FILES = {
  'student-search': ['BM_STU_001', 'BM_STU_002', 'BM_STU_003', 'BM_STU_004', 'BM_STU_005'],
  'book-search': ['BM_BK_001', 'BM_BK_002', 'BM_BK_003', 'BM_BK_004'],
  'course-list': ['BM_CRS_001', 'BM_CRS_002', 'BM_CRS_003'],
  'enrollment-list': ['BM_ENR_001', 'BM_ENR_002', 'BM_ENR_003', 'BM_ENR_004'],
  'me-reads': ['BM_ME_001', 'BM_ME_002', 'BM_ME_003'],
};

// The P0 subset re-run at S3 (02-benchmark-plan.md §3 step 6) -- the six scenarios covering
// H1/H2/H3, one per hazard-exposing shape, per 01-benchmark-strategy.md §9.
export const P0_SCENARIOS = [
  'BM_STU_002',
  'BM_STU_004',
  'BM_BK_001',
  'BM_CRS_001',
  'BM_ENR_002',
  'BM_ME_002',
];

export function officialId(key) {
  return key.replace(/_/g, '-');
}
