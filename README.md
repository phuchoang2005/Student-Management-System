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

## Getting Started

### Prerequisites

- JDK 21
- Docker (or [Colima](https://github.com/abiosoft/colima) on macOS) — needed both to run MySQL via `docker-compose.yml` and for the [Testcontainers](https://testcontainers.com/)-backed integration tests
- The Maven wrapper (`./mvnw`) checked into `management/` — no local Maven install required

### 1. Start the database

```sh
make up
```

This creates `.env` from `.env.example` if missing (see `.env.example` for the MySQL credentials/port and the `INITIAL_PASSWORD_KEY` used to encrypt students' initial passwords), starts Colima if needed, then brings up the `management-mysql` container defined in `docker-compose.yml`.

Other useful targets: `make down` (stop), `make logs` (tail MySQL logs), `make mysql` (open a MySQL shell), `make reset` (wipe the data volume and start fresh). Run `make help` to list them all.

### 2. Run the app

```sh
cd management
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080` by default, backed by the MySQL instance started in step 1 (Flyway migrates the schema automatically on startup).

### 3. Run the test suite

```sh
cd management
./mvnw test      # unit + architecture (ArchUnit) + Testcontainers-backed integration tests
./mvnw verify     # same, plus packaging and the JaCoCo coverage report
```

The integration tests (`*IntegrationTest`) spin up their own throwaway MySQL container per class via Testcontainers — they don't need `make up` to be running, just a working Docker daemon.

> **Colima users:** if a test run fails immediately with `Container startup failed for image testcontainers/ryuk:0.14.0` / `error while creating mount source path '.../docker.sock'`, Testcontainers' Ryuk cleanup sidecar can't mount the Colima socket. Work around it with:
> ```sh
> TESTCONTAINERS_RYUK_DISABLED=true ./mvnw verify
> ```

After `./mvnw verify`, the coverage report is at `management/target/site/jacoco/index.html`.

### 4. Build / package

```sh
cd management
./mvnw package             # produces management/target/management-<version>.jar (runs tests first)
./mvnw package -DskipTests # same, skipping tests for a faster local build

java -jar target/management-*.jar
```

## Continuous Integration

Every pull request against `main` triggers the [`CI` workflow](.github/workflows/ci.yml), which:

1. Checks out the repository and sets up JDK 21 (Temurin), matching `management/pom.xml`.
2. Starts a MySQL 8.4 service container (same defaults as `docker-compose.yml`) so Flyway-backed tests can run.
3. Runs `./mvnw verify` from `management/`, which compiles, runs the full test suite (unit tests, [ArchUnit](https://www.archunit.org/) architecture rules, the Spring Modulith module-boundary check, and the Testcontainers-backed integration suites), generates the JaCoCo coverage report, and packages the app.
4. Uploads the JaCoCo report (`management/target/site/jacoco/`) as a workflow artifact.

See [`docs/Testing/01-test-strategy.md`](docs/Testing/01-test-strategy.md) and [`docs/Testing/README.md`](docs/Testing/README.md) for the test strategy and the use-case → test-case traceability matrix. The ArchUnit rules under `management/src/test/java/org/phuchoang/management/architecture/` and the `shared/ModuleBoundaryTest` fail the build as soon as code violates the module layout.

To run the same check locally, see [Getting Started](#getting-started) above.
