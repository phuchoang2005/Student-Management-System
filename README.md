# Student Management System

## What's new in v0.1

Version 0.1 focuses on making the system faster and more reliable as the amount of data grows. Several performance bottlenecks were identified through real load testing and then improved:

* **Faster enrollment listings** — course and student information is loaded more efficiently, making enrollment lists **50–78% faster**.
* **Faster search** — student, book, and course searches now use optimized database indexes instead of scanning the entire dataset.
* **Faster navigation through large lists** — keyset pagination keeps access to deeper pages efficient, even as the amount of data increases.
* **Better database scalability** — the database connection pool was tuned to support more concurrent users without unnecessarily limiting the application.

The complete performance investigation, measurements, and before/after results are available in [`docs/Benchmark/`](docs/Benchmark/), with the customer-facing results documented in [`docs/Benchmark/10-customer-performance-summary.md`](docs/Benchmark/10-customer-performance-summary.md).

## What is it?

The Student Management System is a platform for managing **students, books, courses, and enrollments** in one place. It is designed for schools, training centers, and similar organizations that need a simple way to keep everyday records organized and connected.

Instead of maintaining separate spreadsheets for students, books, and courses, the system keeps these records connected automatically. When something changes, related information is updated as part of the same operation, reducing the chance of outdated or inconsistent records.

For example, when a student leaves the school, the system automatically releases any books assigned to them and removes them from their course enrollments. Staff do not need to remember to update each record separately.

## Who is it for?

The system is designed for organizations that need to manage student information, course enrollment, and book borrowing without relying on multiple disconnected spreadsheets.

As these records grow, manual management can lead to duplicate entries, forgotten updates, and information that no longer matches reality. This system addresses those problems by keeping related records connected and applying important business rules automatically.

Every student, book, and course has a unique identifier. Students cannot accidentally be enrolled in the same course twice, and returning a book or leaving a course removes the relationship without deleting the underlying book or course.

When a student or course is removed, the system also takes care of the related information automatically, helping keep the database clean and consistent.

## What can it do?

The system provides the essential features needed for everyday management:

* **Manage students** — add, view, update, and remove student records.
* **Manage books** — maintain the book catalog and track who currently has each book.
* **Manage courses** — create and maintain available courses.
* **Manage enrollments** — enroll students in courses and remove them when necessary.
* **Track borrowing** — assign books to students and mark them as returned.
* **Find related information** — quickly see which books belong to a student or who is enrolled in a course.

The system also uses role-based access so that each user sees the information relevant to their responsibilities. A Registrar works with students, courses, and enrollments; a Librarian works with books and borrowing information; a Course Administrator manages courses and rosters; and students can view their own records, books, and courses.

All changes require an authorized login, and the system provides clear feedback when an action is not valid—for example, when someone tries to enroll a student in the same course twice or access a student record that does not exist.

## Screenshots

The demo includes five different user roles, allowing you to see how the same application adapts to different responsibilities.

![Sign-in screen listing one demo account per role](assests/images-demo/login.png)

**Sign in** — the demo provides a sample account for each role, making it easy to explore the different experiences without creating real accounts.

![registrar view of the course catalog](assests/images-demo/registrar-role.png)

**Registrar** — manage courses, view enrollment information, and work with the student register.

![Librarian view of the book catalog with ownership status](assests/images-demo/librarian-role.png)

**Librarian** — manage the book catalog and see which student currently has each book.

![System Administrator view of staff account provisioning](assests/images-demo/system-admin-role.png)

**System Administrator** — manage staff accounts, activate or deactivate system access, and manage administrative functions.

## Demo video

A short walkthrough covering all four staff roles — Registrar, Librarian, Course Admin, and System Admin — recorded end-to-end against a live instance of the app.

[Youtube Link](https://youtu.be/vD3ChtpMnwQ)

There is also an Webm format video in [here](assests/demo-videos)

## How is it built?

The application consists of a **Java/Spring Boot REST API** and a **Next.js web interface**. MySQL is used for persistent data storage, with Flyway managing database changes.

The project is organized into separate modules so that different areas of the application can evolve independently. It also includes input validation, structured error handling, automated testing, and database-level integrity rules.

* `management/` — the backend API built with Spring Boot 4, Spring Modulith, MySQL 8, and Flyway.
* `management-frontend/` — the web interface built with Next.js 16, TypeScript, and Chakra UI v3.
* `docs/` — product, business, architecture, testing, and UI/UX documentation.
* `util/` — supporting development and documentation tools.

For architecture, database design, module boundaries, and the technical roadmap, see the [`docs/`](docs/) directory.

## Engineering Highlights

The project was developed with a strong focus on reliability and maintainability. The implementation is backed by explicit business requirements, automated tests, and continuous verification.

* **Complete feature coverage** — all 28 defined use cases are implemented and covered by **211 automated test cases**.
* **Reliable database integration** — integration tests run against a real MySQL instance rather than relying only on mocks.
* **Protected module boundaries** — automated architecture checks prevent modules from depending on internal implementation details of other modules.
* **Role-based access control** — five user roles are supported through a centralized authorization model and tested against the complete access matrix.
* **Automatic data consistency** — related records are updated automatically when important entities such as students are removed.
* **Continuous verification** — every pull request runs the full test and architecture verification process before being merged.

## Performance

Performance was evaluated using real API requests and a real MySQL database at multiple dataset sizes.

The test environments ranged from a small demonstration dataset to an institution-sized environment with **5,000 students, 300 courses, 8,000 books, and 30,000 enrollments**, followed by a larger stress-test environment with approximately **50,000 students, 1,000 courses, 80,000 books, and 400,000 enrollments**.

The tests helped identify areas where performance could degrade as the amount of data increased. These findings were then used to improve search, list views, database connections, and other high-traffic operations.

The performance work also included follow-up testing to verify that improvements in one area did not unintentionally make another area slower. The complete measurements and investigation are documented in [`docs/Benchmark/`](docs/Benchmark/).

## Getting Started

### Prerequisites

* JDK 21
* Docker, or [Colima](https://github.com/abiosoft/colima) on macOS
* Node.js 20+
* The Maven wrapper included in `management/`

### 1. Start the database

```sh
make -C management up
```

This starts the MySQL database used by the application.

### 2. Run the backend

```sh
cd management
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`.

### 3. Run the demo UI

```sh
cd management-frontend
npm install
npm run dev
```

Open `http://localhost:3000` in your browser. The login page provides demo accounts for the different roles so you can explore the system from each perspective.

### 4. Run the tests

```sh
cd management
./mvnw test
./mvnw verify
```

The test suite includes unit tests, architecture checks, and integration tests using a temporary MySQL database through Testcontainers.

### 5. Build the application

```sh
cd management
./mvnw package
```

The resulting application package is created under `management/target/`.

## Continuous Integration

Every pull request against `main` automatically runs the project's verification process. The CI pipeline sets up JDK 21 and MySQL 8.4, runs the complete test suite, checks the application architecture, generates the JaCoCo coverage report, and packages the application.

This means that changes are checked automatically before they are merged, helping maintain a stable codebase as the project evolves.

## Documentation

The project documentation is maintained under [`docs/`](docs/). It covers the business requirements, use cases, system architecture, database design, testing strategy, performance testing, and UI/UX design.

The documentation can also be generated as an interactive HTML site with rendered Mermaid and PlantUML diagrams:

```sh
make -C docs docs
make -C docs docs-watch
```
