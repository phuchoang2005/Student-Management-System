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
├── package.json      npm project for seed/ only — lib/ needs no npm packages at all
├── lib/
│   ├── config.js      base URL, scale selection, VU profiles, role credentials — all from env vars
│   ├── session.js      login once per VU, assert the session is still live
│   └── slo.js          the proposed SLO classes as reusable k6 threshold objects
└── seed/
    ├── scales.js        S1–S4 row counts and distribution parameters
    ├── rng.js            seeded PRNG (mulberry32), independent named substreams
    ├── sampling.js        weighted course selection, enrollment-count mixture, Fisher–Yates shuffle
    ├── db.js              mysql2 wrapper: truncate, batched insert, post-load verification
    └── generate.js        entry point — run this to seed a dataset
```

**Not built yet, on purpose:**
- `bench/scenarios/*.js` — the actual `BM-*` load-test scenarios (PM-033).
- `make bench*` Makefile targets (PM-032). Until then, run k6 and the generator directly (below).

`lib/*.js` is consumed by k6 itself — `import http from 'k6/http'` resolves through k6's own
embedded JS runtime, not `node_modules`. `package.json`/`node_modules` here are inert to k6 and
exist only for `seed/`'s real npm dependencies (`mysql2`, `bcryptjs`).

## Prerequisites

- [k6](https://k6.io/) installed (`brew install k6`).
- Node.js 18+ (for the dataset generator only).
- A MySQL instance with the schema fully Flyway-migrated (`make up` + `./mvnw spring-boot:run`
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

## Using `lib/` (until scenarios land)

`lib/session.js` is the one piece the plan calls not optional
(`02-benchmark-plan.md` §1.1): log in once per VU, never per iteration — a login costs a real
BCrypt verification, and re-authenticating every iteration measures that instead of whatever the
scenario claims to test. A minimal scenario file, once PM-033 adds real ones, will look like:

```js
import { login, assertLive } from '../lib/session.js';
import { sloThresholds } from '../lib/slo.js';

export const options = { thresholds: sloThresholds('example', 'READ_LIST') };

export function setup() {
  return login('STUDENT');
}

export default function (session) {
  assertLive(session);
  // ... the actual request(s) under test, tagged `{ tags: { scenario: 'example' } }` ...
}
```

Configure a run with env vars, e.g.:

```sh
k6 run --env BASE_URL=http://localhost:8080 --env SCALE=S1 --env STUDENT_USERNAME=<cohort email> scenarios/example.js
```
