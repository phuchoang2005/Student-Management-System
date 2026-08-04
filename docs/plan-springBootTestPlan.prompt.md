## Plan: Spring Boot Test Strategy

Build a layered Spring Boot test plan for the Student Management API that validates the contract-first behavior in the docs, with emphasis on business rules, relationship cleanup, validation, and error envelopes. The recommended approach is to cover repository, service, controller, and full integration paths separately so the suite is fast, diagnosable, and traceable back to the OpenAPI contract.

**Steps**
1. Phase 1: Scope and test taxonomy
2. Treat the API contract as the source of truth for test coverage: Students, Books, Courses, Student Books, and Student Courses.
3. Split the suite into four layers: repository tests, service tests, controller slice tests, and integration tests. *Depends on step 2*
4. Define the contract-level assertions each layer owns so the same behavior is not tested redundantly at every layer. Repository tests own persistence and constraints; service tests own business rules; controller tests own request mapping, validation, status codes, and response envelopes; integration tests own end-to-end lifecycle and cleanup behavior. *Depends on step 3*
5. Phase 2: Repository coverage
6. Verify uniqueness and relational integrity against the database model for studentCode, email, isbn, and courseCode.
7. Verify join-table behavior for student_course and book ownership queries, including empty results and deletes that remove only relationships, not parent entities.
8. Use repository tests to confirm pagination/sorting/search query methods work as expected for list endpoints if custom queries exist.
9. Phase 3: Service coverage
10. Test student create/update/delete rules, including duplicate detection, not-found handling, and deletion side effects that unassign books and remove enrollments.
11. Test book create/update/delete rules, including duplicate ISBN handling, ownership constraints, and owner removal when a book is deleted.
12. Test course create/update/delete rules, including duplicate courseCode handling and removal of student_course rows on delete.
13. Test relationship rules explicitly: assigning a book to a student, unassigning it, enrolling a student in a course, unenrolling, listing related records, and resolving book owner. Include conflict cases for already-owned books and duplicate enrollments. *Depends on step 10*
14. Phase 4: Controller slice coverage
15. Verify each endpoint returns the documented status codes: 201 for creates, 200 for gets and successful relationship assignments, 204 for deletes and detachments, 400 for validation failures, 404 for missing resources, and 409 for duplicate-resource conflicts.
16. Verify validation error responses match the OpenAPI schema shape, especially fieldErrors, and that duplicate/404 responses use the common error envelope with timestamp, status, error, code, message, and path.
17. Verify request binding for path parameters, request bodies, pagination defaults, size bounds, sort, and search parameters.
18. Verify Location headers on create responses and empty-array behavior for relationship list endpoints.
19. Phase 5: Integration coverage
20. Run end-to-end scenarios for each resource: create, fetch, update, list, delete, and confirm the database state afterward.
21. Run high-value relationship scenarios: assign/unassign books, enroll/unenroll courses, delete a student and confirm books become unassigned and enrollments disappear, delete a book and confirm owner removal, delete a course and confirm join-row cleanup.
22. Include negative integration scenarios for duplicate creation, invalid payloads, missing IDs, duplicate book ownership, duplicate enrollment, and book-owner lookups for missing or unowned books.
23. Phase 6: Reporting and maintenance
24. Map each test class back to the OpenAPI endpoint or rule it covers so future API changes can be audited quickly.
25. Keep the plan focused on the documented API contract and avoid testing unrelated concerns such as authentication, UI flows, or future subdomains not present in the docs.

**Relevant files**
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/openapi.yml — endpoint inventory and tags.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/students.yml — student create and list contract.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/students_byId.yml — student lifecycle and delete side effects.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/books.yml — book create and list contract.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/books_byId.yml — book update/delete behavior and ownership boundary.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/student-books_byId.yml — assign and unassign book rules.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/student-books.yml — owned-books list contract.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/book-owner.yml — owner lookup edge case for missing or unowned books.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/courses.yml — course create and list contract.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/courses_byId.yml — course lifecycle and cleanup rules.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/student-courses_byId.yml — enrollment and unenrollment rules.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/student-courses.yml — student course list contract.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/course-students.yml — course roster list contract.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/components/schemas/ValidationErrorResponse.yml — validation error response shape.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/components/schemas/ErrorResponse.yml — common error response shape.
- /Users/phuchoang/Local_Document/student-management/docs/database/schema.mermaid — relational baseline for repository and integration assertions.

**Verification**
1. Confirm every documented endpoint has at least one test scenario assigned to a layer.
2. Assert validation failures use the fieldErrors schema, while 404 and 409 cases use the common error envelope.
3. Confirm delete flows only remove or null the intended rows and do not delete unrelated parent entities.
4. Confirm relationship conflicts are tested, including duplicate enrollment and already-owned book assignment.
5. Confirm pagination tests validate zero-based paging, bounded sizes, and page metadata.

**Decisions**
- Included scope: Spring Boot tests for the current REST API, persistence rules, relationship behavior, and error handling.
- Excluded scope: security, UI tests, performance tests, and unrelated future subdomains.
- Preferred stack assumption: JUnit 5 with Spring Boot Test and PostgreSQL-backed integration coverage, ideally via Testcontainers.

If you want, I can next turn this into a more execution-ready checklist with concrete test classes and scenarios.