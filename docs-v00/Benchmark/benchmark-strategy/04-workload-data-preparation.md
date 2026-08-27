# Workload & Data Preparation

Benchmark Documentation — Part 4 of 5 ([Benchmark Strategy](./01-benchmark-strategy.md) → [Benchmark Plan](./02-benchmark-plan.md) → [Scenarios](./03-benchmark-scenarios.md) → Workload Data Preparation → [Baseline & Reporting](./05-baseline-and-reporting.md)).

The datasets the scenarios in [03-benchmark-scenarios.md](./03-benchmark-scenarios.md) run against: how large, how shaped, how generated, and how reset. Extends [`Testing/04-test-data-preparation.md`](../Testing/04-test-data-preparation.md) §5.4, which deliberately deferred bulk generation ("this domain doesn't need large-volume synthetic generation — no performance/load testing is in scope") on the strength of the exclusion this set reverses (`01-benchmark-strategy.md` §2.1). Everything that document fixes about fixture conventions and PII still applies here unchanged.

This is documentation only — no generator code is included here.

---

## 1. Dataset Scales

Four scales. A run uses exactly one (`02-benchmark-plan.md` §2.3), and the comparison *between* scales is the deliverable (`BM-XC-004`).

| | **S1 — Demo** | **S2 — Institution** | **S3 — Stress** | **S4 — Probe** |
| --- | --- | --- | --- | --- |
| `students` | 50 | 5,000 | 50,000 | open-ended |
| `courses` | 20 | 300 | 1,000 | open-ended |
| `books` | 100 | 8,000 | 80,000 | open-ended |
| `enrollments` | 150 | 30,000 | 400,000 | open-ended |
| `users` | ~55 | ~5,010 | ~50,010 | — |
| **Purpose** | Smoke, CI, first baseline | **The scale the SLOs are written for** | Curve extrapolation | Find the breaking point |
| **Fits in buffer pool** | trivially | should — verify | probably not — the point | no |
| **Seed time** | seconds | < 1 min | minutes | — |

**S2 is the scale that matters.** It represents a plausible single institution: a few thousand enrolled students, a few hundred courses, an average of six courses per student. The SLOs in `01-benchmark-strategy.md` §4.2 are written to be met here, and a scenario that breaches at S2 is a finding.

**S3 is a probe, and breaches there are expected.** Its job is to give `BM-XC-004` a third point so the S1→S2→S3 curve has a shape rather than a slope. Do not treat an S3 breach as a defect on its own.

**S4 is optional and unbounded** — run it only when a specific question needs it ("at what row count does student search cross one second?"). It has no fixed row counts because the answer is what defines them.

### 1.1 Constraints every scale must satisfy

The schema will reject a careless generator, and it will do so partway through a long insert. Check these before generating, not after:

| Constraint | Where | Consequence for the generator |
| --- | --- | --- |
| `uq_students_student_code`, `uq_students_email` | `students` | Codes and emails must be globally unique across the whole generated set — a per-batch counter that resets will collide. |
| `uq_courses_course_code` | `courses` | Same, and the code is only `VARCHAR(20)`. |
| `uq_books_isbn` | `books` | Generated ISBNs must be unique; they need not be checksum-valid, but must satisfy the domain's `Isbn` format if any row will ever be read back through the API. |
| `uq_enrollments_student_course` | `enrollments` | **The one that bites.** A skewed random pairing (§2) will naturally propose duplicates; the generator must deduplicate the `(student_id, course_id)` pairs before insert, not rely on `INSERT IGNORE` — which would silently deliver fewer rows than the scale claims. |
| `chk_courses_credits` (`credits > 0`) | `courses` | No zero-credit filler rows. |
| `uq_users_student_id` + `chk_users_student_role` | `users` | A `STUDENT` row must have a non-null `student_id`; any non-`STUDENT` row must have it null. One user per student, at most. |
| `role` ENUM | `users` | Includes `SYSTEM_ADMINISTRATOR` only after `V2__add_system_administrator_role.sql`. Generate against a fully migrated schema, never a partially migrated one. |
| `enabled` | `users` | Added by `V3__add_enabled_to_users.sql`. Accounts intended for login scenarios must be enabled. |

---

## 2. Distribution Matters More Than Volume

