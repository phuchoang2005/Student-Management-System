# Benchmark Plan

Benchmark Documentation — Part 2 of 5 ([Benchmark Strategy](./01-benchmark-strategy.md) → Benchmark Plan → [Scenarios](./03-benchmark-scenarios.md) → [Workload Data Preparation](./04-workload-data-preparation.md) → [Baseline & Reporting](./05-baseline-and-reporting.md)).

Turns the strategy in [01-benchmark-strategy.md](./01-benchmark-strategy.md) into an executable procedure: what the harness looks like, how a run is conducted so that two runs are comparable, in what order the hazards are attacked, and what has to be true before and after. The scenarios themselves live in [03-benchmark-scenarios.md](./03-benchmark-scenarios.md).

This is documentation only — no harness code is included here. The layout in §1 is a specification for building it.

---

## 1. Harness Layout

A new top-level `bench/` directory, a sibling of `management/`, `management-frontend/`, `docs/`, and `util/` — deliberately outside `management/src/`, for the reasons in `01-benchmark-strategy.md` §6.1.

```
bench/
├── README.md                  how to run it, and the prerequisites
├── lib/
│   ├── session.js             login → capture JSESSIONID → reuse for the run
│   ├── slo.js                 the §4.2 SLO classes as reusable k6 threshold objects
│   └── config.js              base URL, scale selection, VU profiles, from env vars
├── scenarios/
│   ├── student-search.js      one file per BM-* group
│   ├── enrollment-list.js
│   ├── enrollment-batch.js
│   ├── auth-login.js
│   ├── cascade-delete.js
│   └── mixed-soak.js
├── seed/
│   ├── generate.<ext>         deterministic dataset generator (04 §4)
│   └── scales.<ext>           S1–S4 row counts and distribution parameters (04 §1–2)
└── jmh/                       or management/src/test/java/.../benchmark/ — see §1.2
```

### 1.1 `lib/session.js` is the one piece that is not optional

Authentication is a server-side session keyed by a `JSESSIONID` cookie (`04-authentication-authorization.md`), not a bearer token. That has three consequences the harness must respect, and getting any of them wrong silently invalidates a run:

1. **Log in once per virtual user, in `setup` or in a guarded init block — never per iteration.** A login costs a BCrypt verification (H5). A scenario that re-authenticates every iteration is measuring H5 no matter which endpoint it names, and its numbers are meaningless for anything else.
2. **Carry the cookie explicitly.** k6 does maintain a per-VU cookie jar, but the run must assert that the session is still live rather than assume it — a silently expired session turns a latency benchmark into a 401 benchmark, and §4's error-rate threshold is what catches that.
3. **Every VU is a session, and sessions are heap-resident and uncapped** (H7). At high VU counts the harness is itself the thing that exercises H7, which is a feature (`BM-XC-002` uses it) but must be accounted for when reading any *other* high-concurrency scenario.

Log in as the role the scenario actually needs. The RBAC allow-list in `SecurityConfig` is explicit per method and path, and a 403 measured at full speed looks exactly like a fast endpoint.

### 1.2 Where the JMH benchmarks live

Two options, and the choice has a real consequence:

- **`management/src/test/java/.../benchmark/`** — simplest, no new module, and JMH is already test-scope. But it puts benchmark classes inside the tree that ArchUnit and `ApplicationModules.verify()` police. The naming rules (`@Service` → `*Service` in `application/`, records in `internal/` → `*Row`) are class-shape rules that a `*Benchmark` class in its own package should not trip, but this must be **verified by running `./mvnw test` after adding the first one**, not assumed.
- **`bench/jmh/` as a separate minimal Maven project** — completely isolated, at the cost of depending on `management` as an artifact and therefore needing it installed locally first.

Start with the first and fall back to the second if the architecture tests object. Note also the annotation-processor caveat in `01-benchmark-strategy.md` §8: `management/pom.xml` configures an explicit `annotationProcessorPaths` list, and JMH's generator must be added *to that list* or no benchmark classes are generated at all.

### 1.3 Makefile targets

Following the existing `docs` block in the root `Makefile`, which already establishes the pattern of a documented target group with a `help` line each:

| Target | Does |
| --- | --- |
| `make bench-seed SCALE=S2` | Drops, re-migrates, and regenerates the dataset at the named scale with the recorded seed |
| `make bench SCENARIO=... SCALE=S2` | Runs one k6 scenario against the running API, writing raw output under `bench/out/` |
| `make bench-all SCALE=S2` | The full sequence in §3, in order |
| `make bench-report` | Renders the raw k6 output into the run-record template (`05-baseline-and-reporting.md` §3) |
| `make bench-jmh` | Runs the JMH suite |

`make bench` must **not** depend on `make up` implicitly. The database has to be at a known scale and a known configuration before a run starts, and a target that quietly starts a container with whatever data was last in it is how two incomparable runs get compared.

---

## 2. Run Protocol

The protocol is what makes two runs comparable. It is not optional detail; skipping the warm-up produces numbers that are wrong in a specific and misleading direction.

### 2.1 The three phases

