import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import LoginPage from './LoginPage'
import { AuthProvider } from '../auth/AuthContext'
import { stubFetch } from '../test/fetchMock'

function renderLogin() {
  return render(
    <AuthProvider>
      <LoginPage />
    </AuthProvider>,
  )
}

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Email'), 'admin@example.org')
  await user.type(screen.getByLabelText('Password'), 'admin12345')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('LoginPage', () => {
  it('keeps the token on a successful sign-in', async () => {
    const user = userEvent.setup()
    const { calls } = stubFetch(() => ({
      body: {
        token: 'a-real-token', tokenType: 'Bearer', expiresInSeconds: 3600,
        email: 'admin@example.org', role: 'ADMIN', partnerId: null,
      },
    }))

    renderLogin()
    await fillAndSubmit(user)

    await waitFor(() =>
      expect(localStorage.getItem('loan-tracker.token')).toBe('a-real-token'))
    expect(JSON.parse(calls[0]?.init?.body as string)).toMatchObject({
      email: 'admin@example.org',
    })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('does not claim the session expired when there was never a session',
    async () => {
      // A 401 means two things. Carrying a token, it has expired. Not carrying
      // one, a sign-in was refused, and "your session has expired" is nonsense
      // to someone who has not signed in yet.
      const user = userEvent.setup()
      stubFetch(() => ({
        status: 401,
        body: { status: 401, message: 'Invalid email or password', fieldErrors: {} },
      }))

      renderLogin()
      await fillAndSubmit(user)

      await screen.findByText('Invalid email or password')
      expect(screen.queryByText(/session has expired/)).not.toBeInTheDocument()
    })

  it("shows the server's message when credentials are refused", async () => {
    const user = userEvent.setup()
    stubFetch(() => ({
      status: 401,
      body: { status: 401, message: 'Invalid email or password', fieldErrors: {} },
    }))

    renderLogin()
    await fillAndSubmit(user)

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument()
  })

  it('explains the wait instead of leaving a spinner, when the host is asleep',
    async () => {
      // A free instance sleeps after fifteen minutes and takes about a minute
      // to return. A visitor staring at a spinner concludes it is broken.
      //
      // Only applies to a remote API. Locally the backend is on the same
      // machine and cannot be asleep, so the notice is gated on
      // VITE_API_BASE_URL being set, which is what this stubs.
      vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test')
      vi.useFakeTimers({ shouldAdvanceTime: true })
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

      let release: (() => void) | undefined
      vi.stubGlobal('fetch', vi.fn(async () => {
        await new Promise<void>((resolve) => { release = resolve })
        return { ok: true, status: 200, json: async () => ({
          token: 't', tokenType: 'Bearer', expiresInSeconds: 3600,
          email: 'admin@example.org', role: 'ADMIN', partnerId: null,
        }) } as Response
      }))

      renderLogin()
      await fillAndSubmit(user)

      expect(screen.queryByRole('status')).not.toBeInTheDocument()
      await vi.advanceTimersByTimeAsync(3500)
      expect(await screen.findByRole('status'))
        .toHaveTextContent(/Waking the demo server/)

      release?.()
      vi.useRealTimers()
    })

  it('never mentions a demo server when the API is local', async () => {
    // VITE_API_BASE_URL is empty in development, so the notice must not appear
    // however long the local backend takes.
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

    let release: (() => void) | undefined
    vi.stubGlobal('fetch', vi.fn(async () => {
      await new Promise<void>((resolve) => { release = resolve })
      return { ok: true, status: 200, json: async () => ({}) } as Response
    }))

    renderLogin()
    await fillAndSubmit(user)
    await vi.advanceTimersByTimeAsync(10000)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    release?.()
    vi.useRealTimers()
  })

  it('does not cry cold start when the server answers promptly', async () => {
    const user = userEvent.setup()
    stubFetch(() => ({
      status: 401,
      body: { status: 401, message: 'Invalid email or password', fieldErrors: {} },
    }))

    renderLogin()
    await fillAndSubmit(user)

    await screen.findByText('Invalid email or password')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
