import type { ApiError } from './types'

const BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const TOKEN_KEY = 'loan-tracker.token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    // Private browsing and blocked site data both throw here rather than
    // returning null, and an unreadable token is the same as no token.
    return null
  }
}

export function setToken(token: string | null): void {
  try {
    if (token === null) localStorage.removeItem(TOKEN_KEY)
    else localStorage.setItem(TOKEN_KEY, token)
  } catch {
    /* Non-fatal: the session simply will not survive a reload. */
  }
}

/** Thrown for any non-2xx response, carrying the API's own error body when it
 *  sent one so a caller can show the server's message rather than inventing
 *  its own. */
export class RequestError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'RequestError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

let onUnauthorized: (() => void) | null = null

/** Lets the auth layer clear its state when a token expires mid-session,
 *  without every call site having to check for 401. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(`${BASE}${path}`, { ...init, headers })

  if (res.status === 401) {
    onUnauthorized?.()
    throw new RequestError(401, 'Your session has expired. Please sign in again.')
  }

  if (!res.ok) {
    let body: Partial<ApiError> = {}
    try {
      body = (await res.json()) as Partial<ApiError>
    } catch {
      /* An error with no JSON body, for example a gateway failure. */
    }
    throw new RequestError(
      res.status,
      body.message ?? `Request failed with status ${res.status}`,
      body.fieldErrors ?? {},
    )
  }

  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
}
