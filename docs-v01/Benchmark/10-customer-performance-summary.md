# Performance Improvements in v0.1

*A summary of measured performance gains delivered since the previous release, based on internal load testing.*

---

## Summary

v0.1 makes the platform faster under real, concurrent usage — without changing what any feature does, only how efficiently it's carried out behind the scenes. Four parts of the system got faster:

| Business problem | Technology used to fix it | Improvement |
| --- | --- | --- |
| The system slowed sharply, for everyone, once around 10 staff/students were active at the same time — capping how many people could work in the system at once | **HikariCP** — the connection-pool manager sitting between the application and the MySQL database — resized and tuned for real concurrent usage | The hard slowdown at 10 simultaneous users is gone; the system now sustains meaningfully more concurrent users before slowing down |
| Enrollment listing pages — course rosters, batch enrollment views — got slower the more rows they showed, punishing exactly the screens with the most data | **Batched SQL lookups**: a single database query resolves an entire page's worth of course/student data at once, instead of one query per row | **50–78% faster** |
| Search — the single most-used action for staff finding a student, book, or course — was the slowest set of screens in the product | **MySQL full-text search indexing**, paired with purpose-built database queries in place of an unindexed, brute-force scan | **Faster, and confirmed more efficient at the database level** for every search screen |
| Pages got dramatically slower the deeper someone browsed into a list — up to ~10× slower for a deep page than a shallow one at our largest tested scale | **Keyset (seek) pagination** — list queries jump directly to "everything after the last item seen" instead of skipping and discarding rows | Implemented; large-scale verification scheduled next |

Each is explained below: what the old implementation was doing that made it slow, what technology fixed it, and what changed.

---

## How we tested this

Every result below comes from load tests run against a dataset sized to represent **a realistic single institution**: 5,000 students, 300 courses, 8,000 books, and 30,000 enrollments — the scale our performance targets are written for. (A handful of results, called out where they apply, used a larger "stress" dataset — 50,000 students, 80,000 books, 400,000 enrollments — to see how far ahead of that the system holds up.)

Each test simulates a fixed number of people using the system **at the exact same time**, continuously, for a 30-second measurement window (after a short warm-up so the numbers reflect steady real usage, not a cold start). Unless noted otherwise, that's **20 concurrent users** — a realistic estimate of staff actively working in the system at once. The specific concurrency level and situation for each result is called out below.

---

## 1. Handling many simultaneous users

**Business problem:** the busier the system got, the worse it performed for *everyone on it* — not gracefully, but with a hard cliff. Past a small number of concurrent staff/students, every additional person made every other person's experience worse, regardless of what they were individually doing. That's a direct cap on how many people can use the product at once during busy periods (start of term, registration windows).

**The problem in the old version:** the system was configured, out of the box, to hold open only 10 database connections at a time. That number was never deliberately chosen for real usage — it was just a default. The moment 10 people used the system at the same instant, an 11th request had to sit in a queue waiting for one of those 10 connections to free up, no matter how simple or fast that request's own work was. This produced a hard ceiling on how much the system could do at once, and a sharp jump in wait times right at that 10-user mark.

**Technology used:** **HikariCP**, the high-performance JDBC connection-pool manager built into the application's Spring Boot framework. It's the component responsible for managing the limited, reusable set of connections between the application and the MySQL database — it was already present, just left at its untuned default.

**What changed in v0.1:** the number of database connections the system can use at once was increased threefold, sized against the database's own available headroom.

**Tested under:** a stepped concurrency sweep — 5, then 10, then 20, then 40 people using the system at the same time — deliberately spanning the old 10-connection ceiling so the exact breaking point would show up in the data. Run against the enrollment-listing screen (the single heaviest page in the product on the database), at institution scale.

**Measured result:** at a representative concurrency level, response time improved from 203 ms to 156 ms (23% faster) and throughput rose from 29.6 to 40.2 requests/second (36% more work done per second). More importantly, the rigid ceiling that used to appear exactly at 10 concurrent users is gone — the system now sustains a meaningfully higher level of simultaneous activity before that kind of slowdown appears again.

---

## 2. Enrollment listings

**Business problem:** the screens staff rely on most for day-to-day enrollment management — course rosters, batch enrollment views — were also the ones that got disproportionately slower as they showed more data. A registrar pulling up a full course roster was penalized precisely for asking a bigger, more useful question.

**The problem in the old version:** when building a page of enrollment results, the system looked up each row's course details one at a time, in a loop — for a page of 100 enrollments, that meant up to 100 separate trips to the database just to render one screen.

