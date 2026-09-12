import { vi } from 'vitest'

export interface StubResponse {
  status?: number
  body?: unknown
}

type Route = (url: string, init?: RequestInit) => StubResponse | undefined

/**
 * Stubs global fetch so a test exercises the real API client rather than a
 * mocked module. That matters: the client is where the bearer token, the
 * error shape and the 401 handling live, and mocking it away would leave all
 * three untested while the tests still looked green.
 */
export function stubFetch(routes: Route) {
  const calls: { url: string; init?: RequestInit }[] = []

  const impl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : String(input)
    calls.push({ url, init })

    const match = routes(url, init) ?? { status: 404, body: {
      timestamp: '', status: 404, error: 'Not Found',
      message: `no stub for ${url}`, fieldErrors: {},
    } }

    const status = match.status ?? 200
    return {
      ok: status >= 200 && status < 300,
      status,
      json: async () => match.body,
    } as Response
  })

  vi.stubGlobal('fetch', impl)
  return { calls, impl }
}

/** The body of the last call, parsed. */
export function lastBody(calls: { init?: RequestInit }[]): Record<string, unknown> {
  const body = calls[calls.length - 1]?.init?.body
  return body ? (JSON.parse(String(body)) as Record<string, unknown>) : {}
}