**A uniformly random dataset of the right size will produce confidently wrong results.** This is the single most important thing in this document.

Uniform enrollments — every student in exactly six courses, every course holding exactly the same number — make `BM-ENR-002` and `BM-ENR-003` measure a fiction: every page is the same page, every course lookup hits an equally warm buffer, and the N+1 (H2) looks like a constant that could be anything. Real registrar data is lumpy, and the lumps are where the cost is.

| Table | Required shape | Why |
| --- | --- | --- |
| **`enrollments` per course** | Heavy skew — a handful of large mandatory courses holding thousands, a long tail holding a handful. A Zipf-like or explicit head/tail split, not uniform. | `BM-ENR-003` and `BM-CRS-003` are meant to be run against *the most-enrolled course*. Without a head, that course does not exist and the worst case is never measured. |
| **`enrollments` per student** | Skewed, mean around 6, with a tail of students carrying 15–20. | `BM-ENR-002` and `BM-ME-002` need students whose enrollment list spans multiple pages; with a uniform 6, page 2 is always empty and H2 never reaches its worst case. |
| **`books.owner_id`** | Skewed, plus **20–30% left `NULL`** (unowned). | `BM-BK-003` exists to confirm the per-page owner memo works (`01` §3.1); it needs pages with many distinct owners *and* pages with few. Unowned books also exercise the `LEFT`-side path. |
| **Searchable text** (names, titles, authors, course names) | Drawn from a vocabulary with **known term frequencies** — some substrings matching thousands of rows, some matching one, some matching none. | `BM-STU-002` asks whether scan cost tracks *result count* or *table size*. That question is unanswerable unless the generator knows, in advance, how many rows each search term will match. Record the term→hit-count table alongside the dataset. |
| **`student_code` ordering** | Insertion order should **not** match `student_code` order. | Every paged query is `ORDER BY student_code`. If codes were assigned in insertion order, the physical row order matches the sort order and the `OFFSET` walk (H3) is artificially cheap — the benchmark would flatter itself. |

That last row is easy to get wrong and produces a plausible-looking, badly optimistic result. Shuffle before assigning codes, or assign codes from a shuffled pool.

---

## 3. Determinism

**Every dataset is generated from a recorded RNG seed**, and the seed is written into the run record (`05-baseline-and-reporting.md` §3).

Without this, two runs at "S2" are two different datasets with the same row counts, and any delta between them mixes a real regression with the difference between two random draws — irreducibly, and invisibly. With it, `git checkout` of an older commit plus the same seed reproduces the exact rows, which is what makes a regression bisectable.

The seed fixes: which students enroll in which courses, which books are owned and by whom, the search-term frequency table, and the shuffle in §2's last row. It does **not** need to fix `created_at`/`updated_at`, which are database-generated and not read by any scenario.

---

## 4. Generation Approach

### 4.1 Bulk SQL, outside the application

Datasets are generated by **bulk `INSERT` / `LOAD DATA` against an already-migrated schema**, not by driving the public API.

Seeding S3 through `POST /api/v1/students` would take hours, and most of those hours would be BCrypt: registration hashes a generated initial password *and* AES-encrypts it (H5), so 50,000 students is 50,000 deliberate key-stretching operations. That is a benchmark of `BM-STU-006`, run 50,000 times, before the actual benchmark starts.

Run the generator against a schema Flyway has fully migrated (V1→V4). Never write DDL in the generator, and never add a benchmark-only migration — the schema under test must be the schema in `management/src/main/resources/db/migration/`, unmodified. If a scenario appears to need a schema change to be measurable, that is a finding to record, not a change to make.

For the largest scales, insert in batches with `unique_checks` and `foreign_key_checks` handled deliberately: disabling them speeds the load substantially, but §1.1's constraints then go unenforced during insert, so the generator must guarantee them itself and the load must be verified afterwards (`SELECT COUNT(*)` per table against the declared scale, plus a duplicate check on each unique key).

### 4.2 The account cohort — the consequence of §4.1

Rows inserted directly into `students` have **no corresponding `users` row.** The API creates the account as part of registration (`AccountProvisioning`, called synchronously by `StudentService`); a bulk insert bypasses that entirely.

