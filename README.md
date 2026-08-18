# Student Management System

## What is this?

This is a system for keeping track of **students, the books they borrow, and the courses they take** — the kind of record-keeping a school, training center, or library would need day to day.

Think of it as the digital version of three linked ledgers:

- **A student register** — who is enrolled, with their contact details and a unique student code.
- **A book log** — which books exist, and which student (if any) currently has each one checked out.
- **A course catalog** — which courses are offered, and who is enrolled in each one.

The system makes sure these three records stay consistent with each other automatically. For example, if a student leaves the school and their record is deleted, any book they had is automatically marked as "returned" (no longer assigned to them) and they're automatically removed from any course rosters — without anyone needing to update those records by hand.

## Who is it for, and what problem does it solve?

Anywhere that manually tracks "which student has which book" or "who's enrolled in which course" using spreadsheets runs into the same problems over time: duplicate entries, forgotten updates, and records that quietly go out of sync with each other (e.g., a book still shown as "borrowed" by a student who left months ago).

This system exists to prevent that by enforcing a few simple rules automatically:

- Every student, book, and course has a unique identifying code — no accidental duplicates.
- A student can't be enrolled in the same course twice.
- Returning a book or leaving a course never deletes the book or course itself — only the link between it and the student is removed.
- Removing a student or a course automatically cleans up everything connected to it, in one safe step, so nothing is left dangling.

## What can it actually do?

In plain terms, the system supports:

- **Adding, viewing, updating, and removing** students, books, and courses.
- **Assigning a book to a student** (and later marking it returned/unassigned).
- **Enrolling a student in a course** (and later unenrolling them).
- **Looking things up**, such as "which books does this student have?" or "who is enrolled in this course?"

Only authorized, logged-in users can make changes (add, edit, delete). A student can look up their own information, but not anyone else's.

If someone tries to do something that doesn't make sense — like enrolling in the same course twice, or looking up a student that doesn't exist — the system rejects the request with a clear explanation rather than silently doing the wrong thing.

## How is it built? (for technical readers)

Under the hood, this is a Java/Spring Boot REST API — a backend service that other applications (a web dashboard, a mobile app, etc.) would talk to. It's built as a portfolio-grade example of clean, modular backend architecture, with layered code, input validation, proper error handling, and a real relational database (MySQL) enforcing data integrity.

For architecture details, module boundaries, database design, and the technical roadmap, see the documentation: [Document](docs/).

## Continuous Integration

Every pull request against `main` triggers the [`CI` workflow](.github/workflows/ci.yml), which:

1. Checks out the repository and sets up JDK 21 (Temurin), matching `management/pom.xml`.
2. Starts a MySQL 8.4 service container (same defaults as `docker-compose.yml`) so Flyway-backed tests can run.
3. Runs `./mvnw verify` from `management/`, which compiles, runs the full test suite (unit, [ArchUnit](https://www.archunit.org/) architecture rules, and the Spring Modulith module-boundary check), and packages the app.

At this stage no `@DataJdbcTest`/Testcontainers-backed integration suites exist yet, so `mvn verify` only exercises the unit and architecture levels described in [`docs/Testing/01-test-strategy.md`](docs/Testing/01-test-strategy.md); the ArchUnit rules under `management/src/test/java/org/phuchoang/management/architecture/` and the `shared/ModuleBoundaryTest` are wired to fail as soon as code violates the module layout, even before any domain code exists.

To run the same check locally:

```sh
cd management
./mvnw verify
```

This requires a reachable MySQL instance matching `application.properties`' defaults — run `make up` from the repo root first.
