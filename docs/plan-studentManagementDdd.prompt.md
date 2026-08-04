## Plan: DDD-First Diagram Sprint

Shift the 1-week diagram sprint to Domain-Driven Design by making domain boundaries and business rules first-class: define bounded contexts and ubiquitous language first, then produce Use Case, Sequence, and Class diagrams that reflect aggregates, invariants, and domain services instead of CRUD-only structure.

**Steps**
1. Phase 1: DDD Framing and Ubiquitous Language (Day 1)
2. Build a glossary from requirements and API contract to normalize core terms: Student, Book Ownership, Course Enrollment, Enrollment lifecycle, Unassign, Unenroll, Conflict.
3. Define bounded contexts and context map: Academic Context (Student + Course + Enrollment) and Library Context (Book + Ownership), with Student as shared identity concept. Depends on step 2.
4. Capture context interaction rules and anti-corruption notes where API endpoints cross concerns (for example student deletion affects ownership and enrollment). Depends on step 3.
5. Phase 2: DDD-Oriented Use Case Diagram (Day 2)
6. Keep actors: Academic Admin, Library Admin, API Client Application.
7. Group use cases by bounded context instead of endpoint grouping.
8. Annotate key business invariants as includes/extends, such as unique identifiers, single owner per book, no duplicate enrollment, and existence checks.
9. Freeze Use Case v1 only after every use case maps to a context and aggregate responsibility. Depends on step 7.
10. Phase 3: DDD Sequence Diagrams (Days 3-4)
11. Produce 5 business-level sequence diagrams with domain intent emphasized:
12. Enroll Student in Course: enforce duplicate enrollment invariant.
13. Unenroll Student from Course: remove association safely.
14. Assign Book to Student: enforce single-owner invariant.
15. Unassign Book from Student: detach ownership safely.
16. Delete Student: orchestrate cross-context cleanup (ownership and enrollment removals) before aggregate deletion.
17. Represent participants at business level (Actor -> API -> Domain Policy/Service -> Persistence), keeping technical internals minimal while still showing domain rule checkpoints. Depends on step 11.
18. Phase 4: DDD Class Diagram (Day 5)
19. Create core domain model with aggregate roots and boundaries:
20. Academic aggregate roots: Student, Course; association concept: Enrollment.
21. Library aggregate root: Book with Owner reference to Student identity.
22. Add value objects where meaningful: StudentCode, CourseCode, ISBN, Email.
23. Mark repositories and domain services conceptually (for example EnrollmentPolicy, OwnershipPolicy) without implementation details.
24. Add invariants as notes on aggregates (unique codes/email/isbn, enrollment uniqueness, single book owner). Depends on step 19.
25. Keep DTO/page/error models in a separate boundary section so domain model remains clean.
26. Phase 5: Review and Handoff (Day 5)
27. Validate consistency between DDD artifacts and OpenAPI/ER schema.
28. Validate that each endpoint behavior is explainable through aggregate rules or domain service orchestration.
29. Deliver Mermaid sources and optional rendered PNG/PDF.

**Relevant files**
- /Users/phuchoang/Local_Document/student-management/docs/req.md — domain language, business rules, and architecture intent.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract-information.md — contract-first behavior references to align domain rules.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/openapi.yml — operation inventory for traceability to use cases and sequences.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/students.yml — student context commands/queries.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/students_byId.yml — student lifecycle and delete orchestration behavior.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/books.yml — book context commands/queries.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/books_byId.yml — book lifecycle operations.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/student-books_byId.yml — ownership commands and conflict rules.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/book-owner.yml — ownership query semantics and ambiguity notes.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/courses.yml — course context commands/queries.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/courses_byId.yml — course lifecycle and enrollment cleanup behavior.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/student-courses_byId.yml — enrollment commands and duplicate protection.
- /Users/phuchoang/Local_Document/student-management/docs/api-contract/paths/course-students.yml — enrollment query view by course.
- /Users/phuchoang/Local_Document/student-management/docs/database/schema.mermaid — entity relations and cardinality baseline for aggregates.

**Verification**
1. Ubiquitous language check: one canonical term per business concept across all three diagram types.
2. Context map check: every use case is assigned to Academic or Library context.
3. Aggregate rule check: each sequence includes explicit invariant checkpoints (duplicate enrollment, single owner, existence).
4. Model purity check: class diagram separates domain model from transport DTOs.
5. Contract traceability check: all important endpoints are covered by at least one use case or sequence flow.

**Decisions**
- Included scope: DDD-oriented Use Case, Sequence, and Class diagrams for current API contract.
- Excluded scope: auth/authorization, UI-level workflows, advanced subdomains (grading, attendance, schedule).
- Sequence detail remains business level.
- Mermaid remains the source format.

**Further Considerations**
1. Optional stretch: add a context map diagram as a fourth artifact for clearer DDD communication.
2. Optional stretch: add domain event notes (StudentDeleted, BookAssigned, StudentEnrolled) without committing to event-driven architecture yet.
3. Optional stretch: split Class Diagram into two views: Domain Model and Application/API Boundary.