# Non-Functional Requirements

Business Analysis — v0.1 addendum. The first non-functional requirements this document set has ever stated.

`req.md` §7 "Out of Scope" says the requirements document "intentionally excludes system architecture, technology choices, storage design, interface design, and any other implementation concerns" — and nothing in `req.md`, `use-cases.md`, or `user-stories.md` says anything about how fast a use case should complete. That silence is why `docs-v00/Benchmark/benchmark-strategy/01-benchmark-strategy.md` §2.2 had to *invent* Service Level Objectives from scratch, calling them explicitly "proposals, not requirements... derived from what the domain plausibly demands," because there was nothing here to test against.

That is no longer only a proposal sitting in a technical document. Six accepted benchmark runs (`docs-v00/Benchmark/result/`) have since measured this system against those proposed targets, found every one of nineteen P0/P1 scenarios breaching them, and traced why (`06-conclusions-and-recommendations.md`). A fix plan now exists (`docs-v01/Benchmark/07-improvement-roadmap.md`, `08-hazard-fix-specs.md`). At that point, "how fast should this feel" has stopped being a purely technical question and become a business one — this document is where the answer belongs.

---

## 1. The requirement classes

Restated in business language from `01-benchmark-strategy.md` §4.2's SLO classes — the technical document remains the source of the exact millisecond figures and their p95/p99 split; this section exists so a non-technical reader of BA-docs can find the same commitment stated in terms of what a user experiences, not what a load test asserts.

| Class | What it covers | Feels like |
| --- | --- | --- |
| **Read-single** | Opening one record you already know the identifier for — a student's profile, a book's detail, a course's detail | Instant. No perceptible wait between selecting a record and seeing it. |
| **Read-list** | Searching or browsing a list — student/book/course search, enrollment lists, staff-account list | Near-instant for the first page of results. A brief, single perceptible pause is acceptable; a noticeable stall is not. |
| **Write-simple** | Creating, updating, or deleting one record — registering a student, updating a record, assigning a book, removing a course | A short, expected pause while the action is confirmed — comparable to any form submission. |
| **Login** | Signing in | A deliberately slightly longer pause than other actions, because the system does real cryptographic work to protect the password — but still well within what a user tolerates once per session. |
| **Batch-50** | Enrolling a student in many courses in one action, up to the 50-course cap | A few seconds is acceptable for the largest batch this action allows; the system tells the caller which of the up-to-50 courses succeeded rather than making the whole action wait or fail together. |

**These are still proposals, not measured requirements** — no stakeholder has signed off on them as a contract, and `01-benchmark-strategy.md` §2.2's own caveat still applies: "revise them freely once anyone has better information; that is a documentation change, not a failure." What has changed since that caveat was written is that these targets are no longer *untested* proposals — they have been run against the real system, found unmet everywhere, and turned into a funded, sequenced plan to close the gap. That is a meaningfully stronger claim on this document than "someone guessed a number," even though it is still not a customer-signed requirement.

Two rules apply across every class, unchanged from the technical document and restated here because they are business-meaningful on their own: **an error is never an acceptable substitute for a slow-but-correct answer** (error rate must stay under 0.1% regardless of load), and **no class may miss its target by more than 2× at the realistic single-institution data scale** — beyond that, the feature is not merely slow, it is not fit for the population this system is built for.

## 2. Traceability to use cases

Extends the existing `req.md` → `use-cases.md` traceability convention rather than introducing a new one. A representative mapping, not exhaustive — every use case sharing an endpoint shape with one below inherits the same class:

