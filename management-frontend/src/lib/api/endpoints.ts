/**
 * The client-side copy of the API contract: one named function per operation, so no component ever
 * writes a URL string. When the backend contract changes, exactly this file moves.
 *
 * Derived from the shipped controllers, not from the hand-authored api-specification.md.
 *
 * Every student is addressed by `studentCode`, every book by `isbn`, every course by `courseCode`.
 * No function here takes a numeric id, because no endpoint accepts one any more.
 */
import request from './client';
import type {
  ActiveSession,
  BatchEnrollmentResponse,
  BookCreateRequest,
  BookDetail,
  BookSummary,
  CourseCreateRequest,
  CourseDetail,
  CourseSummary,
  CourseUpdateRequest,
  DemoAccount,
  Enrollment,
  InitialPassword,
  LoginResponse,
  MeBook,
  MeCourse,
  MeProfile,
  Page,
  Role,
  StaffAccountCreated,
  StaffAccountStatus,
  StaffAccountSummary,
  StudentDetail,
  StudentRegistration,
  StudentSummary,
  StudentWriteRequest,
} from './types';

const enc = encodeURIComponent;

export const auth = {
  login: (username: string, password: string) =>
    request<LoginResponse>('POST', '/api/v1/auth/login', { body: { username, password } }),

  /**
   * 200 with an empty body. A wrong `currentPassword` comes back 401 (not 400) — the one place
   * outside login that uses 401, so the caller must render it inline rather than treat it as an
   * expired session.
   */
  changePassword: (currentPassword: string, newPassword: string, retypeNewPassword: string) =>
    request<null>('POST', '/api/v1/auth/password', {
      body: { currentPassword, newPassword, retypeNewPassword },
    }),

  /** Public. 404s when `app.demo-accounts.enabled` is false (the `prod` profile), so callers tolerate failure. */
  demoAccounts: () => request<DemoAccount[]>('GET', '/api/v1/auth/demo-accounts'),
};

export const students = {
  /** Transparently scoped server-side for a STUDENT caller: 0 or 1 rows, never a 403. */
  search: (query?: string, page = 0, size = 20) =>
    request<Page<StudentSummary>>('GET', '/api/v1/students', { params: { query, page, size } }),
  get: (code: string) => request<StudentDetail>('GET', `/api/v1/students/${enc(code)}`),
  /** 404 once the student has changed their password — deliberately indistinguishable from "no such student". */
  initialPassword: (code: string) =>
    request<InitialPassword>('GET', `/api/v1/students/${enc(code)}/initial-password`),
  /** 201; the response carries `username` + a one-time `initialPassword` shown exactly once. */
  register: (body: StudentWriteRequest) =>
    request<StudentRegistration>('POST', '/api/v1/students', { body }),
  update: (code: string, body: StudentWriteRequest) =>
    request<StudentDetail>('PUT', `/api/v1/students/${enc(code)}`, { body }),
  remove: (code: string) => request<null>('DELETE', `/api/v1/students/${enc(code)}`),
};

export const books = {
  /** `ownerStudentCode` is how the Librarian pulls up one student's borrowed books. */
  search: (query?: string, page = 0, size = 20, ownerStudentCode?: string) =>
    request<Page<BookSummary>>('GET', '/api/v1/books', {
      params: { query, ownerStudentCode, page, size },
    }),
  get: (isbn: string) => request<BookDetail>('GET', `/api/v1/books/${enc(isbn)}`),
  create: (body: BookCreateRequest) => request<BookDetail>('POST', '/api/v1/books', { body }),
  assignOwner: (isbn: string, studentCode: string) =>
    request<BookDetail>('PATCH', `/api/v1/books/${enc(isbn)}/owner`, { body: { studentCode } }),
  /** Idempotent: 200 with `ownerStudentCode: null` even if the book was already unowned. */
  clearOwner: (isbn: string) => request<BookDetail>('DELETE', `/api/v1/books/${enc(isbn)}/owner`),
  remove: (isbn: string) => request<null>('DELETE', `/api/v1/books/${enc(isbn)}`),
};

