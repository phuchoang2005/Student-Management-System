/**
 * The response shapes the backend actually returns, transcribed from the controllers and DTOs in
 * `management/src/main/java/org/phuchoang/management/<module>/web/`.
 *
 * Note what is *absent*: there is no `id` on any of these, and no `studentId` / `ownerId` / numeric
 * `courseId`. Surrogate ids are a database concern and no longer cross the HTTP boundary
 * (`api-specification.md` §5 decision #9) — a student is a `studentCode`, a book an `isbn`, a
 * course a `courseCode`. If a screen here ever needs an id, the API design is wrong, not the screen.
 */

export type Role =
  | 'REGISTRAR'
  | 'LIBRARIAN'
  | 'COURSE_ADMINISTRATOR'
  | 'STUDENT'
  | 'SYSTEM_ADMINISTRATOR';

/** The `PageResponse<T>` envelope every list endpoint returns. `page` is 0-based. */
export interface Page<T> {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  content: T[];
}

export interface LoginResponse {
  role: Role;
  mustChangePassword: boolean;
}

export interface DemoAccount {
  role: Role;
  username: string;
  password: string;
}

export interface StudentSummary {
  studentCode: string;
  firstName: string;
  lastName: string;
  email: string;
}

export interface StudentDetail extends StudentSummary {
  dateOfBirth: string;
  createdAt: string;
  updatedAt: string;
}

export interface StudentRegistration extends StudentDetail {
  /** Shown exactly once, in this response. Recoverable afterwards only via UC-23, and only until changed. */
  username: string;
  initialPassword: string;
}

export interface InitialPassword {
  username: string;
  initialPassword: string;
}

export interface StudentWriteRequest {
  studentCode?: string;
  firstName: string;
  lastName: string;
  email: string;
  dateOfBirth: string;
}

export interface BookSummary {
  isbn: string;
  title: string;
  author: string;
  /** `null` when the book is unowned. */
  ownerStudentCode: string | null;
}

export interface BookOwner {
  studentCode: string;
  firstName: string;
  lastName: string;
  email: string;
}

export interface BookDetail {
  isbn: string;
  title: string;
  author: string;
  publishedDate: string | null;
  ownerStudentCode: string | null;
  createdAt: string;
  updatedAt: string;
  owner: BookOwner | null;
}

export interface BookCreateRequest {
  isbn: string;
  title: string;
  author: string;
  publishedDate?: string | null;
  ownerStudentCode?: string | null;
}

export interface CourseSummary {
  courseCode: string;
  name: string;
  credits: number;
  /**
   * How many students are enrolled. A count, not the roster — the roster stays a separately
   * authorized read (`GET /enrollments?courseCode=`), so this reveals no student's identity to a
   * Student browsing the catalogue. A snapshot rather than a capacity guarantee.
   */
  enrolledCount: number;
}

export interface CourseDetail extends CourseSummary {
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CourseCreateRequest {
  courseCode: string;
  name: string;
  description?: string | null;
  credits: number;
}

/** `courseCode` is immutable — the PUT body does not accept it. */
export type CourseUpdateRequest = Omit<CourseCreateRequest, 'courseCode'>;

export interface EnrollmentStudent {
  studentCode: string;
  firstName: string;
  lastName: string;
  email: string;
}

export interface EnrollmentCourse {
  courseCode: string;
  name: string;
  credits: number;
}

/**
 * One enrollment with both sides resolved. Also the row type of
 * `GET /enrollments?studentCode=|courseCode=`: filtering by either side yields the same rows viewed
 * from a different end, so "this student's courses" and "this course's roster" read off one shape.
 */
export interface Enrollment {
  student: EnrollmentStudent;
  course: EnrollmentCourse;
  enrolledAt: string;
}

/** One course's outcome in a batch enrollment. `ENROLLED` is the only success. */
export type BatchEnrollmentStatus =
  | 'ENROLLED'
  | 'UNKNOWN_COURSE'
  | 'ALREADY_ENROLLED'
  | 'INVALID_COURSE_CODE';

export interface BatchEnrollmentResult {
  courseCode: string;
  status: BatchEnrollmentStatus;
  /** Set only when `status` is `ENROLLED`. */
  enrolledAt: string | null;
  /** Set only when `status` is not `ENROLLED`. */
  message: string | null;
}

/**
 * The batch answers 200 even when every course was rejected — the request succeeded, and the
 * per-course statuses are its answer. `requested` counts *distinct* courses, so it is below the
 * number submitted when the request repeated one.
 *
 * Enrolled courses are committed independently: a rejection later in the list does not undo them.
 */
export interface BatchEnrollmentResponse {
  studentCode: string;
  requested: number;
  enrolled: number;
  failed: number;
  results: BatchEnrollmentResult[];
}

export interface MeProfile {
  studentCode: string;
  firstName: string;
  lastName: string;
  email: string;
  dateOfBirth: string;
}

/** No owner field — every row is the caller's own. */
export interface MeBook {
  isbn: string;
  title: string;
  author: string;
}

export type MeCourse = CourseSummary;

/**
 * Staff accounts keep their numeric `id`: `PATCH /staff-accounts/{id}/status` is an `identity`
 * concern with no business key, and the note-fix role rework does not touch the sysadmin surface.
 */
/**
 * One signed-in session.
 *
 * `handle` is a SHA-256 digest of the session id, never the id itself — a session id is a
 * replayable credential, so the API does not emit one. It is the address used to end the session.
 *
 * The list is in-memory and per-process: it empties when the backend restarts, and that is expected
 * rather than a fault.
 */
export interface ActiveSession {
  handle: string;
  username: string;
  role: Role;
  lastRequest: string;
  /** The caller's own session. It cannot be revoked from here — sign out instead. */
  current: boolean;
}

export interface StaffAccountSummary {
  id: number;
  username: string;
  role: Role;
  enabled: boolean;
}

export interface StaffAccountCreated {
  username: string;
  role: Role;
  initialPassword: string;
}

export interface StaffAccountStatus {
  username: string;
  enabled: boolean;
}
