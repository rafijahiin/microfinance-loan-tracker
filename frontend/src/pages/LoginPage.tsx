import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { RequestError } from '../api/client'

/** How long a sign-in may take before we assume the host is asleep rather than
 *  the network being slow. Free instances stop after about fifteen minutes
 *  idle and take roughly a minute to come back. */
const COLD_START_HINT_MS = 3000

export default function LoginPage() {
  const { signIn } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [slow, setSlow] = useState(false)
  const timer = useRef<number | undefined>(undefined)

  // Clear the pending timer if the component goes away mid-request, so a
  // resolved-or-unmounted sign-in cannot set state on a dead component.
  useEffect(() => () => window.clearTimeout(timer.current), [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSlow(false)
    setBusy(true)

    // A visitor clicking Sign in on a sleeping free instance waits about a
    // minute. Left to a spinner they conclude it is broken and leave, which is
    // a worse outcome than telling them what is happening.
    timer.current = window.setTimeout(() => setSlow(true), COLD_START_HINT_MS)

    try {
      await signIn(email, password)
    } catch (err) {
      setError(err instanceof RequestError ? err.message : 'Could not reach the server.')
    } finally {
      window.clearTimeout(timer.current)
      setSlow(false)
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <div className="card">
        <div className="brand">
          <span className="mark">LT</span>
          <span className="name">
            Loan Portfolio Tracker
            <small>Two-tier microfinance</small>
          </span>
        </div>
        <form onSubmit={onSubmit}>
          {error && <div className="error">{error}</div>}
          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email" type="email" autoComplete="username" required
              value={email} onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password" type="password" autoComplete="current-password" required
              value={password} onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <button type="submit" disabled={busy} style={{ width: '100%' }}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
          {slow && (
            <p role="status" style={{
              marginTop: 12, marginBottom: 0, fontSize: 12.5,
              color: 'var(--ink-3)', lineHeight: 1.6,
            }}>
              Waking the demo server. It sleeps when idle and can take up to a
              minute to start. This only happens on the first visit.
            </p>
          )}
        </form>
        <div className="demo-hint">
          Demo accounts, available when the stack is started with
          SEED_DEMO_DATA=true:
          <br />
          admin@example.org / admin12345
          <br />
          officer.rangpur@example.org / officer12345
        </div>
      </div>
      <p className="colophon">
        An independent portfolio project. Not affiliated with or endorsed by
        Palli Karma-Sahayak Foundation.
      </p>
    </div>
  )
}