export const courses = {
  search: (query?: string, page = 0, size = 20) =>
    request<Page<CourseSummary>>('GET', '/api/v1/courses', { params: { query, page, size } }),
  get: (code: string) => request<CourseDetail>('GET', `/api/v1/courses/${enc(code)}`),
  create: (body: CourseCreateRequest) => request<CourseDetail>('POST', '/api/v1/courses', { body }),
  /** `courseCode` is immutable — the PUT body does not accept it. */
  update: (code: string, body: CourseUpdateRequest) =>
    request<CourseDetail>('PUT', `/api/v1/courses/${enc(code)}`, { body }),
  remove: (code: string) => request<null>('DELETE', `/api/v1/courses/${enc(code)}`),
};

export const enrollments = {
  /**
   * Exactly one filter, never both and never neither — the backend answers a missing or doubled
   * filter with a 400 rather than enumerating every enrollment in the system.
   */
  byStudent: (studentCode: string, page = 0, size = 20) =>
    request<Page<Enrollment>>('GET', '/api/v1/enrollments', {
      params: { studentCode, page, size },
    }),
  byCourse: (courseCode: string, page = 0, size = 20) =>
    request<Page<Enrollment>>('GET', '/api/v1/enrollments', {
      params: { courseCode, page, size },
    }),
  create: (studentCode: string, courseCode: string) =>
    request<Enrollment>('POST', '/api/v1/enrollments', { body: { studentCode, courseCode } }),
  /**
   * One student, up to 50 courses. Answers 200 with a per-course outcome for each — a duplicate or
   * unknown course fails only its own row, and the rest stay enrolled. Only an unknown student is a
   * whole-request 400, since it makes every row unanswerable.
   */
  createBatch: (studentCode: string, courseCodes: string[]) =>
    request<BatchEnrollmentResponse>('POST', '/api/v1/enrollments/batch', {
      body: { studentCode, courseCodes },
    }),
  get: (studentCode: string, courseCode: string) =>
    request<Enrollment>('GET', `/api/v1/enrollments/${enc(studentCode)}/${enc(courseCode)}`),
  remove: (studentCode: string, courseCode: string) =>
    request<null>('DELETE', `/api/v1/enrollments/${enc(studentCode)}/${enc(courseCode)}`),
};

/**
 * A Student's own three views. `profile` is the only way a Student learns their own `studentCode`:
 * the login response carries just `{role, mustChangePassword}` and this API has no session probe.
 */
export const me = {
  profile: () => request<MeProfile>('GET', '/api/v1/me/profile'),
  courses: (page = 0, size = 20) =>
    request<Page<MeCourse>>('GET', '/api/v1/me/courses', { params: { page, size } }),
  books: (page = 0, size = 20) =>
    request<Page<MeBook>>('GET', '/api/v1/me/books', { params: { page, size } }),
};

export const staffAccounts = {
  /** Scoped server-side to `STAFF_ROLES`, so sysadmin and student accounts never appear. */
  list: (page = 0, size = 20) =>
    request<Page<StaffAccountSummary>>('GET', '/api/v1/staff-accounts', { params: { page, size } }),
  /** `role` must be REGISTRAR, LIBRARIAN, or COURSE_ADMINISTRATOR; anything else is a 400. */
  create: (username: string, role: Role) =>
    request<StaffAccountCreated>('POST', '/api/v1/staff-accounts', { body: { username, role } }),
  setStatus: (id: number, enabled: boolean) =>
    request<StaffAccountStatus>('PATCH', `/api/v1/staff-accounts/${id}/status`, {
      body: { enabled },
    }),
};

/**
 * Who is signed in right now, and ending one of those sessions. System Administrator only.
 *
 * Not paged, unlike every other list here: the session registry is an in-memory snapshot with no
 * stable ordering to offset into, so pages would overlap and skip as sessions come and go.
 */
export const sessions = {
  list: () => request<ActiveSession[]>('GET', '/api/v1/sessions'),
  /**
   * Deferred by nature: the session is flagged, and its owner's *next* request is what gets
   * rejected with a 401. Nothing can be done with it in the meantime.
   */
  revoke: (handle: string) => request<null>('DELETE', `/api/v1/sessions/${enc(handle)}`),
};

/**
 * Not part of the documented API. Spring Security's default `POST /logout` should still be
 * registered, but nothing promises it, and the must-change-password gate 403s it for a user who
 * hasn't changed their password yet. Failure is ignored by design — see `AuthContext.logout`.
 */
export const logoutEndpoint = () => request<null>('POST', '/logout');