| Phase | Duration | What is happening | Counted? |
| --- | --- | --- | --- |
| **Warm-up** | ≥ 60 s, or until p95 stabilizes | JIT compiles the hot paths from interpreted bytecode to optimized native code; the InnoDB buffer pool populates from disk; the Hikari pool opens its connections; class loading finishes | **No — discarded** |
| **Steady state** | 300 s (60 s for the CI smoke run) | The measurement window. Fixed duration, fixed VU count. | **Yes** |
| **Cool-down** | ~30 s | VUs ramp to zero; in-flight requests drain; async listeners finish | No |

**Why the warm-up is not negotiable.** A cold JVM can be an order of magnitude slower than a warm one, and a cold InnoDB buffer pool turns every read into disk I/O. A run that includes its warm-up in the measurement reports a p99 dominated by startup and a p50 that drifts downward across the window — and, worse, the effect is *larger* for short runs, so it systematically punishes exactly the quick comparison runs someone will want to do most often.

### 2.2 Repetition

**Three repetitions per scenario per scale; report the median of the three.** Single runs on a shared host are not reproducible enough to act on, and the median is robust against the one repetition where a background process woke up. If the three repetitions spread by more than ~20% at p95, the host was too noisy — record that fact and re-run rather than reporting the median of noise.

### 2.3 Isolation rules

- **One dataset scale per run.** Never mix scales within a run; the whole point is the comparison between them.
- **Reseed between scales**, per `04-workload-data-preparation.md` §5 — do not let a previous scale's rows survive into the next.
- **Restart the application between scales.** The JVM will have JIT-compiled and heap-shaped itself around the previous dataset.
- **Write scenarios mutate state.** Enrollment, registration, and deletion scenarios all leave rows behind, which changes the dataset out from under any scenario that runs after them. Either reseed after each write scenario, or run all read scenarios before any write scenario. §3 chooses the latter and says so.
- **Nothing else runs on the host.** Close the IDE, the browser, the dev server. Record host CPU during the run (`01-benchmark-strategy.md` §7.2) so this can be checked rather than trusted.

---

## 3. Execution Sequence

Ordered by the risk prioritization in `01-benchmark-strategy.md` §9, and arranged so read scenarios precede write scenarios (§2.3).

| Step | What | Hazard | Why here |
| --- | --- | --- | --- |
| **0** | Close H8 first: add actuator + Prometheus under the `benchmark` profile, confirm metrics are reachable | H8 | Every subsequent step is more useful with server-side attribution and less useful without it. Doing this after the first red run means re-running the first red run. |
| **1** | Seed **S1**, run the full read catalog, accept as the first baseline | — | S1 is the demo-scale dataset. It should pass everything comfortably; if it does not, the problem is not a scaling problem and everything after this is premature. |
| **2** | Seed **S2**, run the full read catalog | H1, H2, H3 | The P0 hazards, at the scale that represents a real institution. **This is the run that matters most** — the SLOs in `01` §4.2 are written to be met here. |
| **3** | Write scenarios at S2: registration, enrollment, batch | H4, H5 | After the reads, because they mutate the dataset. |
| **4** | Login burst at rising concurrency, S2 | H5 | Isolated deliberately: it saturates CPU, so anything sharing the run with it measures contention rather than itself. |
| **5** | Cascade / bulk-delete, S2 | H6 | Last of the S2 writes because it is the most destructive to the dataset. |
| **6** | Seed **S3**, re-run the P0 read scenarios only | H1, H2, H3 | The stress probe. Breaches are expected and informative — the shape of the S1→S2→S3 curve is the actual deliverable, and no single point on it is. |
| **7** | Mixed-role soak, S2, extended duration | H6, H7 | Needs a stable system; runs once the discrete scenarios are understood. |
| **8** | JMH suite | H5 | Independent of everything above (no server, no database) and can run any time, but reads best *after* step 4 has shown what BCrypt costs end-to-end. |

Steps 1, 2, and 6 are the minimum useful run. Everything else can be deferred without making those three uninterpretable.

---

## 4. Entry & Exit Criteria

In the shape of `Testing/01-test-strategy.md` §5.

| Step / level | Entry criteria | Exit criteria |
| --- | --- | --- |
| **Any run** | API running against a migrated database at a known scale and seed; host quiet; the pinned configuration in `01` §7.2 recorded | A run record written to [`result/`](./result/) per `05-baseline-and-reporting.md` §3, with the verdict per scenario |
| **Baseline establishment (S1)** | Step 0 complete; S1 seeded | Every read scenario green against its SLO class; run accepted as the S1 baseline |
| **P0 measurement (S2)** | S1 baseline accepted; S2 seeded and verified against the row counts in `04` §1 | Every scenario has p50/p95/p99 and throughput at a recorded concurrency; each of H1, H2, H3 has a quantified cost, whether or not it breaches |
| **Write scenarios** | Read scenarios complete at this scale; dataset reseeded or write-last ordering confirmed | Batch cost characterized at 1 / 10 / 50 courses; registration latency broken down into its BCrypt and AES components (cross-referenced to the JMH result) |
| **Stress (S3)** | S2 results recorded and accepted | The S1→S2→S3 curve is plotted per P0 scenario; growth is classified as flat, linear, or worse |
| **Soak** | Discrete scenarios understood at S2 | No unbounded heap growth beyond what H7 predicts; `event_publication` fully drained at the end; no rejected async tasks |
| **JMH** | Suite compiles and generates (verify the annotation-processor caveat, §1.2) | BCrypt cost curve recorded for strengths 4–14; the value-object result recorded **whether or not it is noise** — a null result is the deliverable there |
| **Regression run** (ongoing) | A prior accepted baseline exists at the same scale | No scenario regressed beyond the thresholds in `05-baseline-and-reporting.md` §2, or each that did has a linked issue |

