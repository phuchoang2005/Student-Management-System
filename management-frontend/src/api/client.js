/**
 * The single fetch wrapper. Every call in endpoints.js goes through `request`.
 *
 * What lives here: JSON encode/decode, 204-empty handling, tolerance for the bodyless 403 the
 * must-change-password filter writes, and ApiError normalisation.
 *
 * What deliberately does NOT live here: the redirect decision for a 403. A 403 is ambiguous on this
 * backend -- anonymous, wrong role, and must-change-password all produce one, and two of the three
 * carry no body. Resolving it needs the local auth state, which only RequireAuth and the pages have.
 */

/** Drops null/undefined/'' so `?query=&page=0` never sends an empty query the backend would match on. */
function clean(params) {
  return Object.fromEntries(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  );
}

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    // ValidationError.errors[] -- present on 400s, absent everywhere else.
    this.errors = body?.errors ?? [];
    this.body = body;
  }

  /**
   * The single hook that lets any form render inline validation without per-form code.
   * Bean-validation failures and the service-level domain checks both land in `errors[]` with a
   * `field` matching the request DTO's property name.
   */
  fieldError(name) {
    return this.errors.find((e) => e.field === name)?.message;
  }

  /** True when the server sent no body at all -- the must-change-password gate's signature. */
  get isBodyless() {
    return this.body === null;
  }
}

async function request(method, path, { body, params } = {}) {
  const search = params ? new URLSearchParams(clean(params)).toString() : '';
  const url = search ? `${path}?${search}` : path;

  const res = await fetch(url, {
    method,
    credentials: 'include',
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 204) return null;

  // Not every non-2xx has a body: the filter-chain 403s write a status and return.
  const payload = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, payload);
  return payload;
}

export default request;
