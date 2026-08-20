/**
 * The client-side copy of the API contract: one named function per operation, so no component ever
 * writes a URL string. When the backend contract changes, exactly this file moves.
 *
 * Derived from the shipped controllers, not from the hand-authored api-specification.md.
 */
import request from './client.js';

const enc = encodeURIComponent;

export const auth = {
  login: (username, password) =>
    request('POST', '/api/v1/auth/login', { body: { username, password } }),

  /**
   * 200 with an empty body. A wrong `currentPassword` comes back 401 (not 400) -- the one place
   * outside login that uses 401, so the caller must render it inline rather than treat it as an
   * expired session.
   */
  changePassword: (currentPassword, newPassword, retypeNewPassword) =>
    request('POST', '/api/v1/auth/password', {
      body: { currentPassword, newPassword, retypeNewPassword },
    }),

  /** Public. 404s when app.demo-accounts.enabled is false (the `prod` profile), so callers tolerate failure. */
  demoAccounts: () => request('GET', '/api/v1/auth/demo-accounts'),
};

export const students = {
  search: (query, page = 0, size = 20) =>
    request('GET', '/api/v1/students', { params: { query, page, size } }),
  get: (code) => request('GET', `/api/v1/students/${enc(code)}`),
  /** 404 once the student has changed their password -- deliberately indistinguishable from "no such student". */
  initialPassword: (code) => request('GET', `/api/v1/students/${enc(code)}/initial-password`),
  /** 201; the response carries `username` + a one-time `initialPassword` shown exactly once. */
  register: (body) => request('POST', '/api/v1/students', { body }),
  update: (code, body) => request('PUT', `/api/v1/students/${enc(code)}`, { body }),
  remove: (code) => request('DELETE', `/api/v1/students/${enc(code)}`),
};

export const books = {
  /** `owner` is a numeric student id, not a student code. */
  search: (query, page = 0, size = 20, owner) =>
    request('GET', '/api/v1/books', { params: { query, owner, page, size } }),
  get: (isbn) => request('GET', `/api/v1/books/${enc(isbn)}`),
  create: (body) => request('POST', '/api/v1/books', { body }),
  assignOwner: (isbn, studentId) =>
    request('PATCH', `/api/v1/books/${enc(isbn)}/owner`, { body: { studentId } }),
  /** Idempotent: 200 with `ownerId: null` even if the book was already unowned. */
  clearOwner: (isbn) => request('DELETE', `/api/v1/books/${enc(isbn)}/owner`),
  remove: (isbn) => request('DELETE', `/api/v1/books/${enc(isbn)}`),
};

export const courses = {
  search: (query, page = 0, size = 20) =>
    request('GET', '/api/v1/courses', { params: { query, page, size } }),
  get: (code) => request('GET', `/api/v1/courses/${enc(code)}`),
  create: (body) => request('POST', '/api/v1/courses', { body }),
  /** `courseCode` is immutable -- the PUT body does not accept it. */
  update: (code, body) => request('PUT', `/api/v1/courses/${enc(code)}`, { body }),
  remove: (code) => request('DELETE', `/api/v1/courses/${enc(code)}`),
};

export const enrollments = {
  /** Keyed by the student id + course code pair, not by an enrollment id. */
  create: (studentId, courseCode) =>
    request('POST', '/api/v1/enrollments', { body: { studentId, courseCode } }),
  get: (studentId, courseCode) =>
    request('GET', `/api/v1/enrollments/${enc(studentId)}/${enc(courseCode)}`),
  remove: (studentId, courseCode) =>
    request('DELETE', `/api/v1/enrollments/${enc(studentId)}/${enc(courseCode)}`),
};

export const me = {
  /**
   * The only endpoint with prefixed paging params: it composes two independently paged collections
   * and Spring resolves only one page/size pair per request.
   */
  booksAndCourses: ({ booksPage = 0, booksSize = 10, coursesPage = 0, coursesSize = 10 } = {}) =>
    request('GET', '/api/v1/me/books-and-courses', {
      params: { booksPage, booksSize, coursesPage, coursesSize },
    }),
};

export const staffAccounts = {
  /** Scoped server-side to STAFF_ROLES, so sysadmin and student accounts never appear. */
  list: (page = 0, size = 20) =>
    request('GET', '/api/v1/staff-accounts', { params: { page, size } }),
  /** `role` must be REGISTRAR, LIBRARIAN, or COURSE_ADMINISTRATOR; anything else is a 400. */
  create: (username, role) =>
    request('POST', '/api/v1/staff-accounts', { body: { username, role } }),
  setStatus: (id, enabled) =>
    request('PATCH', `/api/v1/staff-accounts/${enc(id)}/status`, { body: { enabled } }),
};

/**
 * Not part of the documented API. Spring Security's default POST /logout should still be
 * registered, but nothing promises it, and the must-change-password gate 403s it for a user who
 * hasn't changed their password yet. Failure is ignored by design -- see AuthContext.logout.
 */
export const logoutEndpoint = () => request('POST', '/logout');
