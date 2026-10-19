# Repository Guidelines

## Project Structure & Module Organization

`management/` is the Java 21 Spring Boot API. Organize backend code by business module under `src/main/java/org/phuchoang/management/` (for example, `student`, `book`, `course`, and `identity`); keep domain, application, port, internal, and web concerns separated. Flyway migrations live in `management/src/main/resources/db/migration/` and follow `V<number>__description.sql`.

`management-frontend/` is the Next.js/TypeScript demo UI. Routes are in `src/app/`, reusable UI in `src/components/`, and API/auth hooks and contracts in `src/lib/`. Keep frontend API changes centralized in `src/lib/api/endpoints.ts` and `types.ts`. `bench/` contains k6 scenarios and data seeding; `docs/` contains product, architecture, testing, and benchmark records.

## Build, Test, and Development Commands

From the repository root:

```sh
make -C management up                 # start the local MySQL container
cd management && ./mvnw spring-boot:run
cd management && ./mvnw test          # backend tests
cd management && ./mvnw verify        # tests, architecture checks, JaCoCo report
cd management-frontend && npm install && npm run dev  # UI on port 3001
cd management-frontend && npm run typecheck && npm run build
```

Use `make -C management down` to stop local infrastructure. Do not use `npm run lint`: Next.js 16 removed that command and this project has no ESLint configuration.

## Coding Style & Naming Conventions

Follow the surrounding code: Java uses four-space indentation, PascalCase types, camelCase members, and `*Test` test classes. Keep value objects and domain rules in domain packages; expose module behavior through application services and ports rather than reaching into another module's internals. TypeScript uses two-space indentation, PascalCase React components, camelCase functions/hooks, and App Router route conventions such as `students/[code]/page.tsx`. Extend Chakra tokens in `src/theme/system.ts` instead of adding ad-hoc CSS.

## Testing Guidelines

Add focused unit tests beside the affected backend module under `management/src/test/java/`; use `*IntegrationTest` for database, HTTP, security, or cross-module behavior. Integration tests use MySQL/Testcontainers, so Docker must be available. Run `./mvnw verify` before opening a PR; CI runs the same command and publishes JaCoCo output. Frontend verification is currently manual plus `typecheck` and `build`; Playwright recording lives in `scripts/demo-recording/`.

## Commit & Pull Request Guidelines

History favors concise Conventional Commit-style subjects, often scoped: `feat(*)`, `fix(*)`, `docs(benchmark)`, `perf(*)`, or `style(*)`. Use an imperative summary, keep each commit focused, and avoid generic messages such as “updates.” PRs should explain the user or performance impact, link the relevant issue (especially benchmark findings), list validation commands, and include screenshots for visible UI changes. Update documentation and migration notes when behavior or schema changes.