**Technology used:** a **batched SQL lookup** — the application collects every distinct course/student code a page needs, resolves them all in a single database query using SQL's `IN (...)` clause (via **Spring Data JDBC**, the data-access layer the backend is built on), and assembles the page from that one result set. This is the same batching pattern already proven out on the book-listing pages, now ported to enrollments.

**What changed in v0.1:** the system now collects everything a page needs up front and fetches it in a single batch, then assembles the page from that — the same efficient approach already used successfully elsewhere in the product, now applied consistently to enrollments as well.

**Tested under:** 20 people using the system at the same time, at institution scale (30,000 enrollments), across three realistic situations: a standard 20-row page, a large 100-row page, and a 100-row page filtered down to the single busiest course on record — the worst case a registrar would actually hit.

**Measured result:**

| Scenario | Before | After | Improvement |
| --- | --- | --- | --- |
| Standard enrollment page | 703 ms | 355 ms | 50% faster |
| Large enrollment page | 670 ms | 308 ms | 54% faster |
| Enrollment page filtered by course | 2.52 s | 0.54 s | 78% faster |

Just as important: response time no longer grows with how many rows are on the page. Before, a large page cost roughly five times what a small one did; now the cost stays essentially flat regardless of page size.

---

## 3. Search (students, books, courses)

**Business problem:** search is the entry point to almost every staff workflow — finding a student, locating a book, looking up a course — so search being slow doesn't just hurt one screen, it adds friction to nearly everything staff do in the product every day.

**The problem in the old version:** every search request scanned the *entire* table for a text match, without any index to help narrow the search — and did it **twice** per request: once to fetch the matching rows, and a second time, separately, just to count how many results existed so the results could be paginated.

**Technology used:** a **MySQL FULLTEXT index** on the searchable columns (names, codes, titles, authors), replacing the old approach of scanning every row and pattern-matching text character by character. Search and plain browsing were also split into two separate, purpose-built database queries (via Spring Data JDBC) instead of one generic query trying to serve both — letting the database plan and optimize each independently.

**What changed in v0.1:** the database now has a dedicated search index built for this purpose, and each search is answered by a single, purpose-built query rather than two full scans — the database can go directly to matching rows instead of reading the whole table twice.

**Tested under:** 20 people searching at the same time, at institution scale (5,000 students / 8,000 books / 300 courses), using realistic search terms of mixed popularity — some matching thousands of records, some matching only a handful, so both a common and a rare search were covered.

**Measured result:** for directory-style browsing, response time improved from 242 ms to 204 ms (16% faster). Beyond the timed result, we independently confirmed at the database level — across student, book, and course search alike — that each search now runs as one query instead of two, and that the database is using the new index directly rather than scanning full tables.

---

## 4. Browsing deep into a list (in progress)

**Business problem:** the further into a results list someone browsed — an older page of a book catalog, a large course roster, a search result that isn't near the top — the slower that page got. And it didn't degrade gently: in our largest-scale baseline test, a page drawn from deep in a list cost roughly **10× longer to load** than an equivalent shallow page, purely because of how far into the list it was.

**The problem in the old version:** list pages were fetched by asking the database to skip past every row before the requested page and then return the next handful — so to show page 50, the database still had to generate and discard the 49 pages nobody asked for. The deeper the page, the more work got thrown away before the customer saw anything, and that cost grows with the size of the underlying data — meaning it gets worse over time as more students, books, and courses are added.

**Technology used:** **keyset (a.k.a. "seek") pagination** — instead of "skip N rows," each list now remembers the last item a user saw and asks the database for "everything after that point," using the same natural sort key each list already uses (student code, ISBN, course code). The database can jump straight to the right spot instead of walking past everything before it.

**Tested under (the baseline problem):** 20 concurrent users, comparing a shallow first page against a deep page (drawn from the last 10% of available pages) on the same query — first at institution scale, then again on a "stress" dataset ten times larger (50,000 students, 80,000 books, 400,000 enrollments) to see how the cost grows as the school grows. The 10× figure above is from that larger dataset — where the old approach hurt most, and where the new approach matters most.

**Status:** implemented across the student, book, and course listing screens, with "Next" cursor-based navigation in place of it. Its performance benefit is expected to be most visible at large scale — exactly where the old approach was worst — and a dedicated large-scale verification run is scheduled next to confirm and quantify it before we publish a measured number here.

---

## A note on the numbers

Figures above are response times measured under simulated concurrent load in a controlled test environment. Exact millisecond values will vary with server hardware and real-world traffic patterns — but the underlying changes (a correctly sized connection pool, batched database lookups, and indexed search) are structural fixes, so the improvement they deliver holds regardless of environment.
