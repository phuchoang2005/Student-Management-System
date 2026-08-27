// bench/lib/config.js — base URL, scale selection, VU profiles, and role credentials, all from
// k6's __ENV. No hardcoded target: every value here is meant to be overridden per run via
// `k6 run --env NAME=value ...` (02-benchmark-plan.md §1).

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// One dataset scale per run (02-benchmark-plan.md §2.3) — never mixed. Scenarios read this to
// pick scale-appropriate think-times/expectations, not to change which host they hit.
export const SCALE = __ENV.SCALE || 'S1';

export const VUS = Number(__ENV.VUS || 20);
export const DURATION = __ENV.DURATION || '30s';

// Warm-up/steady-state/cool-down shape from 02-benchmark-plan.md §2.1. A scenario file builds its
// own `options.stages` from these rather than hardcoding phase lengths inline.
export const WARMUP_DURATION = __ENV.WARMUP_DURATION || '15s';
export const COOLDOWN_DURATION = __ENV.COOLDOWN_DURATION || '5s';

// Staff usernames are fixed by the seed generator's account cohort (bench/seed/generate.js) — one
// per role, free-form strings (Username has no format requirement for non-STUDENT accounts). A
// STUDENT username is NOT fixed: the generator creates a whole cohort of real students, each
// keyed by their own email, so the caller must supply one (e.g. from the generator's printed
// cohort sample) via STUDENT_USERNAME.
const DEFAULT_STAFF_USERNAMES = {
  REGISTRAR: 'bench.registrar',
  LIBRARIAN: 'bench.librarian',
  COURSE_ADMINISTRATOR: 'bench.course_administrator',
  SYSTEM_ADMINISTRATOR: 'bench.system_administrator',
};

// Shared plaintext password across the whole account cohort (staff + bulk-seeded students) —
// documented in bench/README.md. Not a real secret: this only ever exists in throwaway benchmark
// data (04-workload-data-preparation.md §6).
const DEFAULT_COHORT_PASSWORD = 'Benchmark123!';

/**
 * Resolve login credentials for a role, from env vars with cohort-matching defaults:
 *   {ROLE}_USERNAME / {ROLE}_PASSWORD, e.g. STUDENT_USERNAME, REGISTRAR_PASSWORD.
 * Falls back to COHORT_PASSWORD (or the built-in default) for password, and to the fixed staff
 * username for any non-STUDENT role.
 */
export function credentialsFor(role) {
  const key = role.toUpperCase();
  const username = __ENV[`${key}_USERNAME`] || DEFAULT_STAFF_USERNAMES[key];
  const password =
    __ENV[`${key}_PASSWORD`] || __ENV.COHORT_PASSWORD || DEFAULT_COHORT_PASSWORD;

  if (!username) {
    throw new Error(
      `No username configured for role ${role}. Set ${key}_USERNAME (e.g. one of the ` +
        'account-cohort students printed by bench/seed/generate.js).',
    );
  }
  return { username, password };
}
