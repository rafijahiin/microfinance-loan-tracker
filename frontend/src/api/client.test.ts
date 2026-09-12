import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, getToken, setToken, setUnauthorizedHandler, RequestError } from './client'
import { stubFetch } from '../test/fetchMock'

describe('api client', () => {
  beforeEach(() => {
    setToken(null)
    setUnauthorizedHandler(null)
  })

  it('sends the bearer token when there is one', async () => {
    setToken('a-token')
    const { calls } = stubFetch(() => ({ body: { ok: true } }))

    await api.get('/api/partners')

    const headers = calls[0]?.init?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer a-token')
  })

  it('sends no Authorization header when signed out', async () => {
    const { calls } = stubFetch(() => ({ body: { ok: true } }))

    await api.get('/api/partners')

    const headers = calls[0]?.init?.headers as Headers
    expect(headers.get('Authorization')).toBeNull()
  })

  it("raises the server's own message rather than inventing one", async () => {
    stubFetch(() => ({
      status: 409,
      body: {
        status: 409, error: 'Conflict',
        message: 'Payment of 12000.01 is more than the 12000.00 still owed',
        fieldErrors: {},
      },
    }))

    // The server knows whether this was an overpayment, a duplicate receipt or
    // a settled loan. A generic client-side message would hide which.
    await expect(api.post('/api/loans/1/repayments', {}))
      .rejects.toThrow('Payment of 12000.01 is more than the 12000.00 still owed')
  })

  it('carries per-field validation errors through to the caller', async () => {
    stubFetch(() => ({
      status: 400,
      body: {
        status: 400, error: 'Bad Request', message: 'Validation failed',
        fieldErrors: { principal: 'must be greater than or equal to 1.00' },
      },
    }))

    await expect(api.post('/api/loans', {})).rejects.toMatchObject({
      status: 400,
      fieldErrors: { principal: 'must be greater than or equal to 1.00' },
    })
  })

  it('calls the unauthorized handler once on a 401, so a stale session clears', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    stubFetch(() => ({ status: 401, body: {} }))

    await expect(api.get('/api/loans')).rejects.toBeInstanceOf(RequestError)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('survives an error response that is not JSON', async () => {
    // A gateway or proxy failure returns HTML, and the client must still throw
    // something a caller can display rather than a parse error.
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: false,
      status: 502,
      json: async () => {
        throw new Error('not json')
      },
    } as unknown as Response)))

    await expect(api.get('/api/loans')).rejects.toThrow('Request failed with status 502')
  })

  it('stores and clears the token', () => {
    setToken('x')
    expect(getToken()).toBe('x')
    setToken(null)
    expect(getToken()).toBeNull()
  })

  it('survives an environment with no storage at all', async () => {
    // Private browsing, blocked site data, and this very jsdom configuration
    // all present a world without localStorage. The client must treat that as
    // "signed out", not throw on import or on every request.
    const real = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      get() {
        throw new Error('access denied')
      },
    })
    try {
      expect(getToken()).toBeNull()
      expect(() => setToken('x')).not.toThrow()

      const { calls } = stubFetch(() => ({ body: { ok: true } }))
      await api.get('/api/partners')
      const headers = calls[0]?.init?.headers as Headers
      expect(headers.get('Authorization')).toBeNull()
    } finally {
      if (real) Object.defineProperty(globalThis, 'localStorage', real)
    }
  })
})
