// bench/lib/scenarios.js — BM-* -> SLO class metadata (03-benchmark-scenarios.md), shared by the
// scenario files (to build thresholds via slo.js) and bench/report.js (to render verdicts).
//
// Read-path catalog (PM-033, Sprint 7) plus write/auth/cross-cutting ids (PM-035/036, Sprint 8).
// PM-037's BM-JMH-* ids are still deliberately not listed here (separate suite, separate runner).
//
// Keys use underscores (BM_STU_001) so they work unquoted as k6 `options.scenarios` keys and as
// JS identifiers; `officialId(key)` recovers the hyphenated `BM-STU-001` form docs/run-records use.
//
// Every entry carries `vus`: the VU count 03-benchmark-scenarios.md specifies for that id (20 for
// every PM-033 read, since "unless stated otherwise" defaults to 20 -- §1). bench/report.js reads
// this instead of a hardcoded literal so scenarios that specify a different count (BM-STU-006's 5,
// BM-IDN-001's 1/10/25/50/100 ramp, ...) render correctly.

export const SCENARIOS = {
  BM_STU_001: { sloClass: 'READ_LIST', hazard: 'H3 (control floor)', vus: 20 },
  BM_STU_002: { sloClass: 'READ_LIST', hazard: 'H1', vus: 20 },
  BM_STU_003: { sloClass: 'READ_LIST', hazard: 'H1', vus: 20 },
  BM_STU_004: { sloClass: 'READ_LIST', hazard: 'H3', vus: 20 },
  BM_STU_005: { sloClass: 'READ_SINGLE', hazard: '-- (control)', vus: 20 },
  // BM-STU-006/007, PM-035: registration (H5 -- pays BCrypt+AES-GCM) and update (control), both
  // written distinct-target-per-iteration so no VU ever contends with another (bench/lib/vuShard.js).
  BM_STU_006: { sloClass: 'WRITE_SIMPLE', hazard: 'H5', vus: 5 },
  BM_STU_007: { sloClass: 'WRITE_SIMPLE', hazard: '-- (control)', vus: 10 },

  BM_BK_001: { sloClass: 'READ_LIST', hazard: 'H1', vus: 20 },
  BM_BK_002: { sloClass: 'READ_LIST', hazard: '-- (control)', vus: 20 },
  BM_BK_003: { sloClass: 'READ_LIST', hazard: '-- (non-hazard check)', vus: 20 },
  BM_BK_004: { sloClass: 'READ_SINGLE', hazard: '-- (control)', vus: 20 },
  // BM-BK-005, PM-035: owner assignment (control -- one StudentLookup.idOf plus one versioned write).
  BM_BK_005: { sloClass: 'WRITE_SIMPLE', hazard: '-- (control)', vus: 10 },

  BM_CRS_001: { sloClass: 'READ_LIST', hazard: 'H1', vus: 20 },
  BM_CRS_002: { sloClass: 'READ_LIST', hazard: '-- (reference point)', vus: 20 },
  BM_CRS_003: { sloClass: 'READ_SINGLE', hazard: '-- (control)', vus: 20 },
  // BM-CRS-004, PM-035: delete (H6 -- client-visible latency only; BM-XC-001 measures the cascade
  // this triggers). Must run last within writes.js -- it destroys the courses other entries need.
  BM_CRS_004: { sloClass: 'WRITE_SIMPLE', hazard: 'H6', vus: 5 },

  BM_ENR_001: { sloClass: 'READ_LIST', hazard: 'H2', vus: 20 },
  BM_ENR_002: { sloClass: 'READ_LIST', hazard: 'H2 (headline)', vus: 20 },
  BM_ENR_003: { sloClass: 'READ_LIST', hazard: 'H2', vus: 20 },
  BM_ENR_004: { sloClass: 'READ_SINGLE', hazard: '-- (control)', vus: 20 },
  // BM-ENR-005-008, PM-035: single-enrollment unit (005) vs. batch at 1/10/50 courses (006-008,
  // H4) -- 007/008 cross into the Batch-50 SLO class since their per-request statement/commit count
  // (~2N+2) is what that class exists to hold to a looser bar than a single-aggregate write.
  BM_ENR_005: { sloClass: 'WRITE_SIMPLE', hazard: '-- (unit baseline)', vus: 10 },
  BM_ENR_006: { sloClass: 'WRITE_SIMPLE', hazard: 'H4 (degenerate batch, 1 course)', vus: 5 },
  BM_ENR_007: { sloClass: 'BATCH_50', hazard: 'H4 (midpoint, 10 courses)', vus: 5 },
  BM_ENR_008: { sloClass: 'BATCH_50', hazard: 'H4 (worst case, 50 courses)', vus: 5 },

  BM_ME_001: { sloClass: 'READ_SINGLE', hazard: '-- (control)', vus: 20 },
  BM_ME_002: { sloClass: 'READ_LIST', hazard: 'H2', vus: 20 },
  BM_ME_003: { sloClass: 'READ_LIST', hazard: '-- (control)', vus: 20 },

  // BM-IDN-001, PM-035: the login ramp, held at each step -- five distinct k6 scenario keys so
  // report.js and a result record can show the curve, not just its last point. Runs alone
  // (`make bench-auth-ramp`) -- see Makefile.
  BM_IDN_001_VU01: { sloClass: 'LOGIN', hazard: 'H5 (ramp step 1/5)', vus: 1 },
  BM_IDN_001_VU10: { sloClass: 'LOGIN', hazard: 'H5 (ramp step 2/5)', vus: 10 },
  BM_IDN_001_VU25: { sloClass: 'LOGIN', hazard: 'H5 (ramp step 3/5)', vus: 25 },
  BM_IDN_001_VU50: { sloClass: 'LOGIN', hazard: 'H5 (ramp step 4/5)', vus: 50 },
  BM_IDN_001_VU100: { sloClass: 'LOGIN', hazard: 'H5 (ramp step 5/5)', vus: 100 },
  // BM-IDN-002, PM-035: wrong-password login. H5-shaped cost, but a measurable known-vs-unknown-
  // username timing delta is a user-enumeration finding for the security channel, not this one --
  // see auth-login.js's header comment.
  BM_IDN_002: { sloClass: 'LOGIN', hazard: 'H5 (see security note in auth-login.js)', vus: 20 },
  BM_IDN_003: { sloClass: 'READ_LIST', hazard: '-- (control)', vus: 10 },
  // BM-IDN-004, PM-036: /sessions read, meant to run *during* the BM-XC-002 soak (mixed-soak.js
  // drives it as one of its concurrent executors) -- cost tracks live session count (H7).
  BM_IDN_004: { sloClass: 'READ_LIST', hazard: 'H7', vus: 10 },

  // BM-XC-001, PM-036: bulk student deletion at N=10/50/200. sloClass covers the HTTP-visible
  // latency only -- the cascade completion (wall-clock to event_publication drain, H6) is measured
  // separately by bench/seed/cascade-drain.js, which k6 itself cannot query MySQL to do.
  BM_XC_001_N10: { sloClass: 'WRITE_SIMPLE', hazard: 'H6 (N=10)', vus: 10 },
  BM_XC_001_N50: { sloClass: 'WRITE_SIMPLE', hazard: 'H6 (N=50)', vus: 20 },
  BM_XC_001_N200: { sloClass: 'WRITE_SIMPLE', hazard: 'H6 (N=200)', vus: 20 },
};

