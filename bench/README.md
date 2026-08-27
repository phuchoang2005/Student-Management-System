# bench/

The k6 load-test harness and deterministic dataset generator for the benchmark suite specified in
[`docs/benchmark-strategy/`](../docs/benchmark-strategy/). A new top-level directory, sibling of
`management/`, `management-frontend/`, `docs/`, and `util/` — deliberately outside
`management/src/`.

## Why this lives outside `management/`

`management/src/` is governed by ArchUnit layering rules, Spring Modulith boundary verification,
and naming conventions enforced at build time (`CLAUDE.md`). A JavaScript harness in `bench/`
cannot violate any of them, cannot slow `./mvnw verify`, and cannot accidentally become a
dependency of the application (`01-benchmark-strategy.md` §6.1). k6 was chosen over Gatling/JMeter
specifically so this code could live here instead of as a Maven module.

## Layout

```
bench/
├── package.json         npm project for seed/ only — lib/ needs no npm packages at all
├── lib/
│   ├── config.js          base URL, scale selection, VU profiles, role credentials — all from env vars
│   ├── session.js         login/loginAs/assertLive — the primitives ensureLoggedIn(As) builds on
│   ├── vuSession.js       ensureLoggedIn(As): log in once per VU, re-login only on a role switch
│   ├── vuShard.js         shardFor()/uniqueCode() — collision-free write targets across VUs (PM-035/036)
│   ├── manifest.js        loads the per-scale manifest/search-term JSON (open(), init-context only)
│   ├── scenarios.js       BM-* id -> SLO class/vus registry + SCENARIO_FILES, read by runner.js/report.js
│   ├── runner.js          buildOptions(): the shared sequential-scenario/threshold builder
│   ├── slo.js             the proposed SLO classes as reusable k6 threshold objects
│   └── reportStats.js     shared k6 --summary-export parsing, reused by report.js/scale-sweep.js/xc003-report.js
├── scenarios/
│   ├── student-search.js, book-search.js, course-list.js, enrollment-list.js, me-reads.js   (PM-033, reads)
│   ├── writes.js, enrollment-batch.js, auth-login.js                                        (PM-035, write/auth)
│   └── cascade-delete.js, mixed-soak.js                                                     (PM-036, cross-cutting)
├── seed/
│   ├── scales.js          S1–S4 row counts and distribution parameters
│   ├── rng.js             seeded PRNG (mulberry32), independent named substreams
│   ├── sampling.js        weighted course selection, enrollment-count mixture, Fisher–Yates shuffle
│   ├── db.js              mysql2 wrapper: truncate, batched insert, post-load verification
│   ├── generate.js        entry point — run this to seed a dataset
│   ├── manifest.js        post-seed query script — writes bench/out/<scale>-manifest.json
│   └── cascade-drain.js   BM-XC-001 companion — waits for event_publication to drain (PM-036)
├── report.js             bench-report: renders k6 exports into the run-record Results table (PM-032)
├── scale-sweep.js        BM-XC-004: S1→S2→S3 growth classification from existing exports (PM-036)
├── xc003-report.js       BM-XC-003: BM-ENR-002 pool-saturation sweep report (PM-036)
├── monitor-soak.js       BM-XC-002 companion — heap-bytes-per-session sampling, H7 (PM-036)
└── out/                  gitignored — raw k6 JSON, manifests, and companion-script output
```

`lib/*.js` and `scenarios/*.js` are consumed by k6 itself — `import http from 'k6/http'` resolves
through k6's own embedded JS runtime, not `node_modules`. `package.json`/`node_modules` here exist
only for `seed/`'s real npm dependencies (`mysql2`, `bcryptjs`) and are inert to k6; the top-level
Node scripts (`report.js`, `scale-sweep.js`, `xc003-report.js`, `monitor-soak.js`) use only Node
built-ins (plus `mysql2` for `cascade-drain.js`, via `seed/db.js`), no new dependency added.

## Prerequisites

- [k6](https://k6.io/) installed (`brew install k6`).
- Node.js 18+ (for the dataset generator only).
- A MySQL instance with the schema fully Flyway-migrated (`make -C management up` + `./mvnw spring-boot:run`
  once, or any equivalent — the generator never runs DDL and never adds a benchmark-only
  migration).
- The API running with `-Dspring-boot.run.profiles=benchmark` (see
  `management/src/main/resources/application-benchmark.properties`) so `/actuator/health` and
  `/actuator/prometheus` are reachable and demo accounts are off, per
  `04-workload-data-preparation.md` §4.3.

## Seeding a dataset

```sh
cd bench
npm install
npm run seed -- --scale=S1 --seed=my-seed-value
# add --force to truncate and regenerate an already-seeded database
```

Scales: `S1` (demo, 50 students), `S2` (institution, 5,000 — **the scale the SLOs are written
for**), `S3` (stress, 50,000). See `docs/benchmark-strategy/04-workload-data-preparation.md` §1 for
the full row-count table and why the distributions matter more than the volume.

The generator prints the RNG seed and per-table row counts on completion — record the seed if the
result of this run will ever be compared against another. It also writes the search vocabulary's
term→hit-count table to `bench/out/<scale>-search-terms.json` (gitignored — generator output is
never committed, only the generator and its seed are).

### The account cohort

Since the generator bulk-inserts `students` directly (bypassing the API, per
`04-workload-data-preparation.md` §4.1), most seeded students have no login. A small cohort of
students — plus one staff account per role — gets a real `users` row so login-dependent scenarios
have something to authenticate as:

| Role | Username | Password |
| --- | --- | --- |
| STUDENT (cohort) | that student's own generated email (see the generator's printed sample) | `Benchmark123!` |
| REGISTRAR | `bench.registrar` | `Benchmark123!` |
| LIBRARIAN | `bench.librarian` | `Benchmark123!` |
| COURSE_ADMINISTRATOR | `bench.course_administrator` | `Benchmark123!` |
| SYSTEM_ADMINISTRATOR | `bench.system_administrator` | `Benchmark123!` |

