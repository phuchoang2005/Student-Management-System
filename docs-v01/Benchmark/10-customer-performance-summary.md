# Performance Improvements in v0.1

*A summary of measured performance gains delivered since the previous release, based on internal load testing.*

---

## Summary

v0.1 makes the platform faster under real, concurrent usage — without changing what any feature does, only how efficiently it's carried out behind the scenes. Three parts of the system got faster:

| Business problem | Technology used to fix it | Improvement |
| --- | --- | --- |
| The system slowed sharply, for everyone, once around 10 staff/students were active at the same time — capping how many people could work in the system at once | **HikariCP** — the connection-pool manager sitting between the application and the MySQL database — resized and tuned for real concurrent usage | The hard slowdown at 10 simultaneous users is gone; the system now sustains meaningfully more concurrent users before slowing down |
| Enrollment listing pages — course rosters, batch enrollment views — got slower the more rows they showed, punishing exactly the screens with the most data | **Batched SQL lookups**: a single database query resolves an entire page's worth of course/student data at once, instead of one query per row | **50–78% faster** |
| Search — the single most-used action for staff finding a student, book, or course — was the slowest set of screens in the product | **MySQL full-text search indexing**, paired with purpose-built database queries in place of an unindexed, brute-force scan | **Faster, and confirmed more efficient at the database level** for every search screen |

Each is explained below: what the old implementation was doing that made it slow, what technology fixed it, and what changed.

---

## 1. Handling many simultaneous users

**Business problem:** the busier the system got, the worse it performed for *everyone on it* — not gracefully, but with a hard cliff. Past a small number of concurrent staff/students, every additional person made every other person's experience worse, regardless of what they were individually doing. That's a direct cap on how many people can use the product at once during busy periods (start of term, registration windows).

**The problem in the old version:** the system was configured, out of the box, to hold open only 10 database connections at a time. That number was never deliberately chosen for real usage — it was just a default. The moment 10 people used the system at the same instant, an 11th request had to sit in a queue waiting for one of those 10 connections to free up, no matter how simple or fast that request's own work was. This produced a hard ceiling on how much the system could do at once, and a sharp jump in wait times right at that 10-user mark.

**Technology used:** **HikariCP**, the high-performance JDBC connection-pool manager built into the application's Spring Boot framework. It's the component responsible for managing the limited, reusable set of connections between the application and the MySQL database — it was already present, just left at its untuned default.

**What changed in v0.1:** the number of database connections the system can use at once was increased threefold, sized against the database's own available headroom.

**Measured result:** at a representative concurrency level, response time improved from 203 ms to 156 ms (23% faster) and throughput rose from 29.6 to 40.2 requests/second (36% more work done per second). More importantly, the rigid ceiling that used to appear exactly at 10 concurrent users is gone — the system now sustains a meaningfully higher level of simultaneous activity before that kind of slowdown appears again.

---

## 2. Enrollment listings

**Business problem:** the screens staff rely on most for day-to-day enrollment management — course rosters, batch enrollment views — were also the ones that got disproportionately slower as they showed more data. A registrar pulling up a full course roster was penalized precisely for asking a bigger, more useful question.

**The problem in the old version:** when building a page of enrollment results, the system looked up each row's course details one at a time, in a loop — for a page of 100 enrollments, that meant up to 100 separate trips to the database just to render one screen.

**Technology used:** a **batched SQL lookup** — the application collects every distinct course/student code a page needs, resolves them all in a single database query using SQL's `IN (...)` clause (via **Spring Data JDBC**, the data-access layer the backend is built on), and assembles the page from that one result set. This is the same batching pattern already proven out on the book-listing pages, now ported to enrollments.

**What changed in v0.1:** the system now collects everything a page needs up front and fetches it in a single batch, then assembles the page from that — the same efficient approach already used successfully elsewhere in the product, now applied consistently to enrollments as well.

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

**Measured result:** for directory-style browsing, response time improved from 242 ms to 204 ms (16% faster). Beyond the timed result, we independently confirmed at the database level — across student, book, and course search alike — that each search now runs as one query instead of two, and that the database is using the new index directly rather than scanning full tables.

---

## A note on the numbers

Figures above are response times measured under simulated concurrent load in a controlled test environment. Exact millisecond values will vary with server hardware and real-world traffic patterns — but the underlying changes (a correctly sized connection pool, batched database lookups, and indexed search) are structural fixes, so the improvement they deliver holds regardless of environment.
