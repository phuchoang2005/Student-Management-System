/**
 * The single fetch wrapper. Every call in `endpoints.ts` goes through `request`.
 *
 * What lives here: JSON encode/decode, 204-empty handling, tolerance for the bodyless 403 the
 * must-change-password filter writes, and `ApiError` normalisation.
 *
 * What deliberately does NOT live here: the redirect decision for a 403. A 403 is ambiguous on this
 * backend — anonymous, wrong role, and must-change-password all produce one, and two of the three
 * carry no body. Resolving it needs the local auth state, which only `RequireAuth` and the pages
 * have.
 */

export interface FieldError {
  field: string;
  message: string;
}

interface ErrorBody {
  message?: string;
  errors?: FieldError[];
}

export class ApiError extends Error {
  readonly status: number;
  /** `ValidationError.errors[]` — present on 400s, absent everywhere else. */
  readonly errors: FieldError[];
  readonly body: ErrorBody | null;

  constructor(status: number, body: ErrorBody | null) {
    super(body?.message ?? `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.errors = body?.errors ?? [];
    this.body = body;
  }

  /**
   * The single hook that lets any form render inline validation without per-form code.
   * Bean-validation failures and the service-level domain checks both land in `errors[]` with a
   * `field` matching the request DTO's property name.
   */
  fieldError(name: string): string | undefined {
    return this.errors.find((e) => e.field === name)?.message;
  }

  /** True when the server sent no body at all — the must-change-password gate's signature. */
  get isBodyless(): boolean {
    return this.body === null;
  }
}

type Params = Record<string, string | number | boolean | null | undefined>;

/** Drops null/undefined/'' so `?query=&page=0` never sends an empty query the backend would match on. */
function clean(params: Params): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') out[key] = String(value);
  }
  return out;
}

interface RequestOptions {
  body?: unknown;
  params?: Params;
}

export async function request<T>(
  method: string,
  path: string,
  { body, params }: RequestOptions = {},
): Promise<T> {
  const search = params ? new URLSearchParams(clean(params)).toString() : '';
  const url = search ? `${path}?${search}` : path;

  const res = await fetch(url, {
    method,
    credentials: 'include',
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 204) return null as T;

  // Not every non-2xx has a body: the filter-chain 403s write a status and return.
  const payload = (await res.json().catch(() => null)) as T | ErrorBody | null;
  if (!res.ok) throw new ApiError(res.status, payload as ErrorBody | null);
  return payload as T;
}

export default request;