Not a real secret — this password only ever exists in throwaway, fabricated benchmark data
(`04-workload-data-preparation.md` §6). Override it by setting `COHORT_PASSWORD` for both the
generator and `bench/lib/config.js` if you don't want the default.

## The scenario file pattern

`lib/session.js` is the one piece the plan calls not optional (`02-benchmark-plan.md` §1.1): log
in once per VU, never per iteration — a login costs a real BCrypt verification, and
re-authenticating every iteration measures that instead of whatever the scenario claims to test.
Every file under `scenarios/` follows the same shape (see `scenarios/student-search.js` for the
canonical example, or `scenarios/enrollment-list.js` for a slightly richer one):

```js
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../lib/config.js';
import { buildOptions } from '../lib/runner.js';
import { ensureLoggedIn } from '../lib/vuSession.js';

export const options = buildOptions('warmup', [
  { id: 'BM_XXX_001', exec: 'bmXxx001' },
]);

export function warmup() {
  ensureLoggedIn('REGISTRAR');
  // discover whatever sample data the measured exec(s) need; never counted in the metrics
}

export function bmXxx001() {
  ensureLoggedIn('REGISTRAR'); // cheap after the first call — see vuSession.js
  const res = http.get(`${BASE_URL}/api/v1/...`, { tags: { name: 'BM_XXX_001' } });
  check(res, { 'BM-XXX-001 status 200': (r) => r.status === 200 });
}
```

`buildOptions()` wires up the `scenarios`/`thresholds` blocks from `bench/lib/scenarios.js`'s
registry, runs every entry sequentially (never concurrently — different endpoints sharing the
Hikari pool/host CPU at the same moment would confound each other's numbers), and sets
`noCookiesReset: true` (load-bearing: k6 clears cookies between *iterations* by default, which
would silently 403 every request after the first without it). A file that genuinely needs
concurrent traffic (`mixed-soak.js`) or a fixed-iteration burst instead of a steady-state window
(`cascade-delete.js`) hand-builds `options.scenarios` itself — see those files' header comments for
why `buildOptions()` doesn't fit them.

Configure a run with env vars, e.g.:

```sh
k6 run --env BASE_URL=http://localhost:8080 --env SCALE=S2 bench/scenarios/student-search.js
# normally: make -C bench bench SCENARIO=student-search SCALE=S2
```

## Sprint 8 (PM-035/036): write, auth, and cross-cutting scenarios

Beyond `make -C bench bench SCENARIO=<name>` (works for `writes`, `enrollment-batch`, and, for a
quick dev smoke test, `auth-login` too), five scenarios need their own Makefile targets because
they mutate data, must run isolated, or need a companion process k6 itself can't be:

| Target | Covers | Why it's not a plain `make -C bench bench` |
| --- | --- | --- |
| `make -C bench bench-auth-ramp SCALE=..` | BM-IDN-001 | Must run alone — pins `BM_ONLY` to just the 5 ramp stages |
| `make -C bench bench-cascade-delete SCALE=..` | BM-XC-001 | Destructive; waits for `seed/cascade-drain.js` to confirm the async cascade drained before returning |
| `make -C bench bench-xc-003 SCALE=..` | BM-XC-003 | Loops `enrollment-list.js`'s `BM_ENR_002` at VUS=5,10,20,40 — 4 runs, not 1 |
| `make -C bench bench-scale-sweep` | BM-XC-004 | No k6 run at all — classifies existing `bench/out/` exports |
| `make -C bench bench-mixed-soak SCALE=.. [SOAK_DURATION=30m]` | BM-XC-002 + BM-IDN-004 | Backgrounds `monitor-soak.js` around the k6 run for the H7 heap-bytes-per-session sample |

None of `writes`, `enrollment-batch`, `auth-login`, `cascade-delete`, or `mixed-soak` are in
`bench-all`'s `BENCH_SCENARIO_FILES` — that loop stays read-only-only, deliberately.

**Known scale constraints** (documented, not silently worked around — the affected exec functions
no-op rather than error):

- **BM-ENR-008** (50-course batch) needs ≥50 courses; S1 has 20 total. Use S2/S3.
- **BM-CRS-004** and **BM-XC-001's N=50/200 stages** need at least that many courses/students; S1
  (20 courses, 50 students) exhausts almost immediately. Use S2/S3 for a meaningful sample.
- **BM-XC-001** is destructive — restore the dataset (`04-workload-data-preparation.md` §5) before
  any other `bench-*` run at that scale.