---

## 5. CI Integration

**The full benchmark does not belong in pull-request CI, and putting it there would be worse than not having it.**

GitHub-hosted runners are shared, virtualized, and of unpredictable neighbour load. Latency measured on them varies by multiples between runs of identical code. A latency threshold on that substrate produces two failure modes and no successes: set it tight and it fails randomly until people learn to ignore CI, set it loose and it catches nothing. Neither is worth the minutes it costs on every PR.

What *is* worth having is a narrow guard against catastrophic breakage:

| Property | Value |
| --- | --- |
| **Trigger** | Manual (`workflow_dispatch`) plus optionally a nightly schedule on `main` — **not** on pull requests |
| **Job** | Separate from the existing `verify` job in `.github/workflows/ci.yml`; must not gate it |
| **Scale** | S1 only — it seeds in seconds |
| **Duration** | 60 s steady state, after a 60 s warm-up |
| **Asserted** | `http_req_failed` rate < 1%; and a deliberately generous p95 ceiling — roughly 10× the S1 SLO |
| **Not asserted** | Anything resembling the real SLOs in `01` §4.2 |

The point of that p95 ceiling is to catch an accidental full-scan-in-a-loop or a missing index — a change that makes something 50× slower, which no amount of runner noise can disguise. It is a smoke alarm, not a thermometer. **Real SLO verdicts are only ever taken from a workstation run under the §2 protocol**, and `05-baseline-and-reporting.md` treats CI output as advisory.

The existing `verify` job (`./mvnw verify` against a MySQL 8.4 service, uploading the JaCoCo report) is unchanged by any of this.

---

## 6. Risks & Assumptions

| # | Risk / assumption | Impact | Mitigation |
| --- | --- | --- | --- |
| R1 | **The only available environment is a shared laptop host.** | Absolute numbers are not portable. | Accepted and stated, not mitigated — `01-benchmark-strategy.md` §7.1. Every threshold in this set is expressed as a delta for exactly this reason. |
| R2 | The k6 driver competes with the JVM and MySQL for CPU, and at high VU counts becomes the bottleneck. | Silently understated throughput; latency that describes the driver. | Record host CPU per run and **discard** driver-saturated runs (`01` §7.2). Prefer lower VU counts with longer durations over high VU counts. |
| R3 | Colima interposes a VM between MySQL and the disk. | I/O-bound results are pessimistic and vary with the VM's state. | Size `innodb_buffer_pool_size` explicitly and record it; prefer scales that fit in the pool for comparison runs, and treat the one that does not as a separate, labelled measurement. |
| R4 | **Assumption: the proposed SLOs (`01` §4.2) are reasonable.** They are invented. | A whole run could be judged against the wrong bar. | The run record captures raw percentiles, so a later revision of the SLOs can re-judge old runs without re-running them. Verdicts are derived, never the only thing stored. |
| R5 | Write scenarios mutate the dataset, so scenario order affects results. | Non-reproducible runs, in a way that is easy to miss. | The read-before-write ordering in §3, plus reseeding between scales (§2.3). |
| R6 | H8 (no observability) may not be closed before the first runs. | Red runs cannot be attributed; the escalation ladder stalls at its first rung. | Step 0 exists to prevent this. If it is skipped, the run record must say so — an unattributed red result is a finding about the benchmark, not about the system. |
| R7 | Bulk-generated data has no `users` rows, so seeded students cannot log in. | Login and `/me/*` scenarios would have nothing to authenticate as. | `04-workload-data-preparation.md` §4.2 provisions a separate account cohort for exactly this. |
| R8 | Async cascade completion is not visible in the HTTP response (the 204 returns before the listener runs). | H6 looks free from the client side. | Measure completion against the `event_publication` table rather than the response — `03-benchmark-scenarios.md`, `BM-XC-001`. |

---

## 7. Out of Scope (this document)

- Why each hazard matters and what the SLOs are — see [01-benchmark-strategy.md](./01-benchmark-strategy.md).
- The scenarios this plan sequences — see [03-benchmark-scenarios.md](./03-benchmark-scenarios.md).
- Dataset scales and how they are generated — see [04-workload-data-preparation.md](./04-workload-data-preparation.md).
- The run-record format and regression thresholds — see [05-baseline-and-reporting.md](./05-baseline-and-reporting.md).