| NFR class | Governs (representative UCs) | Evidence |
| --- | --- | --- |
| Read-single | UC-17 (student detail), UC-18 (book detail), UC-19 (course detail), UC-16 (own record) | `BM-STU-005`, `BM-BK-004`, `BM-CRS-003`, `BM-ME-001` |
| Read-list | UC-13 (student search), UC-14 (book search), UC-15 (course search), UC-20 (look up enrollments) | `BM-STU-002/003/004`, `BM-BK-001`, `BM-CRS-001`, `BM-ENR-001/002/003` |
| Write-simple | UC-1 (register student), UC-2 (update student), UC-5 (assign book to student), UC-10 (remove course) | `BM-STU-006/007`, `BM-BK-005`, `BM-CRS-004` |
| Login | UC-21 (login) | `BM-IDN-001/002` |
| Batch-50 | UC-26 (enroll student in multiple courses) | `BM-ENR-006/007/008` |

Full scenario-to-endpoint detail lives in `docs-v00/Benchmark/benchmark-strategy/03-benchmark-scenarios.md`; this table exists only to anchor the NFR classes to the business-facing UC ids already in use elsewhere in this repository.

**A note on where these UC ids came from.** They are verified directly against `use-cases.md`'s own numbering, not copied from `03-benchmark-scenarios.md`'s UC column — the two disagree on most of the ids above (e.g. `03-benchmark-scenarios.md` labels the student-detail control `BM-STU-005` as UC-14, but UC-14 in `use-cases.md` is "View/Search Books"; it labels the three own-records scenarios `BM-ME-001/002/003` as UC-19, but UC-19 is "View Course Detail" — the correct id is UC-16, "View Own Record, Books & Courses"). This appears to be a pre-existing drift in the Benchmark scenario catalog's UC column, not something this document introduces or corrects there — `03-benchmark-scenarios.md` is a `docs-v00` file and is cited, not edited, per this version's own convention. Flagged here so the discrepancy isn't silently inherited into a second document.

## 3. Notes for related use cases

Two findings worth recording here rather than in a technical document, because both are about what a use case actually promises the user — a business-analysis question, not an implementation one.

### 3.1 Pagination — no use case needed to change, and none did

Every use case in `use-cases.md` that paginates anything — UC-13 (student search), UC-14 (book search), UC-15 (course search), UC-16 (own record's books/courses), UC-17 (a student detail's associated list), UC-19 (a course's enrolled-student roster), and UC-20 (look up enrollments) — describes it the same way: "the system returns one page of the matching record(s)... [the actor] may request the next page." Never a page number, never a total count. `IP-05` (`docs-v01/Benchmark/08-hazard-fix-specs.md`) was deciding between keeping an absolute "Page N of M" display and moving to Prev/Next-only navigation; that decision has since been made and shipped — Prev/Next-only, cursor-based navigation, per `docs-v01/Benchmark/10-customer-performance-summary.md` §4 — for the student, book, course, and own-record (`/me/...`) listing screens this section covers. **This document set is why that choice was safe**: nothing here has ever promised a page count or a jump-to-page capability, so moving to Prev/Next-only navigation required no change to any UC or user story — it dropped an implementation embellishment the business layer never asked for, not a stated capability. (Staff-account listing, which this section does not cover, keeps absolute page numbers — see `docs-v01/SA-docs/07-benchmark-remediation-impact.md` §2/§7.2 for the resulting API-contract split.)

### 3.2 Login under load — conditional on how it's fixed

UC-21 (login) has exactly one failure path today: wrong username or password, rejected with an authentication error. It says nothing about what happens if too many people try to log in at once — because today, nothing distinguishes that case from a normal slow response.

`IP-08` (`docs-v01/Benchmark/08-hazard-fix-specs.md`) proposes two ways to keep a login rush from degrading the rest of the application: a bulkhead (requests queue invisibly, users just wait slightly longer — no new outcome) or rate-limiting (`429`, users are told to retry — a new outcome). **Only the second requires a change here.** If a bulkhead is chosen, UC-21 needs no edit. If rate-limiting is chosen, UC-21 needs a new alternate flow describing the "too many concurrent sign-in attempts, please retry" outcome, and `user-stories.md` may need a corresponding story for it. This document does not choose between the two — see `docs-v01/SA-docs/07-benchmark-remediation-impact.md` §2 for the architecture-side version of the same open choice.
