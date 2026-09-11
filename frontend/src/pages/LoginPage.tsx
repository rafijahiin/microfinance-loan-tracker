import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { RequestError } from '../api/client'

export default function LoginPage() {
  const { signIn } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await signIn(email, password)
    } catch (err) {
      setError(err instanceof RequestError ? err.message : 'Could not reach the server.')
    } finally {
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
            {busy ? 'Signing in...' : 'Sign in'}
          </button>
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