export const SCENARIO_FILES = {
  'student-search': ['BM_STU_001', 'BM_STU_002', 'BM_STU_003', 'BM_STU_004', 'BM_STU_005'],
  'book-search': ['BM_BK_001', 'BM_BK_002', 'BM_BK_003', 'BM_BK_004'],
  'course-list': ['BM_CRS_001', 'BM_CRS_002', 'BM_CRS_003'],
  'enrollment-list': ['BM_ENR_001', 'BM_ENR_002', 'BM_ENR_003', 'BM_ENR_004'],
  'me-reads': ['BM_ME_001', 'BM_ME_002', 'BM_ME_003'],
  'writes': ['BM_STU_006', 'BM_STU_007', 'BM_BK_005', 'BM_CRS_004'],
  'enrollment-batch': ['BM_ENR_005', 'BM_ENR_006', 'BM_ENR_007', 'BM_ENR_008'],
  'auth-login': [
    'BM_IDN_001_VU01',
    'BM_IDN_001_VU10',
    'BM_IDN_001_VU25',
    'BM_IDN_001_VU50',
    'BM_IDN_001_VU100',
    'BM_IDN_002',
    'BM_IDN_003',
  ],
  'cascade-delete': ['BM_XC_001_N10', 'BM_XC_001_N50', 'BM_XC_001_N200'],
  // mixed-soak's own concurrent executors are reported separately (bench-report has no per-(id,
  // VUS)/soak-shaped rendering) -- see bench/monitor-soak.js's own output and the note in
  // mixed-soak.js's header. BM_IDN_004 is listed under 'auth-login' style consumers elsewhere; it
  // isn't repeated in a 'mixed-soak' SCENARIO_FILES entry since bench-report's one-row-per-id model
  // doesn't fit a 30-minute soak's read anyway.
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
