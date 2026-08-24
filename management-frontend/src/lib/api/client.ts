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
 *
 * A 401 *is* handled here, and the difference is exactly that ambiguity. Outside the login call
 * there is only one thing a 401 can mean — the server session is gone, because it expired or an
 * administrator ended it — so no local state is needed to interpret it and every caller wants the
 * same outcome. See `onSessionExpired`.
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

/** The login call, whose 401 means "wrong password" rather than "your session is gone". */
const LOGIN_PATH = '/api/v1/auth/login';

type SessionExpiredHandler = () => void;

let sessionExpiredHandler: SessionExpiredHandler | null = null;

/**
 * Registers what to do when the server says the session is no longer valid — in practice
 * `AuthProvider` clearing its stored session, which drops `RequireAuth` back to /login.
 *
 * A registration hook rather than a direct import so this module stays free of React and of the
 * auth module, which imports it.
 */
export function onSessionExpired(handler: SessionExpiredHandler | null): void {
  sessionExpiredHandler = handler;
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

  // The session died under us — most often because a System Administrator ended it, which answers
  // 401 from ConcurrentSessionFilter. The error is still thrown so the caller can render it; this
  // only makes sure the stale local session goes with it.
  if (res.status === 401 && path !== LOGIN_PATH) sessionExpiredHandler?.();

  if (!res.ok) throw new ApiError(res.status, payload as ErrorBody | null);
  return payload as T;
}

export default request;