So bulk-seeded students cannot log in, and every scenario that authenticates as a `STUDENT` — `BM-ME-001`, `BM-ME-002`, `BM-ME-003`, and the student share of the `BM-XC-002` soak — would have nothing to authenticate as.

Provision a separate, small **account cohort**: a few hundred students that also get `users` rows, with `role = 'STUDENT'`, a non-null `student_id`, `enabled = TRUE`, `must_change_password = FALSE`, and a known shared plaintext password. Two rules:

- **`must_change_password` must be false.** `MustChangePasswordFilter` gates every other endpoint for a user still holding a generated initial password, so a cohort seeded with it true would answer 403 to every `/me/*` request — at full speed, which looks exactly like a very fast endpoint.
- **The password hash must be a real BCrypt hash at the same strength the application uses**, generated once and reused across the cohort. A hash at a different work factor makes `BM-IDN-001` measure the wrong cost; a shared hash is fine because BCrypt salts are per-hash and verification cost does not depend on how many rows carry the same value.

Staff accounts (`REGISTRAR`, `LIBRARIAN`, `COURSE_ADMINISTRATOR`, `SYSTEM_ADMINISTRATOR`) are needed one per role at minimum, more if a scenario runs many VUs as one role and H7's per-session accounting should distinguish them.

The cohort's students should be drawn from the *middle* of the enrollment distribution, not the head or tail — `BM-ME-002` should measure a typical student, and a cohort accidentally made of students with two enrollments each would never page.

### 4.3 What must not be seeded

**The demo accounts must not be present.** `GET /api/v1/auth/demo-accounts` and its seeder are environment-conditional (`app.demo-accounts.enabled`, hard-disabled in `application-prod.properties` — PM-017). Whether a benchmark run has them enabled changes the `users` table contents and adds a seeder to startup. Fix it one way — **disabled** — and record it in the run configuration, so it is not a silent variable between runs.

---

## 5. Reset Strategy

| Situation | Procedure |
| --- | --- |
| **Between repetitions** of a read-only scenario | Nothing. Reads do not mutate, and re-warming the buffer pool between repetitions would make them incomparable. |
| **Between repetitions** of a write scenario | Restore, then re-warm. A write scenario's second repetition starts from a dataset its first repetition changed. |
| **Between scales** | Full teardown: `make clean` (drops the volume), `make up`, Flyway migrates, regenerate. Restart the application too (`02-benchmark-plan.md` §2.3). |
| **After `BM-XC-001`** (bulk delete) | Mandatory restore — it deletes students and cascades through books, enrollments, and users. |

### 5.1 Restore from a dump, not by regeneration, at S2 and above

Generate each scale **once**, then `mysqldump` it and keep the dump. Restores are substantially faster than regeneration, and — more importantly — a restore is byte-identical every time, whereas a regeneration is only identical if the seed discipline in §3 held perfectly. The dump removes an entire class of "was it really the same dataset?" doubt from every comparison.

Keep dumps outside the repository. `docs/.gitignore` already excludes generated artifacts, and a multi-hundred-megabyte S3 dump is not a source file. `bench/out/` or an equivalent ignored path is the right home; the run record names the dump it used (`05-baseline-and-reporting.md` §3).

Between S2 write scenarios, `TRUNCATE` + reload of only the affected tables is usually enough and is much faster than a full restore — but note the FK direction: `enrollments` and `users` reference `students`, so truncate children before parents, or disable `foreign_key_checks` for the duration.

---

## 6. Synthetic Data & PII

Inherited verbatim from [`Testing/04-test-data-preparation.md`](../Testing/04-test-data-preparation.md) §7, and it applies with more force here because the volumes are larger:

**All benchmark data is fabricated.** Names, emails, dates of birth, ISBNs, and course names are generated. Emails use the reserved, non-routable **`@example.test`** domain. **No real personal data is ever used as benchmark data**, in any environment, at any scale.

Two additions specific to this set:

- **Generated database dumps are not committed** (§5.1) — not for size reasons alone, but because a committed dump is a dataset nobody reviews, and the next person to add "just a few real-looking rows" to one would face no diff.
- **The generator and its seed are committed; its output is not.** That pairing is what makes the data reproducible without ever storing it, and it is the same split `docs/` already uses between committed `.md` sources and gitignored `.html` output.
